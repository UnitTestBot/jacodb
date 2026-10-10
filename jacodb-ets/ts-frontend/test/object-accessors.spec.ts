import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { executeObjectIr } from "./object-runtime";
import { compile, lower, methodByName } from "./util";

function nodeResult(source: string, expression: string): unknown {
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;
    return runInNewContext(`${javascript}\n${expression}`, { exports: {} });
}

describe("object getter descriptors", () => {
    it("preserves the getter body and invokes it on a property read", () => {
        const source = `export function value(): number { return ({ get x() { return 1; } }).x; }`;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "value").body!.cfg.blocks.flatMap((block) => block.stmts);
        const descriptor = stmts.find((stmt) => stmt._ === "DefineAccessorStmt");

        expect(descriptor).toMatchObject({
            _: "DefineAccessorStmt",
            key: { _: "Constant", value: "x" },
            getter: { _: "Local", type: { _: "FunctionType" } },
        });
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef")).toBe(true);
        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value")).toBe(nodeResult(source, `exports.value()`));
    });

    it("keeps captured bindings and calls the getter with its dynamic receiver", () => {
        const source = `
            export function value(seed: number): number {
                const object = { y: 2, get x() { seed++; return seed + this.y; } };
                const first = object.x;
                return first * 10 + object.x;
            }
        `;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value", [3])).toBe(67);
        expect(nodeResult(source, `exports.value(3)`)).toBe(67);
    });

    it("evaluates a computed getter key at creation but runs its body only on Get", () => {
        const source = `
            export function value(key: string): number {
                let reads = 0;
                const object = { get [key]() { reads++; return 4; } };
                return reads * 100 + object[key] * 10 + reads;
            }
        `;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value", ["x"])).toBe(41);
        expect(nodeResult(source, `exports.value("x")`)).toBe(41);
    });

    it("defines a later data property without invoking or retaining the replaced getter", () => {
        const source = `
            export function value(): number {
                let reads = 0;
                const object = { get x() { reads++; return 4; }, ["x"]: 7 };
                return object.x * 10 + reads;
            }
        `;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value")).toBe(70);
        expect(nodeResult(source, `exports.value()`)).toBe(70);
    });

    it("runs a getter while copying spread and creates a data property on the target", () => {
        const source = `
            export function value(): number {
                let reads = 0;
                const original = { get x() { reads++; return 4; } };
                const copied = { ...original };
                return copied.x * 100 + copied.x * 10 + reads;
            }
        `;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value")).toBe(441);
        expect(nodeResult(source, `exports.value()`)).toBe(441);
    });

    it("keeps object setters explicitly unsupported", () => {
        const { file, diagnostics } = lower(`export function value() { return { set x(value: number) {} }; }`);
        const stmts = methodByName(file, "value").body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(true);
        expect(diagnostics.messages.some((message) => message.includes("SetAccessor"))).toBe(true);
    });
});
