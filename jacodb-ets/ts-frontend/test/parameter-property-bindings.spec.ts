import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { MethodDto } from "../src/dto/model";
import { lower } from "./util";

function constructorOf(source: string): MethodDto {
    const { file, diagnostics } = lower(source);
    const owner = file.classes.find((clazz) => clazz.signature.name === "Example");
    const constructor = owner?.methods.find((method) => method.signature.name === "constructor");

    expect(diagnostics.messages).toEqual([]);
    if (constructor === undefined) throw new Error("Missing Example constructor");
    return constructor;
}

function assignedParameterLocal(method: MethodDto, index: number): string {
    const assignment = method.body?.cfg.blocks.flatMap((block) => block.stmts).find((statement) =>
        statement._ === "AssignStmt" && statement.right._ === "ParameterRef" && statement.right.index === index,
    );
    if (assignment?._ !== "AssignStmt" || assignment.left._ !== "Local") throw new Error("Missing parameter assignment");
    return assignment.left.name;
}

function localNamesIn(value: unknown): string[] {
    if (value === null || typeof value !== "object") return [];
    if (Array.isArray(value)) return value.flatMap(localNamesIn);
    const record = value as Record<string, unknown>;
    const names = record._ === "Local" && typeof record.name === "string" ? [record.name] : [];
    return [...names, ...Object.values(record).flatMap(localNamesIn)];
}

describe("constructor parameter-property local bindings", () => {
    it("uses one binding in prologue, property assignment, and constructor array loop", () => {
        const source = `
            export class Example {
                prefixFree = true;
                constructor(public names: string[]) {
                    for (let index = 0; this.prefixFree && index < names.length; index++) {
                        this.prefixFree = false;
                    }
                }
            }
        `;
        const constructor = constructorOf(source);
        const parameter = assignedParameterLocal(constructor, 0);
        const names = localNamesIn(constructor.body?.cfg).filter((name) => name.startsWith("names"));

        expect(new Set(names)).toEqual(new Set([parameter]));
        expect(constructor.body?.locals.filter((local) => local.name.startsWith("names"))).toHaveLength(1);

        const javascript = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        expect(new Function("exports", `${javascript}; return new exports.Example([]).prefixFree;`)({})).toBe(true);
    });

    it("preserves a real block-scoped shadow while returning to the parameter binding afterward", () => {
        const constructor = constructorOf(`
            export class Example {
                marker = 0;
                after = 0;
                constructor(public value: number) {
                    { let value = 20; this.marker = value; }
                    this.after = value;
                }
            }
        `);
        const statements = constructor.body?.cfg.blocks.flatMap((block) => block.stmts) ?? [];
        const parameter = assignedParameterLocal(constructor, 0);
        const marker = statements.find((statement) => statement._ === "AssignStmt"
            && statement.left._ === "InstanceFieldRef" && statement.left.field.name === "marker");
        const after = statements.find((statement) => statement._ === "AssignStmt"
            && statement.left._ === "InstanceFieldRef" && statement.left.field.name === "after");

        expect(after).toMatchObject({ right: { _: "Local", name: parameter } });
        expect(marker).toMatchObject({ right: { _: "Local", name: "value$1" } });
        expect(constructor.body?.locals.filter((local) => local.name.startsWith("value"))).toHaveLength(2);
    });

    it("resolves an earlier parameter property in a later parameter default", () => {
        const constructor = constructorOf(`
            export class Example {
                constructor(public value: number, public copy: number = value) {}
            }
        `);
        const parameter = assignedParameterLocal(constructor, 0);
        const names = localNamesIn(constructor.body?.cfg).filter((name) => name.startsWith("value"));

        expect(new Set(names)).toEqual(new Set([parameter]));
    });
});
