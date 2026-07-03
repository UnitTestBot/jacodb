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

    it("duplicates finally before return (finally never drops out of the IR)", () => {
        const blocks = blocksOf(`
            function f(): number {
                try {
                    return 1;
                } finally {
                    console.log("fin");
                }
            }
        `);
        // try body always returns — the finally must still be present,
        // duplicated BEFORE the ReturnStmt in the same path.
        const returnBlock = blocks.find((b) => b.stmts.some((s) => s._ === "ReturnStmt"))!;
        const stmtKinds = returnBlock.stmts.map((s) => s._);
        const callIdx = returnBlock.stmts.findIndex(
            (s) =>
                s._ === "CallStmt" &&
                ((s.expr.args[0] ?? {}) as { value?: string }).value === "fin",
        );
        const retIdx = stmtKinds.indexOf("ReturnStmt");
        expect(callIdx).toBeGreaterThanOrEqual(0);
        expect(callIdx).toBeLessThan(retIdx);
    });

    it("captures the return value before the finally runs", () => {
        const blocks = blocksOf(`
            function f(): number {
                let x = 1;
                try {
                    return x;
                } finally {
                    x = 2;
                }
            }
        `);
        const returnBlock = blocks.find((b) => b.stmts.some((s) => s._ === "ReturnStmt"))!;
        const ret = returnBlock.stmts.find((s) => s._ === "ReturnStmt") as { arg: { name?: string } };
        // returned value is a snapshot temp, not `x` (which finally mutates)
        expect(ret.arg.name).toMatch(/^%/);
        const copyIdx = returnBlock.stmts.findIndex(
            (s) => s._ === "AssignStmt" && (s.left as { name?: string }).name === ret.arg.name,
        );
        const mutateIdx = returnBlock.stmts.findIndex(
            (s) =>
                s._ === "AssignStmt" &&
                (s.left as { name?: string }).name === "x" &&
                (s.right as { value?: string }).value === "2",
        );
        expect(copyIdx).toBeGreaterThanOrEqual(0);
        expect(mutateIdx).toBeGreaterThan(copyIdx); // snapshot BEFORE the finally mutation
    });

    it("duplicates finally on break out of the try, but not for loops inside the try", () => {
        const stmts = (blocks: { stmts: { _: string }[] }[]) => blocks.flatMap((b) => b.stmts);

        // break LEAVES the try -> finally duplicated on the break path + the normal path
        const breakOut = blocksOf(`
            function f(): void {
                while (true) {
                    try {
                        break;
                    } finally {
                        console.log("fin");
                    }
                }
            }
        `);
        // The try body always breaks, so the normal-path finally is unreachable
        // and dropped; the surviving copy comes from the break-path duplication.
        const finCalls = stmts(breakOut).filter(
            (s) =>
                s._ === "CallStmt" &&
                (((s as { expr: { args: { value?: string }[] } }).expr.args[0] ?? {}).value === "fin"),
        );
        expect(finCalls).toHaveLength(1);

        // break stays INSIDE the try (loop is inside) -> no duplication, single finally
        const breakIn = blocksOf(`
            function f(): void {
                try {
                    while (true) {
                        break;
                    }
                } finally {
                    console.log("fin");
                }
            }
        `);
        const finCallsIn = stmts(breakIn).filter(
            (s) =>
                s._ === "CallStmt" &&
                (((s as { expr: { args: { value?: string }[] } }).expr.args[0] ?? {}).value === "fin"),
        );
        expect(finCallsIn).toHaveLength(1);
    });

    it("duplicates nested finallies innermost-first on return", () => {
        const blocks = blocksOf(`
            function f(): number {
                try {
                    try {
                        return 1;
                    } finally {
                        console.log("inner");
                    }
                } finally {
                    console.log("outer");
                }
            }
        `);
        const returnBlock = blocks.find((b) => b.stmts.some((s) => s._ === "ReturnStmt"))!;
        const order = returnBlock.stmts
            .filter((s) => s._ === "CallStmt")
            .map((s) => ((s as { expr: { args: { value?: string }[] } }).expr.args[0] ?? {}).value);
        expect(order).toEqual(["inner", "outer"]);
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
