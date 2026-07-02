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

    it("degrades unsupported expressions to raw fallback values with diagnostics", () => {
        const { file, diagnostics } = lower("let p = { x: 1 };");
        const stmts = singleBlockStmts(defaultMethod(file));
        const assign = stmts.find((s): s is AssignStmtDto => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "p");
        expect(assign!.right).toMatchObject({ _: "UnsupportedValue" });
        expect((assign!.right as unknown as { type: unknown }).type).toBeDefined();
        expect(diagnostics.messages.length).toBeGreaterThan(0);
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
