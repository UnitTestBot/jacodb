import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { StmtDto } from "../src/dto/stmts";
import { serializeEtsFile } from "../src/serialize";
import { lower, methodByName, singleBlockStmts } from "./util";

function assertSpreadArgumentOrder(stmts: StmtDto[], methodName: string): void {
    const expansion = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "SpreadExpansionExpr");
    const reads = stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef");
    const call = stmts.find((stmt) => stmt._ === "AssignStmt"
        && (stmt.right._ === "PtrCallExpr" || stmt.right._ === "InstanceCallExpr")
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
            _: methodName === "constructor" ? "InstanceCallExpr" : "PtrCallExpr",
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

describe("fixed-size spread arguments", () => {
    it("captures a computed method value and receiver before expanding its spread", () => {
        const source = `
            class Sum {
                bias = 10;
                add(a: number, b: number): number { return this.bias + a + b; }
            }
            export function value(receiver: Sum, pair: [number, number]): number {
                return receiver["add"](...pair);
            }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = singleBlockStmts(methodByName(JSON.parse(serializeEtsFile(file)), "value"));
        const property = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef");
        const expansionIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "SpreadExpansionExpr");
        const call = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr");

        expect(property).toBeDefined();
        expect(expansionIndex).toBeGreaterThan(stmts.indexOf(property!));
        expect(call).toMatchObject({ right: {
            ptr: property?._ === "AssignStmt" ? property.left : undefined,
            receiver: property?._ === "AssignStmt" && property.right._ === "PropertyRef" ? property.right.instance : undefined,
            args: expect.any(Array),
        } });
        expect(call?._ === "AssignStmt" && call.right._ === "PtrCallExpr" ? call.right.args : []).toHaveLength(2);
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "exports.value(new Sum(), [2, 3])")).toBe(15);
    });

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

    it("expands a tuple in a constructor call and preserves JSON argument order", () => {
        const source = `
            class Point {
                y: number;
                constructor(x: number, y: number) { this.y = y; }
            }
            export function make(pair: [number, number]): number { return new Point(...pair).y; }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const stmts = singleBlockStmts(methodByName(roundTrip, "make"));

        assertSpreadArgumentOrder(stmts, "constructor");
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "exports.make([2, 3])")).toBe(3);
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
            && stmt.right._ === "PtrCallExpr" ? [{ name: stmt.right.method.name, index, args: stmt.right.args }] : []);
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

    it("rejects an unbounded spread without calling the target", () => {
        const source = `
            function add(a: number, b: number): number { return a + b; }
            class Point { constructor(public x: number, public y: number) {} }
            export function sum(values: number[]): number { return add(...values); }
            export function make(values: number[]): Point { return new Point(...values); }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = singleBlockStmts(methodByName(file, "sum"));

        expect(stmts.some((stmt) => stmt._ === "AssignStmt"
            && (stmt.right._ === "PtrCallExpr" || stmt.right._ === "StaticCallExpr")
            && stmt.right.method.name === "add")).toBe(false);
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue"
            && stmt.right.kindName === "CallExpression")).toBe(true);
        expect(diagnostics.messages).toEqual(expect.arrayContaining([
            expect.stringContaining("spread argument has no statically known length"),
        ]));

        const constructorStmts = singleBlockStmts(methodByName(file, "make"));
        expect(constructorStmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr")).toBe(false);
        expect(constructorStmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue"
            && stmt.right.kindName === "NewExpression")).toBe(true);
    });
});

describe("computed constructor arrays", () => {
    it("keeps optional computed object constructors guarded and uses property semantics", () => {
        const source = `
            class A { x = 1; }
            export function make(holder: { Ctor: typeof A } | undefined): number {
                return new (holder?.["Ctor"])().x;
            }
        `;
        const { file, diagnostics } = lower(source);
        const method = methodByName(JSON.parse(serializeEtsFile(file)), "make");
        const stmts = method.body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(method.body!.cfg.blocks.length).toBeGreaterThan(1);
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef")).toBe(true);
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef")).toBe(false);
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr"
            && stmt.right.constructorValue?._ === "Local")).toBe(true);
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "exports.make({ Ctor: A })")).toBe(1);
    });

    it("reads an indexed constructor once before allocation and preserves JSON", () => {
        const source = `
            class A { kind = "A"; }
            class B { kind = "B"; }
            let reads = 0;
            function constructors(): Array<typeof A | typeof B> { reads++; return [A, B]; }
            function index(): number { reads++; return 1; }
            export function make(): string { return new (constructors()[index()])().kind; }
            export function count(): number { return reads; }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const stmts = singleBlockStmts(methodByName(roundTrip, "make"));
        const functionCalls = stmts.flatMap((stmt, index) => stmt._ === "AssignStmt"
            && stmt.right._ === "PtrCallExpr" ? [{ name: stmt.right.method.name, index }] : []);
        const readIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef");
        const allocationIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr"
            && stmt.right.constructorValue?._ === "Local");

        expect(functionCalls.map((call) => call.name)).toEqual(["constructors", "index"]);
        expect(functionCalls[1]!.index).toBeLessThan(readIndex);
        expect(readIndex).toBeLessThan(allocationIndex);
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "{ kind: exports.make(), count: exports.count() }")).toEqual({ kind: "B", count: 2 });
    });
});
