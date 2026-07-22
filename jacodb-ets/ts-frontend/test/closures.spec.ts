import { describe, expect, it } from "vitest";
import { AssignStmtDto, StmtDto } from "../src/dto/stmts";
import { defaultMethod, lower, methodByName, singleBlockStmts } from "./util";

function allStmts(source: string): StmtDto[] {
    const { file } = lower(source);
    return file.classes
        .flatMap((c) => c.methods)
        .flatMap((m) => m.body?.cfg.blocks ?? [])
        .flatMap((b) => b.stmts);
}

describe("closures", () => {
    it("lifts arrow functions into %AM methods with FunctionType use-site locals", () => {
        const { file } = lower(`
            let arr = [1, 2, 3];
            arr.map((x: number) => x * 2);
        `);
        const closure = methodByName(file, "%AM0$%dflt");
        expect(closure.body).toBeDefined();
        expect(closure.signature.parameters).toEqual([{ name: "x", type: { _: "NumberType" } }]);

        // Expression body returns the value.
        const stmts = singleBlockStmts(closure);
        expect(stmts[stmts.length - 1]._).toBe("ReturnStmt");

        // Use site: the callback argument is a Local typed FunctionType.
        const callArgs = singleBlockStmts(defaultMethod(file))
            .filter((s): s is Extract<StmtDto, { _: "CallStmt" }> => s._ === "CallStmt")
            .flatMap((s) => s.expr.args);
        expect(callArgs).toContainEqual(
            expect.objectContaining({
                _: "Local",
                name: "%AM0$%dflt",
                type: expect.objectContaining({ _: "FunctionType" }),
            }),
        );
    });

    it("lowers function expressions and block bodies", () => {
        const { file } = lower(`
            const f = function (a: number): number {
                let b = a + 1;
                return b;
            };
            f(1);
        `);
        const closure = methodByName(file, "%AM0$%dflt");
        const stmts = singleBlockStmts(closure);
        expect(stmts.some((s) => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "b")).toBe(true);
    });

    it("numbers nested closures by their enclosing method", () => {
        const { file } = lower(`
            const outer = () => {
                const inner = () => 1;
                return inner;
            };
        `);
        expect(methodByName(file, "%AM0$%dflt")).toBeDefined();
        expect(methodByName(file, "%AM1$%AM0$%dflt")).toBeDefined();
    });

    it("reads and writes captured locals through a lexical environment", () => {
        const { file } = lower(`
            function outer(seed: number): (delta: number) => number {
                let current = seed;
                return (delta: number): number => {
                    current += delta;
                    return current;
                };
            }
        `);
        const closure = methodByName(file, "%AM0$outer");
        expect(closure.signature.parameters).toMatchObject([
            {
                name: "%closures0",
                type: {
                    _: "LexicalEnvType",
                    closures: [{ name: "current", type: { _: "NumberType" } }],
                },
            },
            { name: "delta", type: { _: "NumberType" } },
        ]);
        const stmts = singleBlockStmts(closure);
        expect(stmts.slice(0, 2)).toMatchObject([
            { left: { name: "%closures0" }, right: { _: "ParameterRef", index: 0 } },
            { left: { name: "delta" }, right: { _: "ParameterRef", index: 1 } },
        ]);
        expect(stmts).toContainEqual(expect.objectContaining({
            left: expect.objectContaining({
                _: "ClosureFieldRef",
                base: { name: "%closures0", type: expect.anything() },
                fieldName: "current",
            }),
        }));
        expect(closure.body!.locals).not.toContainEqual(expect.objectContaining({ name: "current" }));
        const outer = methodByName(file, "outer");
        expect(outer.body!.locals).toContainEqual(expect.objectContaining({
            name: "%closures0",
            type: expect.objectContaining({ _: "LexicalEnvType" }),
        }));
    });

    it("retains lexical this and attaches lifted methods to the enclosing class", () => {
        const { file } = lower(`
            class Counter {
                value = 1;
                make(step: number): () => number {
                    return () => this.value + step;
                }
            }
        `);
        const counter = file.classes.find((clazz) => clazz.signature.name === "Counter")!;
        const closure = counter.methods.find((method) => method.signature.name === "%AM0$make");
        expect(closure).toBeDefined();
        expect(closure!.signature.declaringClass.name).toBe("Counter");
        expect(closure!.body!.locals).toContainEqual(expect.objectContaining({
            name: "this",
            type: { _: "ClassType", signature: expect.objectContaining({ name: "Counter" }) },
        }));
        expect(file.classes.find((clazz) => clazz.signature.name === "%dflt")!.methods).not.toContainEqual(
            expect.objectContaining({ signature: expect.objectContaining({ name: "%AM0$make" }) }),
        );
    });

    it("propagates transitive captures through nested closures", () => {
        const { file } = lower(`
            function outer(seed: number): () => () => number {
                let current = seed;
                return () => () => current;
            }
        `);
        const middle = methodByName(file, "%AM0$outer");
        const inner = methodByName(file, "%AM1$%AM0$outer");
        for (const closure of [middle, inner]) {
            expect(closure.signature.parameters[0]).toMatchObject({
                type: { _: "LexicalEnvType", closures: [{ name: "current" }] },
            });
            expect(singleBlockStmts(closure)).toContainEqual(expect.objectContaining({
                right: expect.objectContaining({ _: "ClosureFieldRef", fieldName: "current" }),
            }));
        }
        expect(inner.signature.parameters[0].name).toBe("%closures1");
        expect(middle.body!.locals.filter((local) => local.type._ === "LexicalEnvType").map((local) => local.name)).toEqual([
            "%closures0",
            "%closures1",
        ]);
    });

    it("hoists nested function declarations and captures their outer bindings", () => {
        const { file } = lower(`
            function outer(seed: number): number {
                return inner();
                function inner(): number {
                    seed++;
                    return seed;
                }
            }
        `);
        const outer = methodByName(file, "outer");
        const inner = methodByName(file, "%AM0$outer");
        expect(singleBlockStmts(outer)).toContainEqual(expect.objectContaining({
            right: expect.objectContaining({ _: "PtrCallExpr", ptr: expect.objectContaining({ _: "Local" }) }),
        }));
        expect(singleBlockStmts(inner)).toContainEqual(expect.objectContaining({
            left: expect.objectContaining({ _: "ClosureFieldRef", fieldName: "seed" }),
        }));
    });
});

describe("object literals", () => {
    it("creates %AC classes with fields and per-property stores", () => {
        const { file } = lower(`
            let radius = 3;
            const shape = { kind: "circle", radius, describe(): string { return this.kind; } };
        `);
        const anonClass = file.classes.find((c) => c.signature.name.startsWith("%AC0"));
        expect(anonClass).toBeDefined();
        expect(anonClass!.category).toBe(5);
        expect(anonClass!.fields.map((f) => f.signature.name)).toEqual(["kind", "radius"]);
        expect(anonClass!.methods.map((m) => m.signature.name)).toEqual(["describe"]);
        expect(anonClass!.methods[0].body).toBeDefined();

        const stmts = singleBlockStmts(defaultMethod(file));
        const newExpr = stmts.find(
            (s): s is AssignStmtDto => s._ === "AssignStmt" && s.right._ === "NewExpr",
        );
        expect(newExpr).toBeDefined();
        const fieldStores = stmts.filter(
            (s) => s._ === "AssignStmt" && s.left._ === "InstanceFieldRef",
        );
        expect(fieldStores).toHaveLength(2);
    });
});

describe("destructuring", () => {
    it("unpacks object patterns with renames and defaults", () => {
        const stmts = allStmts(`
            const config = { host: "localhost", port: 8080 };
            const { host, port: p, missing = 42 } = config;
        `);
        const fieldReads = stmts.filter(
            (s): s is AssignStmtDto => s._ === "AssignStmt" && s.right._ === "InstanceFieldRef",
        );
        const bound = fieldReads.map((s) => ({
            local: s.left._ === "Local"
                ? s.left.name
                : s.left._ === "StaticFieldRef"
                  ? s.left.field.name
                  : undefined,
            field: (s.right as { field: { name: string } }).field.name,
        }));
        expect(bound).toContainEqual({ local: "host", field: "host" });
        expect(bound).toContainEqual({ local: "p", field: "port" });
        expect(bound).toContainEqual({ local: "missing", field: "missing" });
        // default: guarded by === undefined check
        expect(
            stmts.some(
                (s) =>
                    s._ === "IfStmt" &&
                    s.condition.op === "===" &&
                    (s.condition.right as { value?: string }).value === "undefined",
            ),
        ).toBe(true);
    });

    it("unpacks array patterns with holes and nesting", () => {
        const stmts = allStmts(`
            const data: [number, number, [string, string]] = [1, 2, ["a", "b"]];
            const [first, , [inner]] = data;
        `);
        const arrayReads = stmts.filter(
            (s): s is AssignStmtDto => s._ === "AssignStmt" && s.right._ === "ArrayRef",
        );
        const indices = arrayReads.map((s) => (s.right as { index: { value: string } }).index.value);
        expect(indices).toContain("0");
        expect(indices).toContain("2");
        expect(indices).not.toContain("1"); // hole skipped
        expect(stmts.some((s) =>
            s._ === "AssignStmt"
            && s.left._ === "StaticFieldRef"
            && s.left.field.name === "inner",
        )).toBe(true);
    });

    it("supports destructuring in for-of", () => {
        const stmts = allStmts(`
            const pairs: [string, number][] = [["a", 1]];
            for (const [key, value] of pairs) {
                console.log(key, value);
            }
        `);
        expect(stmts.some((s) => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "key")).toBe(true);
        expect(stmts.some((s) => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "value")).toBe(true);
    });
});

describe("optional chaining", () => {
    it("guards property access with a null check diamond", () => {
        const stmts = allStmts(`
            class Box { size: number = 1; }
            function f(b: Box | null): number | undefined {
                return b?.size;
            }
        `);
        const nullCheck = stmts.find(
            (s) => s._ === "IfStmt" && (s.condition.right as { value?: string }).value === "null",
        );
        expect(nullCheck).toBeDefined();
        const undefinedAssign = stmts.find(
            (s) =>
                s._ === "AssignStmt" &&
                (s.right as { value?: string }).value === "undefined",
        );
        expect(undefinedAssign).toBeDefined();
    });

    it("guards optional calls", () => {
        const stmts = allStmts(`
            function f(cb?: () => void): void {
                cb?.();
            }
        `);
        expect(stmts.some((s) => s._ === "IfStmt")).toBe(true);
        expect(stmts.some((s) => s._ === "AssignStmt" && s.right._ === "PtrCallExpr")).toBe(true);
    });
});

describe("generators", () => {
    it("lowers yield expressions", () => {
        const { file } = lower(`
            function* gen(): Generator<number> {
                yield 1;
                yield 2;
            }
        `);
        const gen = methodByName(file, "gen");
        const stmts = gen.body!.cfg.blocks.flatMap((b) => b.stmts);
        const yields = stmts.filter((s) => s._ === "AssignStmt" && s.right._ === "YieldExpr");
        expect(yields).toHaveLength(2);
    });
});
