import { describe, expect, it } from "vitest";
import { executor } from "./execute";
import { AssignStmtDto, StmtDto } from "../src/dto/stmts";
import { serializeEtsFile } from "../src/serialize";
import { syntaxKindName } from "../src/lowering/diagnostics";
import * as ts from "typescript";
import { validateEtsFile } from "../src/validate";
import { defaultMethod, lower, lowerProject, methodByName, singleBlockStmts } from "./util";

function allStmts(method: { body?: { cfg: { blocks: { stmts: StmtDto[] }[] } } }): StmtDto[] {
    return (method.body?.cfg.blocks ?? []).flatMap((block) => block.stmts);
}

describe("unsupported loop bindings do not break the whole file", () => {
    // `lower()` throws on unplaced labels via finalize(), so merely lowering is the assertion.
    const cases: Record<string, string> = {
        "array pattern with rest": "declare const xs: any[]; for (const [[a], ...rest] of xs) { console.log(a); }",
        "member expression target": "declare const xs: any[]; const obj: any = {}; for (obj.x of xs) { console.log(obj.x); }",
        "rest in for-in": "declare const o: any; for (const [[a], ...rest] in o) { console.log(a); }",
    };

    for (const [name, source] of Object.entries(cases)) {
        it(`lowers '${name}' into a raw fallback instead of failing`, () => {
            // `lower()` runs finalize() + validateEtsFile(), so an unplaced label would throw here.
            const { file, diagnostics } = lower(source);
            const stmts = file.classes.flatMap((c) => c.methods.flatMap((m) => allStmts(m)));
            expect(stmts.some((s) => s._ === "UnsupportedStmt")).toBe(true);
            expect(diagnostics.messages.some((msg) => msg.includes("unsupported loop binding"))).toBe(true);
        });
    }
});

it("lowers a computed object key in a for-of binding", () => {
    const { file, diagnostics } = lower(`
        const xs = [{ a: 1 }];
        const key = "a";
        for (const { [key]: value } of xs) { console.log(value); }
    `);
    const stmts = file.classes.flatMap((clazz) => clazz.methods.flatMap((method) => allStmts(method)));

    expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef")).toBe(true);
    expect(stmts.some((stmt) => stmt._ === "UnsupportedStmt")).toBe(false);
    expect(diagnostics.messages).toEqual([]);
});

describe("SyntaxKind names", () => {
    it("resolves real names instead of marker aliases", () => {
        expect(syntaxKindName(ts.SyntaxKind.VariableStatement)).toBe("VariableStatement");
        expect(syntaxKindName(ts.SyntaxKind.NumericLiteral)).toBe("NumericLiteral");
        expect(syntaxKindName(ts.SyntaxKind.ReturnStatement)).toBe("ReturnStatement");
    });
});

describe("pattern parameters", () => {
    it("gives distinct names to destructuring parameters", () => {
        const { file } = lower("function f({ a }: any, [b]: any[]): void {}");
        const names = methodByName(file, "f").signature.parameters.map((p) => p.name);
        expect(new Set(names).size).toBe(names.length);
    });

    it("unpacks an object pattern parameter from its ParameterRef before the body", () => {
        const { file } = lower("function g({ x }: { x: number }) { return x; }");
        const stmts = allStmts(methodByName(file, "g"));

        expect(stmts.some((stmt) =>
            stmt._ === "AssignStmt"
            && stmt.left._ === "Local"
            && stmt.left.name === "%pat0"
            && stmt.right._ === "ParameterRef"
            && stmt.right.index === 0,
        )).toBe(true);
        expect(stmts.some((stmt) =>
            stmt._ === "AssignStmt"
            && stmt.left._ === "Local"
            && stmt.left.name === "x"
            && stmt.right._ === "PropertyRef"
            && stmt.right.instance._ === "Local"
            && stmt.right.key._ === "Constant"
            && stmt.right.key.value === "x",
        )).toBe(true);
    });

    it("unpacks object pattern parameters in closure prologues", () => {
        const { file } = lower("function wrap() { return ({ x }: { x: number }) => x; }");
        const stmts = allStmts(methodByName(file, "%AM0$wrap"));

        expect(stmts.some((stmt) =>
            stmt._ === "AssignStmt"
            && stmt.left._ === "Local"
            && stmt.left.name === "x"
            && stmt.right._ === "PropertyRef"
            && stmt.right.instance._ === "Local"
            && stmt.right.key._ === "Constant"
            && stmt.right.key.value === "x",
        )).toBe(true);
    });
});

describe("default parameter initializers", () => {
    it("captures outer values referenced only by a closure's parameter default", () => {
        const { file, diagnostics } = lower("function wrap(seed: number) { return (value = seed) => value; }");
        const closure = methodByName(file, "%AM0$wrap");

        expect(diagnostics.messages).toEqual([]);
        expect(allStmts(closure).some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ClosureFieldRef"
            && stmt.right.fieldName === "seed")).toBe(true);
    });

    it("keeps delayed temporal-dead-zone captures explicitly unsupported", () => {
        const { file, diagnostics } = lower("function f(a = () => b, b = a()) { return b; }");

        expect(diagnostics.messages.some((message) => message.includes("runtime temporal-dead-zone cell"))).toBe(true);
        expect(allStmts(methodByName(file, "f")).some((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "UnsupportedValue")).toBe(true);
    });

    it("guards defaults after their own argument reads in source order through JSON", () => {
        const source = `
            const order: string[] = [];
            function record(label: string, value: number): number { order.push(label); return value; }
            function value(x = record("x", 3), y = record("y", 4)): number { return x + y; }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const body = methodByName(roundTrip, "value").body!;
        const stmts = allStmts({ body });
        const argumentReads = stmts.flatMap((stmt, index) =>
            stmt._ === "AssignStmt" && stmt.right._ === "ParameterRef"
                ? [{ index, parameterIndex: stmt.right.index }]
                : [],
        );
        const defaults = stmts.flatMap((stmt, index) =>
            stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr" && stmt.right.method.name === "record"
                ? [{ index, label: stmt.right.args[0] }]
                : [],
        );
        const conditions = stmts.filter((stmt) => stmt._ === "IfStmt").map((stmt) => stmt.condition);

        expect(diagnostics.messages).toEqual([]);
        expect(argumentReads.map((read) => read.parameterIndex)).toEqual([0, 1]);
        expect(defaults.map((call) => call.label)).toMatchObject([
            { _: "Constant", value: "x" },
            { _: "Constant", value: "y" },
        ]);
        expect(argumentReads[0].index).toBeLessThan(defaults[0].index);
        expect(defaults[0].index).toBeLessThan(argumentReads[1].index);
        expect(argumentReads[1].index).toBeLessThan(defaults[1].index);
        expect(conditions).toEqual([
            expect.objectContaining({ _: "ConditionExpr", op: "===", right: { _: "Constant", value: "undefined", type: { _: "UndefinedType" } } }),
            expect.objectContaining({ _: "ConditionExpr", op: "===", right: { _: "Constant", value: "undefined", type: { _: "UndefinedType" } } }),
        ]);

        const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
        const concrete = new Function(`${js}\nreturn {
            missing: value(),
            undefinedValue: value(undefined, undefined),
            zero: value(0, 1),
            order,
        };`)() as { missing: number; undefinedValue: number; zero: number; order: string[] };
        expect(concrete).toEqual({ missing: 7, undefinedValue: 7, zero: 1, order: ["x", "y", "x", "y"] });
        const ir = executor(roundTrip);
        ir.initialize();
        expect([ir.call("value"), ir.call("value", undefined, undefined), ir.call("value", 0, 1)]).toEqual([7, 7, 1]);
    });
});

describe("rest parameter calls", () => {
    it("preserves the rest signature and array-valued argument slot through JSON", () => {
        const source = `
            function count(...values: number[]): number { return values.length; }
            function sample(): number { return count() + count(1, 2); }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const method = methodByName(roundTrip, "count");
        const stmts = allStmts(method);

        expect(diagnostics.messages).toEqual([]);
        expect(method.signature.parameters).toMatchObject([
            { name: "values", type: { _: "ArrayType" }, isRest: true },
        ]);
        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            left: expect.objectContaining({ _: "Local", name: "values", type: expect.objectContaining({ _: "ArrayType" }) }),
            right: expect.objectContaining({ _: "ParameterRef", index: 0, type: expect.objectContaining({ _: "ArrayType" }) }),
        }));
        const callArgs = allStmts(methodByName(roundTrip, "sample"))
            .filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr"
                && stmt.right.method.name === "count")
            .map((stmt) => stmt.right.args.length);
        expect(callArgs).toEqual([0, 2]);

        const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
        const concrete = new Function(`${js}\nreturn [count(), count(1, 2), sample()];`)() as number[];
        expect(concrete).toEqual([0, 2, 2]);
        const ir = executor(roundTrip);
        expect([ir.call("count"), ir.call("count", 1, 2), ir.call("sample")]).toEqual(concrete);
    });
});

describe("array rest bindings", () => {
    it("copies the tail into a fresh array for empty, short, and long inputs through JSON", () => {
        const source = `
            function tail(input: number[]): number[] {
                const [first, ...rest] = input;
                return rest;
            }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const stmts = allStmts(methodByName(roundTrip, "tail"));
        const allocations = stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewArrayExpr");
        const iteratorCalls = stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceCallExpr"
            && stmt.right.method.name === "Symbol.iterator");
        const tailStores = stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.left._ === "ArrayRef"
            && stmt.left.array._ === "Local" && stmt.left.array.name !== "input");

        expect(diagnostics.messages).toEqual([]);
        expect(allocations).toHaveLength(1);
        expect(allocations[0]).toMatchObject({ right: { _: "NewArrayExpr", size: { _: "Constant", value: "0" } } });
        expect(iteratorCalls).toHaveLength(1);
        expect(tailStores).toHaveLength(1);
        expect(stmts.filter((stmt) => stmt._ === "IfStmt").length).toBeGreaterThan(2);
        expect(stmts.some((stmt) => stmt._ === "UnsupportedStmt")).toBe(false);

        const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
        const concrete = new Function(`${js}\nreturn [tail([]), tail([1]), tail([1, 2, 3])];`)() as number[][];
        expect(concrete).toEqual([[], [], [2, 3]]);
        const ir = executor(roundTrip);
        expect([ir.call("tail", []), ir.call("tail", [1]), ir.call("tail", [1, 2, 3])]).toEqual(concrete);
    });
});

describe("array spread literals", () => {
    it("copies a variable-length input in order through JSON", () => {
        const source = `
            function copy(input: number[]): number[] { return [0, ...input, 9]; }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const stmts = allStmts(methodByName(roundTrip, "copy"));
        const allocations = stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewArrayExpr");
        const lengthRead = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceFieldRef"
            && stmt.right.field.name === "length");
        const sourceRead = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef"
            && stmt.right.array._ === "Local" && stmt.right.array.name !== "input");

        expect(diagnostics.messages).toEqual([]);
        expect(allocations).toHaveLength(1);
        expect(allocations[0]).toMatchObject({ right: { _: "NewArrayExpr", size: { _: "Constant", value: "0" } } });
        expect(lengthRead).toBeUndefined();
        expect(sourceRead).toBeUndefined();
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceCallExpr"
            && stmt.right.method.name === "Symbol.iterator")).toBe(true);
        expect(stmts.some((stmt) => stmt._ === "UnsupportedValue")).toBe(false);

        const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
        const concrete = new Function(`${js}\nreturn [copy([]), copy([1]), copy([1, 2, 3])];`)() as number[][];
        expect(concrete).toEqual([[0, 9], [0, 1, 9], [0, 1, 2, 3, 9]]);
        const ir = executor(roundTrip);
        expect([ir.call("copy", []), ir.call("copy", [1]), ir.call("copy", [1, 2, 3])]).toEqual(concrete);
    });
});

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
            right: { _: "PtrCallExpr", method: { name: "test" }, receiver: { _: "Local" }, ptr: { _: "Local" } },
        });

        const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
        const concrete = new Function(`${js}\nreturn [containsX("XX"), containsX("ab")];`)() as boolean[];
        expect(concrete).toEqual([true, false]);
        const ir = executor(roundTrip);
        expect([ir.call("containsX", "XX"), ir.call("containsX", "ab")]).toEqual(concrete);
    });
});

describe("source local names", () => {
    it("keeps source parameters out of validator-reserved local prefixes", () => {
        const { file } = lower("function f(_tmp0: number) { return _tmp0 + 1; }");

        expect(validateEtsFile(file)).toEqual([]);
    });
});

describe("static context", () => {
    it("uses StaticFieldRef for `this.x` inside a closure of a static method", () => {
        const { file } = lower(`
            class C {
                static value = 1;
                static run(): void {
                    const f = () => this.value;
                    f();
                }
            }
        `);
        const closure = methodByName(file, "%AM0$run");
        const reads = allStmts(closure).filter(
            (s): s is AssignStmtDto => s._ === "AssignStmt" && s.right._ === "StaticFieldRef",
        );
        expect(reads.length).toBeGreaterThan(0);
    });
});

describe("non-finite numeric literals", () => {
    it("preserves built-in non-finite numbers as numeric constants", () => {
        const { file } = lower(`
            function values() {
                const huge: 1e999 = 1e999;
                const infinite = Infinity;
                const negativeInfinite = -Infinity;
                const notANumber = NaN;
                return [huge, infinite, negativeInfinite, notANumber];
            }
        `);
        const json = JSON.stringify(file);
        expect(json).not.toContain('"literal":null');

        const constants = allStmts(methodByName(file, "values"))
            .filter((stmt): stmt is AssignStmtDto => stmt._ === "AssignStmt" && stmt.right._ === "Constant")
            .map((stmt) => stmt.right);
        expect(constants).toEqual(expect.arrayContaining([
            { _: "Constant", value: "1e999", type: { _: "NumberType" } },
            { _: "Constant", value: "Infinity", type: { _: "NumberType" } },
            { _: "Constant", value: "-Infinity", type: { _: "NumberType" } },
            { _: "Constant", value: "NaN", type: { _: "NumberType" } },
        ]));
        expect(methodByName(file, "values").body?.locals).toContainEqual({
            name: "huge",
            type: { _: "NumberType" },
        });
    });

    it("keeps locally shadowed Infinity and NaN as locals", () => {
        const { file } = lower("function shadow(Infinity: number, NaN: number) { return Infinity + NaN; }");
        const values = allStmts(methodByName(file, "shadow"))
            .filter((stmt): stmt is AssignStmtDto => stmt._ === "AssignStmt")
            .flatMap((stmt) => stmt.right._ === "BinopExpr" ? [stmt.right.left, stmt.right.right] : [stmt.right]);

        expect(values).toEqual(expect.arrayContaining([
            { _: "Local", name: "Infinity", type: { _: "NumberType" } },
            { _: "Local", name: "NaN", type: { _: "NumberType" } },
        ]));
    });

    it("keeps project declaration-file Infinity and NaN as locals", () => {
        const { file } = lowerProject({
            "globals.d.ts": "declare var Infinity: number; declare var NaN: number;",
            "main.ts": "function shadowProjectGlobals() { return Infinity + NaN; }",
        }, "main.ts");
        const values = allStmts(methodByName(file, "shadowProjectGlobals"))
            .filter((stmt): stmt is AssignStmtDto => stmt._ === "AssignStmt")
            .flatMap((stmt) => stmt.right._ === "BinopExpr" ? [stmt.right.left, stmt.right.right] : [stmt.right]);

        expect(values).toEqual(expect.arrayContaining([
            { _: "Local", name: "Infinity", type: { _: "NumberType" } },
            { _: "Local", name: "NaN", type: { _: "NumberType" } },
        ]));
    });
});

describe("function hoisting", () => {
    it("materializes a capture only after the captured local is assigned", () => {
        const { file } = lower(`
            function outer(): number {
                const seed = 1;
                function useSeed(): number { return seed; }
                return useSeed();
            }
        `);
        const stmts = singleBlockStmts(methodByName(file, "outer"));
        const seedAssign = stmts.findIndex(
            (s) => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "seed",
        );
        const closureCreate = stmts.findIndex(
            (s): s is AssignStmtDto =>
                s._ === "AssignStmt" && s.right._ === "Local" && s.right.name.startsWith("%AM"),
        );
        // Both statements must exist — an unconditional assertion, unlike a guarded one.
        expect(seedAssign).toBeGreaterThanOrEqual(0);
        expect(closureCreate).toBeGreaterThanOrEqual(0);
        expect(seedAssign).toBeLessThan(closureCreate);
    });

    it("does not make a recursive nested function capture itself", () => {
        const { file } = lower(`
            function run(n: number): number {
                return fact(n);
                function fact(k: number): number {
                    return k <= 1 ? 1 : fact(k - 1);
                }
            }
        `);
        const closure = methodByName(file, "%AM0$run");
        expect(closure.signature.parameters.some((p) => p.type._ === "LexicalEnvType")).toBe(false);
    });
});
