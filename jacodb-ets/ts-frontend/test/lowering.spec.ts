import { describe, expect, it } from "vitest";
import { StmtDto, AssignStmtDto, CallStmtDto } from "../src/dto/stmts";
import { defaultMethod, lower, methodByName, singleBlockStmts } from "./util";

/** Statements of the %dflt method between the prologue (`this := ThisRef`) and the final return. */
function bodyStmts(source: string): StmtDto[] {
    const { file } = lower(source);
    const stmts = singleBlockStmts(defaultMethod(file));
    // prologue of %dflt: [this := ThisRef]; epilogue: ReturnVoidStmt
    return stmts.slice(1, -1);
}

function assigns(stmts: StmtDto[]): AssignStmtDto[] {
    return stmts.filter((s): s is AssignStmtDto => s._ === "AssignStmt");
}

describe("straight-line lowering", () => {
    it("lowers literal initializers to constants", () => {
        const stmts = bodyStmts(`let x = 42; let s = "hi"; let b = true; let n = null; let u = undefined;`);
        expect(stmts).toEqual([
            asgn("x", { _: "Constant", value: "42", type: { _: "NumberType" } }),
            asgn("s", { _: "Constant", value: "hi", type: { _: "StringType" } }),
            asgn("b", { _: "Constant", value: "true", type: { _: "BooleanType" } }),
            asgn("n", { _: "Constant", value: "null", type: { _: "NullType" } }),
            asgn("u", { _: "Constant", value: "undefined", type: { _: "UndefinedType" } }),
        ]);

        function asgn(name: string, right: unknown) {
            return { _: "AssignStmt", left: expect.objectContaining({ _: "Local", name }), right };
        }
    });

    it("lowers binary expressions with immediate operands", () => {
        const stmts = bodyStmts("let a = 1; let b = 2; let c = a + b;");
        const c = assigns(stmts)[2];
        expect(c.right).toEqual({
            _: "BinopExpr",
            op: "+",
            left: expect.objectContaining({ _: "Local", name: "a" }),
            right: expect.objectContaining({ _: "Local", name: "b" }),
            type: { _: "NumberType" },
        });
    });

    it("hoists nested expressions into %N temps", () => {
        const stmts = bodyStmts("let a = 1; let b = 2; let d = (a + b) * 2;");
        const all = assigns(stmts);
        // a, b, %0 := a + b, d := %0 * 2
        expect(all).toHaveLength(4);
        expect(all[2].left).toMatchObject({ _: "Local", name: "%0" });
        expect(all[2].right).toMatchObject({ _: "BinopExpr", op: "+" });
        expect(all[3].left).toMatchObject({ _: "Local", name: "d" });
        expect(all[3].right).toMatchObject({
            _: "BinopExpr",
            op: "*",
            left: { _: "Local", name: "%0" },
            right: { _: "Constant", value: "2" },
        });
    });

    it("lowers comparisons in value position to ConditionExpr", () => {
        const stmts = bodyStmts("let a = 1; let e = a < 2;");
        expect(assigns(stmts)[1].right).toMatchObject({
            _: "ConditionExpr",
            op: "<",
            type: { _: "BooleanType" },
        });
    });

    it("lowers method calls on values to InstanceCallExpr with a Local instance", () => {
        const stmts = bodyStmts(`console.log("hello");`);
        const call = stmts.find((s): s is CallStmtDto => s._ === "CallStmt");
        expect(call).toBeDefined();
        expect(call!.expr).toMatchObject({
            _: "InstanceCallExpr",
            instance: { _: "Local", name: "console" },
            method: { name: "log" },
            args: [{ _: "Constant", value: "hello", type: { _: "StringType" } }],
        });
    });

    it("lowers free project function calls to StaticCallExpr on %dflt", () => {
        const source = `
            function add(a: number, b: number): number { return a + b; }
            let s = add(1, 2);
        `;
        const { file } = lower(source);
        const stmts = singleBlockStmts(defaultMethod(file));
        const sAssign = stmts.find(
            (s): s is AssignStmtDto => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "s",
        );
        expect(sAssign!.right).toMatchObject({
            _: "StaticCallExpr",
            method: {
                declaringClass: { name: "%dflt", declaringFile: { projectName: "proj", fileName: "test.ts" } },
                name: "add",
                parameters: [
                    { name: "a", type: { _: "NumberType" } },
                    { name: "b", type: { _: "NumberType" } },
                ],
                returnType: { _: "NumberType" },
            },
            args: [
                { _: "Constant", value: "1" },
                { _: "Constant", value: "2" },
            ],
        });
    });

    it("lowers function declarations to %dflt methods with prologue and immediate return", () => {
        const source = "function add(a: number, b: number): number { return a + b; }";
        const { file } = lower(source);
        const method = methodByName(file, "add");
        const stmts = singleBlockStmts(method);
        expect(stmts[0]).toMatchObject({
            _: "AssignStmt",
            left: { _: "Local", name: "a" },
            right: { _: "ParameterRef", index: 0, type: { _: "NumberType" } },
        });
        expect(stmts[1]).toMatchObject({
            _: "AssignStmt",
            left: { _: "Local", name: "b" },
            right: { _: "ParameterRef", index: 1 },
        });
        expect(stmts[2]).toMatchObject({ _: "AssignStmt", left: { _: "Local", name: "this" }, right: { _: "ThisRef" } });
        // return arg is an immediate: %0 := a + b; return %0
        expect(stmts[3]).toMatchObject({ _: "AssignStmt", left: { _: "Local", name: "%0" } });
        expect(stmts[4]).toEqual({ _: "ReturnStmt", arg: expect.objectContaining({ _: "Local", name: "%0" }) });
    });

    it("lowers `new` into NewExpr + constructor call", () => {
        const stmts = bodyStmts("class C {}\nlet o = new C();");
        const all = assigns(stmts);
        const classSig = { name: "C", declaringFile: { projectName: "proj", fileName: "test.ts" } };
        expect(all[0]).toMatchObject({
            left: { _: "Local", name: "%0" },
            right: { _: "NewExpr", classType: { _: "ClassType", signature: classSig } },
        });
        expect(all[1]).toMatchObject({
            left: { _: "Local", name: "%0" },
            right: {
                _: "InstanceCallExpr",
                instance: { _: "Local", name: "%0" },
                method: { declaringClass: classSig, name: "constructor" },
                args: [],
            },
        });
        expect(all[2]).toMatchObject({ left: { _: "Local", name: "o" }, right: { _: "Local", name: "%0" } });
    });

    it("lowers ambient `new` with the %unk file signature", () => {
        const stmts = bodyStmts("let d = new Date();");
        expect(assigns(stmts)[0].right).toMatchObject({
            _: "NewExpr",
            classType: {
                _: "ClassType",
                signature: { name: "Date", declaringFile: { projectName: "%unk", fileName: "%unk" } },
            },
        });
    });

    it("lowers array literals to NewArrayExpr plus indexed stores", () => {
        const stmts = bodyStmts("let arr = [10, 20];");
        const all = assigns(stmts);
        expect(all[0].right).toEqual({
            _: "NewArrayExpr",
            elementType: { _: "NumberType" },
            size: { _: "Constant", value: "2", type: { _: "NumberType" } },
        });
        expect(all[1]).toMatchObject({
            left: { _: "ArrayRef", index: { _: "Constant", value: "0" } },
            right: { _: "Constant", value: "10" },
        });
        expect(all[2]).toMatchObject({
            left: { _: "ArrayRef", index: { _: "Constant", value: "1" } },
            right: { _: "Constant", value: "20" },
        });
        expect(all[3]).toMatchObject({ left: { _: "Local", name: "arr" } });
    });

    it("uses the contextual element type for an empty array literal", () => {
        const all = assigns(bodyStmts("const values: number[] = [];"));
        expect(all[0]).toMatchObject({
            left: { _: "Local", type: { _: "ArrayType", elementType: { _: "NumberType" } } },
            right: { _: "NewArrayExpr", elementType: { _: "NumberType" } },
        });
    });

    it("preserves the element type of a numeric Array constructor", () => {
        const all = assigns(bodyStmts("let n = 3; let flags = new Array<boolean>(n);"));
        const allocation = all.find((stmt) => stmt.right._ === "NewArrayExpr");
        expect(allocation).toMatchObject({
            left: { _: "Local", type: { _: "ArrayType", elementType: { _: "BooleanType" } } },
            right: {
                _: "NewArrayExpr",
                elementType: { _: "BooleanType" },
                size: { _: "Local", name: "n", type: { _: "NumberType" } },
            },
        });
        expect(all.some((stmt) => stmt.right._ === "InstanceCallExpr")).toBe(false);
    });

    it("keeps the one-element Array overload as a constructor call", () => {
        const all = assigns(bodyStmts('let values = new Array<string>("x");'));
        expect(all.some((stmt) => stmt.right._ === "NewArrayExpr")).toBe(false);
        expect(all.some((stmt) => stmt.right._ === "InstanceCallExpr")).toBe(true);
    });

    it("lowers element reads and writes through ArrayRef", () => {
        const stmts = bodyStmts("let arr = [1]; let v = arr[0]; arr[0] = 5;");
        const all = assigns(stmts);
        const read = all.find((a) => a.left._ === "Local" && a.left.name === "v")!;
        expect(read.right).toMatchObject({ _: "ArrayRef", array: { _: "Local", name: "arr" } });
        const write = all[all.length - 1];
        expect(write).toMatchObject({
            left: { _: "ArrayRef", array: { _: "Local", name: "arr" } },
            right: { _: "Constant", value: "5" },
        });
    });

    it("lowers instance field reads/writes through InstanceFieldRef", () => {
        const source = `
            class A { f: number = 0; }
            let a = new A();
            let v = a.f;
            a.f = 5;
        `;
        const stmts = bodyStmts(source);
        const all = assigns(stmts);
        const classSig = { name: "A", declaringFile: { projectName: "proj", fileName: "test.ts" } };
        const read = all.find((s) => s.left._ === "Local" && s.left.name === "v")!;
        expect(read.right).toEqual({
            _: "InstanceFieldRef",
            instance: expect.objectContaining({ _: "Local", name: "a" }),
            field: { declaringClass: classSig, name: "f", type: { _: "NumberType" } },
        });
        const write = all[all.length - 1];
        expect(write.left).toMatchObject({ _: "InstanceFieldRef", field: { name: "f" } });
        expect(write.right).toEqual({ _: "Constant", value: "5", type: { _: "NumberType" } });
    });

    it("lowers static field access (enum members) through StaticFieldRef", () => {
        const stmts = bodyStmts("enum E { A, B }\nlet v = E.A;");
        const read = assigns(stmts)[0];
        expect(read.right).toEqual({
            _: "StaticFieldRef",
            field: {
                declaringClass: { name: "E", declaringFile: { projectName: "proj", fileName: "test.ts" } },
                name: "A",
                type: {
                    _: "EnumValueType",
                    signature: { name: "E", declaringFile: { projectName: "proj", fileName: "test.ts" } },
                    name: "A",
                },
            },
        });
    });

    it("desugars compound assignment into load-op-store", () => {
        const stmts = bodyStmts("let x = 1; x += 2;");
        const compound = assigns(stmts)[1];
        expect(compound).toMatchObject({
            left: { _: "Local", name: "x" },
            right: {
                _: "BinopExpr",
                op: "+",
                left: { _: "Local", name: "x" },
                right: { _: "Constant", value: "2" },
            },
        });
    });

    it("lowers increments with the ++ unop", () => {
        const stmts = bodyStmts("let x = 1; x++;");
        const inc = assigns(stmts).find((a) => a.right._ === "UnopExpr")!;
        expect(inc).toMatchObject({
            left: { _: "Local", name: "x" },
            right: { _: "UnopExpr", op: "++", arg: { _: "Local", name: "x" } },
        });
    });

    it("returns the OLD value for postfix and the NEW value for prefix inc/dec on fields", () => {
        const source = `
            class C { f: number = 0; }
            let c = new C();
            let post = c.f++;
            let pre = ++c.f;
        `;
        const stmts = bodyStmts(source);
        const all = assigns(stmts);

        // postfix: %old := c.f; %new := %old ++; c.f := %new; post := %old
        const postAssign = all.find((a) => a.left._ === "Local" && a.left.name === "post")!;
        const postSource = (postAssign.right as { name: string }).name;
        const oldLoad = all.find((a) => a.left._ === "Local" && a.left.name === postSource)!;
        expect(oldLoad.right._).toBe("InstanceFieldRef"); // holds the OLD value

        // prefix: %old := c.f; %new := %old ++; c.f := %new; pre := %new
        const preAssign = all.find((a) => a.left._ === "Local" && a.left.name === "pre")!;
        const preSource = (preAssign.right as { name: string }).name;
        const newCompute = all.find((a) => a.left._ === "Local" && a.left.name === preSource)!;
        expect(newCompute.right._).toBe("UnopExpr"); // holds the UPDATED value
    });

    it("ignores TS fake `this` parameters", () => {
        const { file } = lower(`
            function f(this: Window, x: number): number {
                return x;
            }
        `);
        const method = methodByName(file, "f");
        expect(method.signature.parameters).toEqual([{ name: "x", type: { _: "NumberType" } }]);
        const stmts = singleBlockStmts(method);
        // x binds to ParameterRef(0) — the fake `this` must not shift indices
        expect(stmts[0]).toMatchObject({
            left: { _: "Local", name: "x" },
            right: { _: "ParameterRef", index: 0 },
        });
        // and the `this` local still comes from ThisRef, not a ParameterRef
        expect(stmts[1]).toMatchObject({ left: { name: "this" }, right: { _: "ThisRef" } });
    });

    it("lifts nested function declarations onto the %dflt class", () => {
        const source = `
            function outer(n: number): number {
                function inner(k: number): number {
                    return k + 1;
                }
                return inner(n);
            }
        `;
        const { file } = lower(source);
        // inner must not be lost: it becomes a %dflt method with its real name
        const inner = methodByName(file, "inner");
        expect(inner.body).toBeDefined();
        expect(inner.signature.parameters).toEqual([{ name: "k", type: { _: "NumberType" } }]);
        // and the call site resolves to it as a static call on %dflt
        const outer = methodByName(file, "outer");
        const call = outer
            .body!.cfg.blocks.flatMap((b) => b.stmts)
            .find((s) => s._ === "AssignStmt" && s.right._ === "StaticCallExpr");
        expect(call).toMatchObject({
            right: { method: { declaringClass: { name: "%dflt" }, name: "inner" } },
        });
    });

    it("lowers template literals into string concatenation chains", () => {
        const stmts = bodyStmts("let n = 1; let s = `a${n}b`;");
        const all = assigns(stmts);
        // n, %0 := "a" + n, %1 := %0 + "b", s := %1
        expect(all[1].right).toMatchObject({
            _: "BinopExpr",
            op: "+",
            left: { _: "Constant", value: "a" },
            right: { _: "Local", name: "n" },
        });
        expect(all[2].right).toMatchObject({
            _: "BinopExpr",
            op: "+",
            left: { _: "Local", name: "%0" },
            right: { _: "Constant", value: "b" },
        });
        expect(all[3]).toMatchObject({ left: { _: "Local", name: "s" } });
    });

    it("lowers throw statements", () => {
        const { file } = lower(`throw new Error("boom");`);
        const stmts = singleBlockStmts(defaultMethod(file));
        const last = stmts[stmts.length - 1];
        expect(last).toMatchObject({ _: "ThrowStmt", arg: { _: "Local" } });
    });

    it("degrades unsupported expressions to raw fallback values hoisted into temps", () => {
        const { file, diagnostics } = lower("let p = /abc/g;");
        const stmts = singleBlockStmts(defaultMethod(file));
        // raw values are only legal as the RHS of a Local assignment: %0 := <raw>; p := %0
        const rawAssign = stmts.find(
            (s): s is AssignStmtDto => s._ === "AssignStmt" && (s.right as { _: string })._ === "UnsupportedValue",
        );
        expect(rawAssign).toBeDefined();
        expect(rawAssign!.left).toMatchObject({ _: "Local", name: "%0" });
        expect((rawAssign!.right as unknown as { type: unknown }).type).toBeDefined();
        const pAssign = stmts.find(
            (s): s is AssignStmtDto => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "p",
        );
        expect(pAssign!.right).toMatchObject({ _: "Local", name: "%0" });
        expect(diagnostics.messages.length).toBeGreaterThan(0);
    });

    it("hoists raw fallbacks out of ref stores and call arguments", () => {
        const { file } = lower(`
            let parts = [1, ...[2, 3]];
            console.log(...parts);
        `);
        const stmts = singleBlockStmts(defaultMethod(file));
        for (const s of stmts) {
            if (s._ === "AssignStmt" && s.left._ !== "Local") {
                expect((s.right as { _: string })._).not.toMatch(/^Unsupported/);
            }
            if (s._ === "CallStmt") {
                for (const arg of s.expr.args) {
                    expect((arg as { _: string })._).not.toMatch(/^Unsupported/);
                }
            }
        }
    });

    it("lowers typeof/await/cast expressions", () => {
        const stmts = bodyStmts(`
            let x = 1;
            let t = typeof x;
            let c = x as unknown;
        `);
        const all = assigns(stmts);
        expect(all[1].right).toMatchObject({ _: "TypeOfExpr", arg: { _: "Local", name: "x" } });
        expect(all[2].right).toMatchObject({ _: "CastExpr", arg: { _: "Local", name: "x" }, type: { _: "UnknownType" } });
    });

    it("lowers instanceof", () => {
        const stmts = bodyStmts("class C {}\nlet o = new C();\nlet b = o instanceof C;");
        const check = assigns(stmts).find((a) => a.right._ === "InstanceOfExpr")!;
        expect(check.right).toMatchObject({
            _: "InstanceOfExpr",
            arg: { _: "Local", name: "o" },
            checkType: { _: "ClassType", signature: { name: "C" } },
        });
    });
});
