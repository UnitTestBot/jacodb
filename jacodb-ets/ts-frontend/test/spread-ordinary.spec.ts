import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { StmtDto } from "../src/dto/stmts";
import { serializeEtsFile } from "../src/serialize";
import { lower, methodByName, singleBlockStmts } from "./util";

function assertSpreadArgumentOrder(stmts: StmtDto[], methodName: string): void {
    const expansion = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "SpreadExpansionExpr");
    const reads = stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef");
    const call = stmts.find((stmt) => stmt._ === "AssignStmt"
        && (stmt.right._ === "StaticCallExpr" || stmt.right._ === "InstanceCallExpr")
        && stmt.right.method.name === methodName);

    expect(reads).toHaveLength(2);
    expect(expansion).toMatchObject({ right: { _: "SpreadExpansionExpr", expectedCount: 2 } });
    for (const read of reads) {
        expect(read).toMatchObject({ right: { array: expansion?._ === "AssignStmt" ? expansion.left : undefined } });
    }
    expect(reads.map((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef"
        && stmt.right.index._ === "Constant" ? stmt.right.index.value : "invalid")).toEqual(["0", "1"]);
    expect(call).toMatchObject({
        _: "AssignStmt",
        right: {
            args: reads.map((stmt) => stmt._ === "AssignStmt" ? stmt.left : undefined),
        },
    });
}

function runTypeScript(source: string, expression: string): unknown {
    const js = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;

    return new Function("exports", `${js}\nreturn ${expression};`)({});
}

describe("ordinary call spreads", () => {
    it("preserves runtime iteration for a tuple with a replaced iterator", () => {
        const source = `
            function add(a: number, b: number): number { return a + b; }
            export function sum(pair: [number, number]): number { return add(...pair); }
            export function concrete(): number {
                const pair: [number, number] = [2, 3];
                pair[Symbol.iterator] = function* () { yield 7; yield 8; };
                return sum(pair);
            }
        `;
        const { file } = lower(source);
        const stmts = singleBlockStmts(methodByName(JSON.parse(serializeEtsFile(file)), "sum"));

        assertSpreadArgumentOrder(stmts, "add");
        expect(runTypeScript(source, "exports.concrete()")).toBe(15);
    });

    it("expands a tuple in an ordinary call and preserves JSON argument order", () => {
        const source = `
            function add(a: number, b: number): number { return a + b; }
            export function sum(pair: [number, number]): number { return add(...pair); }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const stmts = singleBlockStmts(methodByName(roundTrip, "sum"));

        assertSpreadArgumentOrder(stmts, "add");
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "exports.sum([2, 3])")).toBe(5);
    });

    it("evaluates arguments around a spread in source order", () => {
        const source = `
            let events: string[] = [];
            function first(): number { events.push("first"); return 1; }
            function pair(): [number, number] { events.push("pair"); return [2, 3]; }
            function last(): number { events.push("last"); return 4; }
            function combine(a: number, b: number, c: number, d: number): number {
                return a * 1000 + b * 100 + c * 10 + d;
            }
            export function value(): number { return combine(first(), ...pair(), last()); }
            export function trace(): string { return events.join(","); }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = singleBlockStmts(methodByName(file, "value"));
        const calls = stmts.flatMap((stmt, index) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticCallExpr" ? [{ name: stmt.right.method.name, index, args: stmt.right.args }] : []);
        const reads = stmts.flatMap((stmt, index) => stmt._ === "AssignStmt"
            && stmt.right._ === "ArrayRef" ? [index] : []);

        expect(calls.map((call) => call.name)).toEqual(["first", "pair", "last", "combine"]);
        expect(reads).toHaveLength(2);
        expect(calls[1]!.index).toBeLessThan(reads[0]);
        expect(reads[1]).toBeLessThan(calls[2]!.index);
        expect(calls[3]!.args).toHaveLength(4);
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "{ value: exports.value(), trace: exports.trace() }")).toEqual({
            value: 1234,
            trace: "first,pair,last",
        });
    });
});
