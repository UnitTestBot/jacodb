import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { AssignStmtDto } from "../src/dto/stmts";
import { compile, lower, methodByName } from "./util";
import { executeObjectIr } from "./object-runtime";

function evaluate(source: string, expression: string): unknown {
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;
    return runInNewContext(`${javascript}\n${expression}`, { exports: {} });
}

describe("computed object binding keys", () => {
    it("reads a computed destructuring key from the original source", () => {
        const source = `
            export function pick(input: Record<string, number>, key: string): number {
                const { [key]: value } = input;
                return value;
            }
        `;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "pick").body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            left: expect.objectContaining({ _: "Local", name: "value" }),
            right: expect.objectContaining({ _: "PropertyRef", key: expect.objectContaining({ _: "Local" }) }),
        }));
        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            left: expect.objectContaining({ _: "Local", name: expect.stringMatching(/^%/) }),
            right: expect.objectContaining({ _: "ToPropertyKeyExpr", arg: expect.objectContaining({ name: "key" }) }),
        }));
        expect(diagnostics.messages).toEqual([]);
        expect(evaluate(source, `exports.pick({ x: 3 }, "x")`)).toBe(3);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "pick", [{ x: 3 }, "x"])).toBe(3);
    });

    it("preserves the destructuring source when a computed key mutates its binding", () => {
        const source = `
            let input = { x: 3 };
            let calls = 0;
            function change(): string { calls++; input = { x: 9 }; return "x"; }
            export function pick(): number {
                const { [change()]: value } = input;
                return value + calls;
            }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "pick").body!.cfg.blocks.flatMap((block) => block.stmts);
        const callIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "PtrCallExpr" && stmt.right.method.name === "change");
        const readIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef");
        const read = stmts[readIndex] as AssignStmtDto;

        expect(callIndex).toBeGreaterThanOrEqual(0);
        expect(readIndex).toBeGreaterThan(callIndex);
        expect(read.right).toMatchObject({ _: "PropertyRef", instance: { _: "Local", name: expect.stringMatching(/^%/) } });
        expect(diagnostics.messages).toEqual([]);
        expect(evaluate(source, `exports.pick()`)).toBe(4);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "pick")).toBe(4);
    });

    it("rejects nullish binding sources before converting the key", () => {
        const source = `export function pick(input: any, key: any) { const { [key]: value } = input; return value; }`;
        const { file, diagnostics } = lower(source);
        let conversions = 0;
        const key = { toString() { conversions++; return "x"; } };

        expect(diagnostics.messages).toEqual([]);
        expect(() => executeObjectIr(JSON.parse(JSON.stringify(file)), "pick", [null, key])).toThrow(TypeError);
        expect(conversions).toBe(0);
        expect(evaluate(source, `(() => {
            let conversions = 0;
            try { exports.pick(null, { toString() { conversions++; return "x"; } }); } catch (error) {}
            return conversions;
        })()`)).toBe(0);
    });

    it("reuses one evaluated computed key for both binding and rest exclusion", () => {
        const source = `
            export function remainder(input: Record<string, number>, key: string): number {
                const { [key]: value, ...rest } = input;
                return value + rest.y;
            }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "remainder").body!.cfg.blocks.flatMap((block) => block.stmts);
        const read = stmts.find((stmt): stmt is AssignStmtDto => stmt._ === "AssignStmt"
            && stmt.right._ === "PropertyRef");
        const copy = stmts.find((stmt) => stmt._ === "CopyDataPropertiesStmt");

        expect(read?.right).toMatchObject({ _: "PropertyRef", key: { _: "Local" } });
        expect(copy).toMatchObject({
            _: "CopyDataPropertiesStmt",
            excludedKeys: [(read?.right as { key: unknown }).key],
        });
        expect(diagnostics.messages).toEqual([]);
        expect(evaluate(source, `exports.remainder({ x: 3, y: 2 }, "x")`)).toBe(5);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "remainder", [{ x: 3, y: 2 }, "x"])).toBe(5);
    });

    it("normalizes a computed key once before reading and excluding it from rest", () => {
        const source = `
            export function remainder(input: any, key: any): number {
                const { [key]: value, ...rest } = input;
                return value + rest.y;
            }
        `;
        const { file, diagnostics } = lower(source);
        let conversions = 0;
        const key = { toString() { conversions++; return conversions === 1 ? "x" : "y"; } };

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "remainder", [{ x: 3, y: 2 }, key])).toBe(5);
        expect(conversions).toBe(1);
        expect(evaluate(source, `(() => {
            let conversions = 0;
            const key = { toString() { conversions++; return conversions === 1 ? "x" : "y"; } };
            return exports.remainder({ x: 3, y: 2 }, key) * 10 + conversions;
        })()`)).toBe(51);
    });
});
