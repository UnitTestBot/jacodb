import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { AssignStmtDto, DefineDataPropertyStmtDto } from "../src/dto/stmts";
import { compile, lower, methodByName } from "./util";
import { executeObjectIr } from "./object-runtime";

function evaluate(source: string, expression: string): unknown {
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;
    return runInNewContext(`${javascript}\n${expression}`, { exports: {} });
}

describe("computed object keys", () => {
    it("writes a computed literal property using its once-evaluated key", () => {
        const source = `
            export function read(key: string): number {
                return ({ [key]: 1 })[key];
            }
        `;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "read").body!.cfg.blocks.flatMap((block) => block.stmts);
        const store = stmts.find((stmt): stmt is DefineDataPropertyStmtDto =>
            stmt._ === "DefineDataPropertyStmt",
        );

        expect(store?.key).toMatchObject({ _: "Local", name: expect.stringMatching(/^%/) });
        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: { _: "ToPropertyKeyExpr", arg: { _: "Local", name: "key", type: { _: "StringType" } } },
        }));
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef")).toBe(true);
        expect(file.classes.flatMap((clazz) => clazz.fields).some((field) => field.signature.name === "%computed"))
            .toBe(false);
        expect(diagnostics.messages).toEqual([]);
        expect(evaluate(source, `exports.read("x")`)).toBe(1);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "read", ["x"])).toBe(1);
    });

    it("snapshots a computed key before the property value changes its binding", () => {
        const source = `
            let key = "x";
            let calls = 0;
            function takeKey(): string { calls++; return key; }
            export function read(): number {
                const object = { [takeKey()]: (key = "y", 7) };
                return object.x + calls;
            }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "read").body!.cfg.blocks.flatMap((block) => block.stmts);
        const keyCallIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "takeKey");
        const storeIndex = stmts.findIndex((stmt) => stmt._ === "DefineDataPropertyStmt");
        const keyStore = stmts[storeIndex] as DefineDataPropertyStmtDto;

        expect(keyCallIndex).toBeGreaterThanOrEqual(0);
        expect(storeIndex).toBeGreaterThan(keyCallIndex);
        expect(keyStore.key).toMatchObject({ _: "Local" });
        expect(diagnostics.messages).toEqual([]);
        expect(evaluate(source, `exports.read()`)).toBe(8);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "read")).toBe(8);
    });

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
            && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "change");
        const readIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef");
        const read = stmts[readIndex] as AssignStmtDto;

        expect(callIndex).toBeGreaterThanOrEqual(0);
        expect(readIndex).toBeGreaterThan(callIndex);
        expect(read.right).toMatchObject({ _: "PropertyRef", instance: { _: "Local", name: expect.stringMatching(/^%/) } });
        expect(diagnostics.messages).toEqual([]);
        expect(evaluate(source, `exports.pick()`)).toBe(4);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "pick")).toBe(4);
    });
    it("converts an object key before evaluating the property value", () => {
        const source = `
            export function read(key: any, state: { phase: string }): number {
                const object = { [key]: (state.phase = "after", 7) };
                return object.x;
            }
        `;
        const { file, diagnostics } = lower(source);
        const state = { phase: "before" };
        let conversions = 0;
        const key = { toString() { conversions++; return state.phase === "before" ? "x" : "y"; } };

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "read", [key, state])).toBe(7);
        expect(conversions).toBe(1);
        expect(evaluate(source, `(() => {
            const state = { phase: "before" };
            const key = { toString() { return state.phase === "before" ? "x" : "y"; } };
            return exports.read(key, state);
        })()`)).toBe(7);
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

    it("preserves the receiver before evaluating a computed access key", () => {
        const source = `export function read(key: string): number {
            let input = { x: 3 };
            return input[(input = { x: 9 }, key)];
        }`;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "read", ["x"])).toBe(3);
        expect(evaluate(source, `exports.read("x")`)).toBe(3);
    });

});
