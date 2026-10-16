import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { ClassDto, EtsFileDto, MethodDto } from "../src/dto/model";
import { StmtDto } from "../src/dto/stmts";
import { serializeEtsFile } from "../src/serialize";
import { executor } from "./execute";
import { lower } from "./util";

function classNamed(file: EtsFileDto, name: string): ClassDto {
    const result = file.classes.find((candidate) => candidate.signature.name === name);
    if (result === undefined) throw new Error(`Missing class ${name}`);
    return result;
}

function methodNamed(clazz: ClassDto, name: string): MethodDto {
    const method = clazz.methods.find((candidate) => candidate.signature.name === name);
    if (method === undefined) throw new Error(`Missing method ${name}`);
    return method;
}

function statements(method: MethodDto): StmtDto[] {
    return method.body?.cfg.blocks.flatMap((block) => block.stmts) ?? [];
}

function hasUnsupported(method: MethodDto): boolean {
    return statements(method).some((statement) => statement._ === "UnsupportedStmt"
        || (statement._ === "AssignStmt" && statement.right._ === "UnsupportedValue"));
}

describe("independent bodies of classes with unsupported definitions", () => {
    it.each([
        { name: "computed ancestor", setup: "class Base { [Symbol.iterator]() { return [][Symbol.iterator](); } }", base: "Base" },
        { name: "ambient DOM constructor", setup: "", base: "HTMLElement" },
        { name: "ambient Map constructor", setup: "", base: "Map<string, number>", runtimeBase: "Map" },
        { name: "mutable constructor alias", setup: "class Plain {} let Alias: typeof Plain = Plain;", base: "Alias" },
        { name: "conditional native or SSR base", setup: "class Fallback {}", base: "(globalThis.HTMLElement ?? Fallback)" },
        { name: "IIFE-produced base", setup: "const Base = (() => { const Base = function () {}; Base.prototype.extra = 1; return Base; })();", base: "Base" },
    ])("keeps an independent body but no executable lifecycle: $name", ({ setup, base, runtimeBase }) => {
        const { file } = lower(`
            ${setup}
            export class Target extends ${base} {
                own = 2;
                score(value: number): number { return value < 0 ? -value : value + 1; }
            }
            export function reference() { return Target; }
            export function construct() { return new Target(); }
        `);
        const roundTrip = JSON.parse(serializeEtsFile(file)) as EtsFileDto;
        const target = classNamed(roundTrip, "Target");
        const moduleClass = classNamed(roundTrip, "%dflt");

        expect(hasUnsupported(methodNamed(target, "score"))).toBe(false);
        expect(target.superClassName).toBe(runtimeBase ?? base);
        expect(target.fields.map((field) => field.signature.name)).toContain("own");
        for (const name of ["constructor", "%instInit", "%statInit"]) {
            expect(hasUnsupported(methodNamed(target, name))).toBe(true);
        }
        expect(hasUnsupported(methodNamed(moduleClass, "%dflt"))).toBe(true);
        expect(hasUnsupported(methodNamed(moduleClass, "reference"))).toBe(true);
        expect(hasUnsupported(methodNamed(moduleClass, "construct"))).toBe(true);
        expect(file.exportInfos.some((info) => info.exportName === "Target")).toBe(false);
        expect(target.fields.some((field) => field.signature.name === "%computed")).toBe(false);
    });

    it("executes a retained pure body like ordinary TypeScript without claiming module construction", () => {
        const source = `
            let definitions = 0;
            function key(): symbol { definitions++; return Symbol.iterator; }
            class Base { [key()]() { return []; } }
            export class Target extends Base {
                score(value: number): number {
                    const magnitude = value < 0 ? -value : value;
                    return magnitude === 0 ? 10 : magnitude + 1;
                }
            }
            export function definitionCount() { return definitions; }
        `;
        const { file } = lower(source);
        const bodyOnlyFile = { ...file, classes: [classNamed(file, "Target")] };
        const oracle = executor(bodyOnlyFile);
        const javascript = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const native = new Function("exports", `${javascript}; return exports;`)({});

        for (const value of [-7, 0, 9]) {
            expect(oracle.call("score", value)).toBe(native.Target.prototype.score.call({}, value));
        }
        expect(native.definitionCount()).toBe(1);
        expect(hasUnsupported(methodNamed(classNamed(file, "%dflt"), "%dflt"))).toBe(true);
    });

    it("allows own data fields while stopping inherited and prototype-dependent operations", () => {
        const { file, diagnostics } = lower(`
            export class Target extends HTMLElement {
                own = 4;
                readOwn() { return this.own; }
                writeOwn(value: number) { this.own = value; return ++this.own; }
                readBase() { return this.id; }
                callBase() { return this.remove(); }
                callSuper() { return super.remove(); }
                computed(key: string) { return this[key]; }
                optional(value: Target | undefined) { return value?.id; }
                static throughStaticThis() { return this.inherited(); }
            }
        `);
        const target = classNamed(file, "Target");

        for (const name of ["readOwn", "writeOwn"]) expect(hasUnsupported(methodNamed(target, name))).toBe(false);
        for (const name of ["readBase", "callBase", "callSuper", "computed", "optional", "throughStaticThis"]) {
            expect(hasUnsupported(methodNamed(target, name))).toBe(true);
        }
        expect(methodNamed(target, "optional").body!.cfg.blocks.some((block) =>
            block.stmts.some((statement) => statement._ === "IfStmt"),
        )).toBe(true);
        expect(diagnostics.messages.some((message) => message.includes("member lookup depends on unsupported class definition"))).toBe(true);
    });

    it("does not claim that an unknown computed string key cannot overwrite a data field", () => {
        const { file } = lower(`
            declare function key(): string;
            class Base { [key()] = 99; }
            class Target extends Base { own = 4; read() { return this.own; } }
        `);

        expect(hasUnsupported(methodNamed(classNamed(file, "Target"), "read"))).toBe(true);
    });

    it("keeps symbol members distinct from ordinary string-named data fields", () => {
        const { file } = lower(`
            const tag: unique symbol = Symbol("tag");
            class Base { [tag] = 99; }
            class Target extends Base { own = 4; read() { return this.own; } }
        `);

        expect(hasUnsupported(methodNamed(classNamed(file, "Target"), "read"))).toBe(false);
    });

    it("retains earlier expression effects before a dependent computed lookup", () => {
        const { file } = lower(`
            class Target extends HTMLElement {}
            export function read(value: Target, key: () => string) { return value[key()]; }
        `);
        const body = statements(methodNamed(classNamed(file, "%dflt"), "read"));
        const keyCall = body.findIndex((statement) => statement._ === "AssignStmt" && statement.right._ === "PtrCallExpr");
        const unsupported = body.findIndex((statement) => statement._ === "AssignStmt" && statement.right._ === "UnsupportedValue");

        expect(keyCall).toBeGreaterThanOrEqual(0);
        expect(unsupported).toBeGreaterThan(keyCall);
    });

    it("retains lifted field closure bodies without constructing their captured environment", () => {
        const { file } = lower(`
            class Base { [Symbol.iterator]() { return []; } }
            class Target extends Base {
                own = 4;
                calculate = (value: number) => this.own + value;
            }
        `);
        const target = classNamed(file, "Target");
        const lifted = target.methods.filter((method) => method.signature.name.startsWith("%AM"));

        expect(lifted).toHaveLength(1);
        expect(hasUnsupported(lifted[0])).toBe(false);
        expect(hasUnsupported(methodNamed(target, "%instInit"))).toBe(true);
        expect(methodNamed(target, "%instInit").body!.locals.some((local) =>
            local.type._ === "LexicalEnvType" || local.type._ === "LexicalEnvTypeRef",
        )).toBe(false);
    });

    it("does not hide unsupported dependencies behind union intersection or constrained parameter types", () => {
        const { file } = lower(`
            class Target extends HTMLElement { own = 1; }
            export function constrained<T extends Target>(value: T) { return value.id; }
            export function union(value: Target | { id: string }) { return value.id; }
            export function intersection(value: Target & { extra: number }) { return value.id; }
            export function optional(value: Target | undefined) { return value?.id; }
            export function cast(value: Target) { return (value as any).id; }
        `);
        const module = classNamed(file, "%dflt");

        for (const name of ["constrained", "union", "intersection", "optional", "cast"]) {
            expect(hasUnsupported(methodNamed(module, name))).toBe(true);
        }
    });
});
