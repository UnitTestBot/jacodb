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

describe("computed constructors", () => {
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
