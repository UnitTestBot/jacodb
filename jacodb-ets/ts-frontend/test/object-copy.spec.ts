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

describe("object property copying", () => {
    it("copies spread properties before later explicit overrides", () => {
        const source = `
            export function read(input: { x: number; y?: number }): number {
                const object = { ...input, y: 1 };
                return object.x + object.y;
            }
        `;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "read").body!.cfg.blocks.flatMap((block) => block.stmts);
        const copyIndex = stmts.findIndex((stmt) => stmt._ === "CopyDataPropertiesStmt");
        const overrideIndex = stmts.findIndex((stmt) => stmt._ === "DefineDataPropertyStmt"
            && stmt.key._ === "Constant" && stmt.key.value === "y");

        expect(copyIndex).toBeGreaterThanOrEqual(0);
        expect(stmts[copyIndex]).toMatchObject({
            _: "CopyDataPropertiesStmt",
            source: { _: "Local", name: "input" },
            excludedKeys: [],
            throwOnNullishSource: false,
        });
        expect(overrideIndex).toBeGreaterThan(copyIndex);
        expect(diagnostics.messages).toEqual([]);
        expect(evaluate(source, `exports.read({ x: 3, y: 9 })`)).toBe(4);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "read", [{ x: 3, y: 9 }])).toBe(4);
    });

    it("copies own enumerable properties when spread encounters a getter", () => {
        const source = `
            export function read(input: { x: number }): number {
                return { ...input, x: 7 }.x;
            }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "read").body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(stmts.filter((stmt) => stmt._ === "CopyDataPropertiesStmt")).toHaveLength(1);
        let reads = 0;
        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "read", [{ get x() { reads++; return 3; } }])).toBe(7);
        expect(reads).toBe(1);
        expect(evaluate(source, `(() => {
            let reads = 0;
            const result = exports.read({ get x() { reads++; return 3; } });
            return result * 10 + reads;
        })()`)).toBe(71);
    });

    it("copies enumerable symbols and excludes inherited and non-enumerable properties", () => {
        const source = `export function copy(input: any): any { return { ...input }; }`;
        const { file, diagnostics } = lower(source);
        const symbol = Symbol("own");
        const shared = { value: 1 };
        const input = Object.create({ inherited: 3 });
        input.x = shared;
        input[symbol] = 4;
        Object.defineProperty(input, "hidden", { value: 9 });

        const result = executeObjectIr(JSON.parse(JSON.stringify(file)), "copy", [input]) as any;

        expect(diagnostics.messages).toEqual([]);
        expect(Reflect.ownKeys(result)).toEqual(["x", symbol]);
        expect(result.x).toBe(shared);
        expect(result[symbol]).toBe(4);
        expect(evaluate(source, `(() => {
            const symbol = Symbol("own");
            const input = Object.create({ inherited: 3 });
            input.x = {}; input[symbol] = 4;
            Object.defineProperty(input, "hidden", { value: 9 });
            const result = exports.copy(input);
            return Reflect.ownKeys(result).length * 10 + result[symbol] + (result.x === input.x ? 1 : 0);
        })()`)).toBe(25);
    });
    it("skips nullish spread sources", () => {
        const source = `export function spread(input: any): number { return { ...input, x: 7 }.x; }`;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "spread", [null])).toBe(7);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "spread", [undefined])).toBe(7);
        expect(evaluate(source, `exports.spread(null)`)).toBe(7);
    });
});
