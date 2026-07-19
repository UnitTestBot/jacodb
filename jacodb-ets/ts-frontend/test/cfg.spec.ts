import { describe, expect, it } from "vitest";
import { BasicBlockDto, MethodDto } from "../src/dto/model";
import { IfStmtDto, StmtDto } from "../src/dto/stmts";
import { defaultMethod, lower, methodByName } from "./util";

function blocksOf(method: MethodDto): BasicBlockDto[] {
    if (method.body === undefined) throw new Error("no body");
    return method.body.cfg.blocks;
}

function lastStmt(block: BasicBlockDto): StmtDto | undefined {
    return block.stmts[block.stmts.length - 1];
}

function ifBlocks(blocks: BasicBlockDto[]): BasicBlockDto[] {
    return blocks.filter((b) => lastStmt(b)?._ === "IfStmt");
}

/** Collect all stmts of all blocks. */
function allStmts(method: MethodDto): StmtDto[] {
    return blocksOf(method).flatMap((b) => b.stmts);
}

describe("control flow lowering", () => {
    it("records source origins for normalized statements and branch terminators", () => {
        const source = [
            "function f(a: number): number {",
            "    const doubled = a * 2;",
            "    if (doubled > 10) return 1;",
            "    return 0;",
            "}",
        ].join("\n");
        const { file } = lower(source, "proj", "src/origins.ts");
        const body = methodByName(file, "f").body!;
        const origins = body.stmtOrigins!;

        expect(origins.length).toBeGreaterThan(0);
        expect(origins.every((origin) => origin.source.fileName === "src/origins.ts")).toBe(true);

        const ifOrigin = origins.find(({ blockId, stmtIndex }) => {
            return body.cfg.blocks[blockId].stmts[stmtIndex]._ === "IfStmt";
        });
        expect(ifOrigin).toBeDefined();
        expect(ifOrigin!.source.nodeKind).toBe("BinaryExpression");
        expect(source.slice(ifOrigin!.source.startOffset, ifOrigin!.source.endOffset)).toBe("doubled > 10");
        expect(ifOrigin!.source.startLine).toBe(2);
    });

    it("lowers if/else into a diamond with [false, true] successor order", () => {
        const { file } = lower(`
            function f(a: number): number {
                if (a > 0) { return 1; } else { return 2; }
            }
        `);
        const blocks = blocksOf(methodByName(file, "f"));
        const entry = blocks[0];
        const last = lastStmt(entry);
        expect(last).toMatchObject({
            _: "IfStmt",
            condition: { _: "ConditionExpr", op: ">" },
        });
        expect(entry.successors).toHaveLength(2);
        const [falseTarget, trueTarget] = entry.successors;
        // true branch: return 1; false branch: return 2
        expect(lastStmt(blocks[trueTarget])).toMatchObject({ _: "ReturnStmt", arg: { value: "1" } });
        expect(lastStmt(blocks[falseTarget])).toMatchObject({ _: "ReturnStmt", arg: { value: "2" } });
    });

    it("implements JavaScript truthiness for booleans, numbers, strings, objects, and unknown values", () => {
        const { file } = lower(`
            class Box {}
            function f(b: boolean, n: number, s: string, o: Box, u: unknown): void {
                if (b) { console.log(1); }
                if (n) { console.log(2); }
                if (s) { console.log(3); }
                if (o) { console.log(4); }
                if (u) { console.log(5); }
            }
        `);
        const conditions = ifBlocks(blocksOf(methodByName(file, "f"))).map(
            (b) => (lastStmt(b) as IfStmtDto).condition,
        );
        expect(conditions[0]).toMatchObject({
            op: "!==",
            left: { _: "Local", name: "b" },
            right: { _: "Constant", value: "false", type: { _: "BooleanType" } },
        });
        expect(conditions.find((c) => c.left._ === "Local" && c.left.name === "n" && c.right._ === "Constant")).toMatchObject({
            op: "!==",
            left: { _: "Local", name: "n" },
            right: { _: "Constant", value: "0", type: { _: "NumberType" } },
        });
        expect(conditions).toContainEqual(expect.objectContaining({
            op: "===",
            left: expect.objectContaining({ _: "Local", name: "n" }),
            right: expect.objectContaining({ _: "Local", name: "n" }),
        }));
        expect(conditions).toContainEqual(expect.objectContaining({
            op: "!==",
            left: expect.objectContaining({ _: "Local", name: "s" }),
            right: expect.objectContaining({ _: "Constant", value: "" }),
        }));
        // Objects are statically always truthy, so their condition needs no IfStmt.
        expect(conditions.some((c) => c.left._ === "Local" && c.left.name === "o")).toBe(false);
        // Unknown values cover false, zero, empty string, nullish, and NaN.
        expect(conditions.filter((c) => c.left._ === "Local" && c.left.name === "u")).toHaveLength(5);
    });

    it("swaps branches for negated conditions", () => {
        const { file } = lower(`
            function f(b: boolean): number {
                if (!b) { return 1; }
                return 2;
            }
        `);
        const blocks = blocksOf(methodByName(file, "f"));
        const entry = blocks[0];
        // `!b` swaps targets: true branch of the emitted `b != false` goes to `return 2`.
        const [falseTarget, trueTarget] = entry.successors;
        expect(lastStmt(blocks[trueTarget])).toMatchObject({ _: "ReturnStmt", arg: { value: "2" } });
        expect(lastStmt(blocks[falseTarget])).toMatchObject({ _: "ReturnStmt", arg: { value: "1" } });
    });

    it("lowers while loops with a back edge", () => {
        const { file } = lower(`
            function f(n: number): number {
                let i = 0;
                while (i < n) { i++; }
                return i;
            }
        `);
        const blocks = blocksOf(methodByName(file, "f"));
        const headBlock = ifBlocks(blocks)[0];
        const [, bodyId] = headBlock.successors; // [exit, body]
        const body = blocks[bodyId];
        expect(body.successors).toEqual([headBlock.id]); // back edge
        expect(body.stmts.some((s) => s._ === "AssignStmt" && s.right._ === "UnopExpr")).toBe(true);
    });

    it("lowers do-while with the body before the condition", () => {
        const { file } = lower(`
            function f(n: number): number {
                let i = 0;
                do { i++; } while (i < n);
                return i;
            }
        `);
        const blocks = blocksOf(methodByName(file, "f"));
        // entry falls into the body; condition block branches back to the body
        const condBlock = ifBlocks(blocks)[0];
        const [, trueTarget] = condBlock.successors;
        const bodyBlock = blocks[trueTarget];
        expect(bodyBlock.stmts.some((s) => s._ === "AssignStmt" && s.right._ === "UnopExpr")).toBe(true);
        expect(bodyBlock.successors).toEqual([condBlock.id]);
    });

    it("lowers for loops: continue targets the update block", () => {
        const { file } = lower(`
            function f(): number {
                let s = 0;
                for (let i = 0; i < 10; i++) {
                    if (i === 5) { continue; }
                    s += i;
                }
                return s;
            }
        `);
        const blocks = blocksOf(methodByName(file, "f"));
        // The continue block jumps to the update block which contains i := i ++
        const continueTargets = blocks
            .filter((b) => b.successors.length === 1)
            .map((b) => blocks[b.successors[0]]);
        const updateBlocks = continueTargets.filter((b) =>
            b.stmts.some(
                (s) =>
                    s._ === "AssignStmt" &&
                    s.left._ === "Local" &&
                    s.left.name === "i" &&
                    s.right._ === "UnopExpr" &&
                    s.right.op === "++",
            ),
        );
        expect(updateBlocks.length).toBeGreaterThanOrEqual(2); // from body end AND from continue
    });

    it("lowers break out of a loop", () => {
        const { file } = lower(`
            function f(): number {
                let i = 0;
                while (true) {
                    if (i > 3) { break; }
                    i++;
                }
                return i;
            }
        `);
        const blocks = blocksOf(methodByName(file, "f"));
        const returnBlock = blocks.find((b) => lastStmt(b)?._ === "ReturnStmt")!;
        // some block inside the loop jumps straight to the return block's region
        expect(returnBlock.predecessors!.length).toBeGreaterThan(0);
    });

    it("lowers ternary into a temp diamond", () => {
        const { file } = lower("let a = 1;\nlet x = a > 0 ? 'pos' : 'neg';");
        const blocks = blocksOf(defaultMethod(file));
        expect(blocks.length).toBeGreaterThanOrEqual(4);
        const assignsToTemp = blocks
            .flatMap((b) => b.stmts)
            .filter(
                (s): s is Extract<StmtDto, { _: "AssignStmt" }> =>
                    s._ === "AssignStmt" &&
                    s.left._ === "Local" &&
                    s.left.name.startsWith("%") &&
                    s.right._ === "Constant",
            );
        const values = assignsToTemp.map((s) => (s.right as { value: string }).value).sort();
        expect(values).toEqual(["neg", "pos"]);
    });

    it("lowers switch with === chain and fallthrough", () => {
        const { file } = lower(`
            function f(x: number): string {
                switch (x) {
                    case 1:
                    case 2:
                        return "small";
                    case 3:
                        return "three";
                    default:
                        return "big";
                }
            }
        `);
        const method = methodByName(file, "f");
        const stmts = allStmts(method);
        const conditions = stmts.filter(
            (s): s is IfStmtDto => s._ === "IfStmt" && s.condition.op === "===",
        );
        expect(conditions).toHaveLength(3);
        // empty case 1 falls through to case 2's body
        const returns = stmts.filter((s) => s._ === "ReturnStmt");
        expect(returns).toHaveLength(3);
    });

    it("lowers for-of via the iterator protocol", () => {
        const { file } = lower(`
            let arr = [1, 2, 3];
            for (const v of arr) { console.log(v); }
        `);
        const method = defaultMethod(file);
        const stmts = allStmts(method);
        const calls = stmts.filter((s) => s._ === "AssignStmt" && s.right._ === "InstanceCallExpr");
        const methodNames = calls.map(
            (s) => ((s as { right: { method: { name: string } } }).right.method.name),
        );
        expect(methodNames).toContain("Symbol.iterator");
        expect(methodNames).toContain("next");
        const fieldReads = stmts
            .filter((s) => s._ === "AssignStmt" && s.right._ === "InstanceFieldRef")
            .map((s) => (s as { right: { field: { name: string } } }).right.field.name);
        expect(fieldReads).toContain("done");
        expect(fieldReads).toContain("value");
        // loop variable is bound
        expect(method.body!.locals.some((l) => l.name === "v")).toBe(true);
    });

    it("lowers for-in via Object.keys indexing", () => {
        const { file } = lower(`
            let obj = [1];
            for (const k in obj) { console.log(k); }
        `);
        const stmts = allStmts(defaultMethod(file));
        const staticCalls = stmts.filter((s) => s._ === "AssignStmt" && s.right._ === "StaticCallExpr");
        expect(staticCalls.length).toBeGreaterThan(0);
        expect(staticCalls[0]).toMatchObject({
            right: { method: { declaringClass: { name: "Object" }, name: "keys" } },
        });
    });

    it("supports labeled break from nested loops", () => {
        const { file } = lower(`
            function f(): number {
                let c = 0;
                outer: for (let i = 0; i < 3; i++) {
                    for (let j = 0; j < 3; j++) {
                        if (j > i) { break outer; }
                        c++;
                    }
                }
                return c;
            }
        `);
        // must lower without raw fallbacks
        const stmts = allStmts(methodByName(file, "f"));
        expect(stmts.every((s) => s._ !== ("UnsupportedStmt" as never))).toBe(true);
    });

    it("drops unreachable code after return", () => {
        const { file } = lower(`
            function f(): number {
                return 1;
                console.log("never");
            }
        `);
        const stmts = allStmts(methodByName(file, "f"));
        expect(stmts.some((s) => s._ === "CallStmt")).toBe(false);
    });

    it.each([
        ["&&", 1],
        ["||", 0],
        ["??", null],
    ])("short-circuits %s and evaluates the RHS only in its branch", (operator, initial) => {
        const { file } = lower(`
            function sideEffect(): number { return 7; }
            function f(a: number | null): number {
                a = ${JSON.stringify(initial)};
                return a ${operator} sideEffect();
            }
        `);
        const blocks = blocksOf(methodByName(file, "f"));
        const entry = blocks[0];
        const callBlock = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "sideEffect",
        ));
        expect(lastStmt(entry)).toMatchObject({ _: "IfStmt" });
        expect(callBlock).toBeDefined();
        expect(callBlock!.id).not.toBe(entry.id);
        expect(blocks.some((block) => lastStmt(block)?._ === "IfStmt" && block.successors.includes(callBlock!.id))).toBe(true);
        expect(allStmts(methodByName(file, "f")).some(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "BinopExpr" && stmt.right.op === operator,
        )).toBe(false);
    });

    it.each(["&&=", "||=", "??="])("short-circuits logical assignment %s", (operator) => {
        const { file } = lower(`
            function sideEffect(): number { return 7; }
            function f(a: number | null): number { return (a ${operator} sideEffect()); }
        `);
        const blocks = blocksOf(methodByName(file, "f"));
        const entry = blocks[0];
        const callBlock = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "sideEffect",
        ));
        expect(lastStmt(entry)).toMatchObject({ _: "IfStmt" });
        expect(callBlock).toBeDefined();
        expect(callBlock!.id).not.toBe(entry.id);
        expect(blocks.some((block) => lastStmt(block)?._ === "IfStmt" && block.successors.includes(callBlock!.id))).toBe(true);
    });
});
