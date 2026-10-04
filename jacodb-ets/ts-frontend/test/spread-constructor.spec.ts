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

describe("constructor call spreads", () => {
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

    it("rejects an unbounded spread without calling the target", () => {
        const source = `
            function add(a: number, b: number): number { return a + b; }
            class Point { constructor(public x: number, public y: number) {} }
            export function sum(values: number[]): number { return add(...values); }
            export function make(values: number[]): Point { return new Point(...values); }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = singleBlockStmts(methodByName(file, "sum"));

        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr"
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
