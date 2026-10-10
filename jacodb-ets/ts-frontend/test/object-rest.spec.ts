import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { compile, lower, methodByName } from "./util";
import { executeObjectIr } from "./object-runtime";

function evaluate(source: string, expression: string): unknown {
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;
    return runInNewContext(`${javascript}\n${expression}`, { exports: {} });
}

describe("object rest bindings", () => {
    it("builds object rest by excluding bound keys", () => {
        const source = `
            export function remainder(input: { x: number; y: number }): number {
                const { x, ...rest } = input;
                return rest.y;
            }
        `;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "remainder").body!.cfg.blocks.flatMap((block) => block.stmts);
        const xReadIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "PropertyRef" && stmt.right.key._ === "Constant" && stmt.right.key.value === "x");
        const copyIndex = stmts.findIndex((stmt) => stmt._ === "CopyDataPropertiesStmt");

        expect(xReadIndex).toBeGreaterThanOrEqual(0);
        expect(copyIndex).toBeGreaterThan(xReadIndex);
        expect(stmts[copyIndex]).toMatchObject({
            _: "CopyDataPropertiesStmt",
            excludedKeys: [{ _: "Constant", value: "x" }],
            throwOnNullishSource: true,
        });
        expect(diagnostics.messages).toEqual([]);
        expect(evaluate(source, `exports.remainder({ x: 1, y: 2 })`)).toBe(2);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "remainder", [{ x: 1, y: 2 }])).toBe(2);
    });
    it("rejects nullish object rest sources", () => {
        const source = `export function rest(input: any): any { const { ...result } = input; return result; }`;
        const { file, diagnostics } = lower(source);
        const serialized = JSON.parse(JSON.stringify(file));

        expect(diagnostics.messages).toEqual([]);
        expect(() => executeObjectIr(serialized, "rest", [undefined])).toThrow(TypeError);
        expect(() => executeObjectIr(serialized, "rest", [null])).toThrow(TypeError);
        expect(evaluate(source, `(() => { try { exports.rest(undefined); } catch (error) { return error instanceof TypeError; } })()`)).toBe(true);
    });

    it("preserves the rest source when a binding default changes its name", () => {
        const source = `export function rest(): number {
            let input: any = { x: undefined, y: 2 };
            const { x = (input = { y: 9 }, 0), ...result } = input;
            return result.y;
        }`;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "rest")).toBe(2);
        expect(evaluate(source, `exports.rest()`)).toBe(2);
    });

    it("excludes selected keys and copies only enumerable own string and symbol keys", () => {
        const source = `export function rest(input: any): any { const { x, ...result } = input; return result; }`;
        const { file, diagnostics } = lower(source);
        const symbol = Symbol("own");
        const input = Object.create({ inherited: 3 });
        input.x = 1;
        input.y = { value: 2 };
        input[symbol] = 4;
        Object.defineProperty(input, "hidden", { value: 9 });

        const result = executeObjectIr(JSON.parse(JSON.stringify(file)), "rest", [input]) as any;

        expect(diagnostics.messages).toEqual([]);
        expect(Reflect.ownKeys(result)).toEqual(["y", symbol]);
        expect(result.y).toBe(input.y);
        expect(result[symbol]).toBe(4);
        expect(evaluate(source, `(() => {
            const symbol = Symbol("own");
            const input = Object.create({ inherited: 3 });
            input.x = 1; input.y = {}; input[symbol] = 4;
            Object.defineProperty(input, "hidden", { value: 9 });
            const result = exports.rest(input);
            return Reflect.ownKeys(result).length * 10 + result[symbol] + (result.y === input.y ? 1 : 0);
        })()`)).toBe(25);
    });
});
