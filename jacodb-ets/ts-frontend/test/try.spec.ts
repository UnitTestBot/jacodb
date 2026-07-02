import { describe, expect, it } from "vitest";
import { BasicBlockDto } from "../src/dto/model";
import { defaultMethod, lower, methodByName } from "./util";

function blocksOf(source: string, method: string = "f"): BasicBlockDto[] {
    const { file } = lower(source);
    const m = method === "%dflt" ? defaultMethod(file) : methodByName(file, method);
    return m.body!.cfg.blocks;
}

describe("try/catch/finally lowering", () => {
    it("keeps catch code reachable and binds CaughtExceptionRef", () => {
        const blocks = blocksOf(`
            function f(): number {
                let r = 0;
                try {
                    r = 1;
                } catch (e) {
                    console.log(e);
                    r = 2;
                }
                return r;
            }
        `);
        const stmts = blocks.flatMap((b) => b.stmts);
        // catch binding: e := CaughtExceptionRef
        const caught = stmts.find(
            (s) => s._ === "AssignStmt" && s.right._ === "CaughtExceptionRef",
        );
        expect(caught).toMatchObject({ left: { _: "Local", name: "e" } });
        // catch body code is present (r = 2)
        const rAssigns = stmts.filter(
            (s) => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "r",
        );
        const values = rAssigns
            .map((s) => (s as { right: { value?: string } }).right.value)
            .filter((v) => v !== undefined);
        expect(values).toContain("1");
        expect(values).toContain("2");
        // entry branches nondeterministically between try and catch
        expect(blocks[0].successors).toHaveLength(2);
    });

    it("joins try and catch paths on the finally block", () => {
        const blocks = blocksOf(`
            function f(): void {
                try {
                    console.log("try");
                } catch {
                    console.log("catch");
                } finally {
                    console.log("finally");
                }
            }
        `);
        // find the block containing the finally call: it must have 2 predecessors
        const finallyBlock = blocks.find((b) =>
            b.stmts.some(
                (s) =>
                    s._ === "CallStmt" &&
                    s.expr._ === "InstanceCallExpr" &&
                    (s.expr.args[0] as { value?: string })?.value === "finally",
            ),
        );
        expect(finallyBlock).toBeDefined();
        expect(finallyBlock!.predecessors!.length).toBe(2);
    });

    it("handles try/finally without catch", () => {
        const blocks = blocksOf(`
            function f(): void {
                try {
                    console.log("try");
                } finally {
                    console.log("finally");
                }
            }
        `);
        const stmts = blocks.flatMap((b) => b.stmts);
        const logged = stmts
            .filter((s) => s._ === "CallStmt")
            .map((s) => ((s as { expr: { args: { value?: string }[] } }).expr.args[0] ?? {}).value);
        expect(logged).toEqual(["try", "finally"]);
    });

    it("supports throw inside try and nested try", () => {
        const blocks = blocksOf(`
            function f(x: number): number {
                try {
                    if (x > 0) {
                        throw new Error("positive");
                    }
                    try {
                        return 1;
                    } catch (inner) {
                        return 2;
                    }
                } catch (outer) {
                    return 3;
                }
            }
        `);
        const stmts = blocks.flatMap((b) => b.stmts);
        expect(stmts.some((s) => s._ === "ThrowStmt")).toBe(true);
        const caught = stmts.filter((s) => s._ === "AssignStmt" && s.right._ === "CaughtExceptionRef");
        expect(caught).toHaveLength(2);
    });
});
