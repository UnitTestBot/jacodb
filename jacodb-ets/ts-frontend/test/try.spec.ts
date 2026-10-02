import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { BasicBlockDto } from "../src/dto/model";
import { defaultMethod, lower, methodByName } from "./util";

function blocksOf(source: string, method: string = "f"): BasicBlockDto[] {
    const { file } = lower(source);
    const m = method === "%dflt" ? defaultMethod(file) : methodByName(file, method);
    return m.body!.cfg.blocks;
}

describe("try/catch/finally lowering", () => {
    it("does not enter catch when the try body cannot throw", () => {
        const source = `
            function f(): number {
                try { return 1; } catch { return 2; }
            }
        `;
        const blocks = blocksOf(source);
        const javascript = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;

        const returns = blocks.flatMap((block) => block.stmts)
            .filter((stmt) => stmt._ === "ReturnStmt");

        expect(runInNewContext(`${javascript}\nf();`)).toBe(1);
        expect(returns).toEqual([{ _: "ReturnStmt", arg: expect.objectContaining({ value: "1" }) }]);
        expect(blocks.flatMap((block) => block.exceptionalSuccessors ?? [])).toHaveLength(0);
    });

    it("does not add a catch edge for arithmetic on numbers", () => {
        const blocks = blocksOf(`
            function f(x: number): number {
                try { return x + 1; } catch { return 2; }
            }
        `);

        expect(blocks.flatMap((block) => block.exceptionalSuccessors ?? [])).toHaveLength(0);
        expect(blocks.flatMap((block) => block.stmts).filter((stmt) => stmt._ === "ReturnStmt")).toHaveLength(1);
    });

    it("keeps catch code reachable and binds CaughtExceptionRef", () => {
        const blocks = blocksOf(`
            function f(fail: boolean): number {
                let r = 0;
                try {
                    if (fail) throw 3;
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
        const throwBlock = blocks.find((block) => block.stmts.some((stmt) => stmt._ === "ThrowStmt"))!;
        const throwIndex = throwBlock.stmts.findIndex((stmt) => stmt._ === "ThrowStmt");
        const catchBlock = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "CaughtExceptionRef",
        ))!;

        expect(throwBlock.successors).toHaveLength(0);
        expect(throwBlock.exceptionalSuccessors).toContainEqual({ stmtIndex: throwIndex, target: catchBlock.id });
    });

    it("omits an unused catch binding while preserving its return", () => {
        const blocks = blocksOf(`
            function f(fail: boolean): boolean {
                try {
                    if (fail) throw 1;
                    return true;
                } catch (error) {
                    return false;
                }
            }
        `);

        const stmts = blocks.flatMap((block) => block.stmts);
        const catchBindings = stmts.filter(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "CaughtExceptionRef",
        );
        const returns = stmts.filter((stmt) => stmt._ === "ReturnStmt");

        expect(catchBindings).toHaveLength(0);
        expect(returns).toHaveLength(2);
    });

    it("ignores matching property names and shadowed locals in a catch body", () => {
        const sources = [
            `function f(obj: { error: number }): number {
                try { return 0; } catch (error) { return obj.error; }
            }`,
            `function f(): number {
                try { return 0; } catch (error) {
                    { const error = 1; return error; }
                }
            }`,
            `function f(): number {
                try { return 0; } catch (error) {
                    type CaughtType = typeof error;
                    return 1;
                }
            }`,
        ];

        for (const source of sources) {
            const stmts = blocksOf(source).flatMap((block) => block.stmts);
            const catchBindings = stmts.filter(
                (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "CaughtExceptionRef",
            );

            expect(catchBindings).toHaveLength(0);
        }
    });

    it("keeps a catch binding referenced by a shorthand property", () => {
        const stmts = blocksOf(`
            function f(): object {
                try { return {}; } catch (error) { return { error }; }
            }
        `).flatMap((block) => block.stmts);
        const catchBindings = stmts.filter(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "CaughtExceptionRef",
        );

        expect(catchBindings).toHaveLength(1);
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
        expect(logged).toEqual(["try", "finally", "finally"]);
        expect(blocks.flatMap((block) => block.exceptionalSuccessors ?? [])).toHaveLength(1);
    });

    it("runs finally and rethrows when a call fails", () => {
        const blocks = blocksOf(`
            function risky(): void { throw 1; }
            function f(): void {
                try { risky(); } finally { console.log("finally"); }
            }
        `);

        const callBlock = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "CallStmt" && stmt.expr.method.name === "risky",
        ))!;
        const callIndex = callBlock.stmts.findIndex(
            (stmt) => stmt._ === "CallStmt" && stmt.expr.method.name === "risky",
        );
        const handlerId = callBlock.exceptionalSuccessors?.find((edge) => edge.stmtIndex === callIndex)?.target;
        const handler = blocks[handlerId!];

        expect(handlerId).toBeDefined();
        expect(handler.stmts[0]).toMatchObject({ _: "AssignStmt", right: { _: "CaughtExceptionRef" } });
        expect(handler.stmts.some((stmt) => stmt._ === "CallStmt" && stmt.expr.args[0]?._ === "Constant"
            && stmt.expr.args[0].value === "finally")).toBe(true);
        expect(handler.stmts.at(-1)?._).toBe("ThrowStmt");
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

    it("captures the thrown value before the finally runs", () => {
        const blocks = blocksOf(`
            function f(x: number): void {
                try {
                    throw x;
                } finally {
                    x = 2;
                }
            }
        `);
        const throwBlock = blocks.find((block) => block.stmts.some((stmt) => stmt._ === "ThrowStmt"))!;
        const thrown = throwBlock.stmts.find((stmt) => stmt._ === "ThrowStmt") as { arg: { name?: string } };
        expect(thrown.arg.name).toMatch(/^%/);
        const copyIdx = throwBlock.stmts.findIndex(
            (stmt) => stmt._ === "AssignStmt" && (stmt.left as { name?: string }).name === thrown.arg.name,
        );
        const mutateIdx = throwBlock.stmts.findIndex(
            (stmt) => stmt._ === "AssignStmt"
                && (stmt.left as { name?: string }).name === "x"
                && (stmt.right as { value?: string }).value === "2",
        );
        expect(copyIdx).toBeGreaterThanOrEqual(0);
        expect(mutateIdx).toBeGreaterThan(copyIdx);
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
        expect(caught).toHaveLength(0);
        expect(stmts.filter((s) => s._ === "ReturnStmt")).toHaveLength(2);
    });

    it("routes a nested throw to the nearest catch", () => {
        const blocks = blocksOf(`
            function f(x: number): number {
                try {
                    try { throw x; } catch (inner) { return inner; }
                } catch (outer) { return outer; }
            }
        `);

        const throwBlock = blocks.find((block) => block.stmts.some((stmt) => stmt._ === "ThrowStmt"))!;
        const innerCatch = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.left._ === "Local" && stmt.left.name === "inner",
        ))!;
        const outerCatch = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.left._ === "Local" && stmt.left.name === "outer",
        ));

        expect(throwBlock.exceptionalSuccessors).toContainEqual({
            stmtIndex: throwBlock.stmts.findIndex((stmt) => stmt._ === "ThrowStmt"),
            target: innerCatch.id,
        });
        expect(outerCatch).toBeUndefined();
    });

    it("records an exceptional edge for a call without turning it into a normal branch", () => {
        const blocks = blocksOf(`
            function risky(): void { throw 1; }
            function f(): number {
                try { risky(); return 1; } catch { return 2; }
            }
        `);

        const callBlock = blocks.find((block) => block.stmts.some((stmt) => stmt._ === "CallStmt"))!;
        const callIndex = callBlock.stmts.findIndex((stmt) => stmt._ === "CallStmt");
        const catcherId = callBlock.exceptionalSuccessors?.find((edge) => edge.stmtIndex === callIndex)?.target;

        expect(catcherId).toBeDefined();
        expect(blocks[catcherId!].stmts).toContainEqual({
            _: "ReturnStmt",
            arg: expect.objectContaining({ value: "2" }),
        });
        expect(callBlock.stmts.at(-1)).toMatchObject({ _: "ReturnStmt", arg: { value: "1" } });
    });

    it("lets an outer catch handle a throw from an inner finally", () => {
        const blocks = blocksOf(`
            function f(): number {
                try {
                    try { return 1; } catch { return 2; } finally { throw 3; }
                } catch (outer) { return outer; }
            }
        `);

        const throwBlock = blocks.find((block) => block.stmts.some((stmt) => stmt._ === "ThrowStmt"))!;
        const outerCatch = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.left._ === "Local" && stmt.left.name === "outer",
        ))!;
        const returnedConstants = blocks.flatMap((block) => block.stmts)
            .filter((stmt) => stmt._ === "ReturnStmt" && stmt.arg._ === "Constant")
            .map((stmt) => stmt.arg.value);

        expect(throwBlock.exceptionalSuccessors).toContainEqual({
            stmtIndex: throwBlock.stmts.findIndex((stmt) => stmt._ === "ThrowStmt"),
            target: outerCatch.id,
        });
        expect(returnedConstants).not.toContain("2");
    });
});
