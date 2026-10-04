import { describe, expect, it } from "vitest";
import { lower, methodByName } from "./util";
import * as ts from "typescript";
import { executor } from "./execute";
import { serializeEtsFile } from "../src/serialize";
import { StmtDto } from "../src/dto/stmts";

function allStmts(method: { body?: { cfg: { blocks: { stmts: StmtDto[] }[] } } }): StmtDto[] {
    return (method.body?.cfg.blocks ?? []).flatMap((block) => block.stmts);
}

describe("regular-expression literals", () => {
    it("retains pattern, flags, allocation, and test call through JSON", () => {
        const source = `function containsX(value: string): boolean { return /x+/gi.test(value); }`;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const stmts = allStmts(methodByName(roundTrip, "containsX"));
        const allocations = stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr");
        const calls = stmts.filter((stmt) => stmt._ === "AssignStmt"
            && (stmt.right._ === "InstanceCallExpr" || stmt.right._ === "PtrCallExpr"));

        expect(diagnostics.messages).toEqual([]);
        expect(allocations).toHaveLength(1);
        expect(calls).toHaveLength(2);
        expect(calls[0]).toMatchObject({
            right: {
                method: { name: "constructor" },
                args: [{ _: "Constant", value: "x+" }, { _: "Constant", value: "gi" }],
            },
        });
        expect(calls[1]).toMatchObject({
            right: { method: { name: "test" } },
        });

        const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
        const concrete = new Function(`${js}\nreturn [containsX("XX"), containsX("ab")];`)() as boolean[];
        expect(concrete).toEqual([true, false]);
        const ir = executor(roundTrip);
        expect([ir.call("containsX", "XX"), ir.call("containsX", "ab")]).toEqual(concrete);
    });
});
