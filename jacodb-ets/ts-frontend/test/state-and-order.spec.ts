import { describe, expect, it } from "vitest";
import { Modifier } from "../src/dto/constants";
import { StmtDto } from "../src/dto/stmts";
import { defaultMethod, lower, methodByName } from "./util";

function flattened(method: ReturnType<typeof defaultMethod>): StmtDto[] {
    return method.body!.cfg.blocks.flatMap((block) => block.stmts);
}

describe("shared module state", () => {
    it("uses one static storage location in the default method and free functions", () => {
        const { file } = lower(`
            let state = 0;
            function increment(): number {
                state++;
                return state;
            }
        `);
        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        expect(defaultClass.fields).toContainEqual(expect.objectContaining({
            signature: expect.objectContaining({ name: "state", type: { _: "NumberType" } }),
            modifiers: expect.any(Number),
        }));
        expect(defaultClass.fields.find((field) => field.signature.name === "state")!.modifiers & Modifier.STATIC)
            .toBe(Modifier.STATIC);
        expect(flattened(defaultMethod(file))).toContainEqual(expect.objectContaining({
            left: expect.objectContaining({ _: "StaticFieldRef", field: expect.objectContaining({ name: "state" }) }),
            right: expect.objectContaining({ _: "Constant", value: "0" }),
        }));
        expect(flattened(methodByName(file, "increment"))).toContainEqual(expect.objectContaining({
            left: expect.objectContaining({ _: "StaticFieldRef", field: expect.objectContaining({ name: "state" }) }),
        }));
    });

    it("hoists nested top-level var declarations into module storage", () => {
        const { file } = lower(`
            if (true) {
                var visible = 1;
                let hidden = 2;
            }
            function read(): number { return visible; }
            function localOnly(): number { var local = 3; return local; }
        `);
        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        expect(defaultClass.fields.map((field) => field.signature.name)).toContain("visible");
        expect(defaultClass.fields.map((field) => field.signature.name)).not.toContain("hidden");
        expect(defaultClass.fields.map((field) => field.signature.name)).not.toContain("local");
        expect(flattened(methodByName(file, "read"))).toContainEqual(expect.objectContaining({
            right: expect.objectContaining({
                _: "StaticFieldRef",
                field: expect.objectContaining({ name: "visible" }),
            }),
        }));
    });
});

describe("call evaluation order", () => {
    it("evaluates an instance receiver before its arguments", () => {
        const { file } = lower(`
            class Service { run(value: number): void {} }
            function receiver(): Service { return new Service(); }
            function argument(): number { return 1; }
            receiver().run(argument());
        `);
        const calls = flattened(defaultMethod(file)).flatMap((stmt) => {
            if (stmt._ === "CallStmt") return [stmt.expr.method.name];
            if (stmt._ === "AssignStmt" && (
                stmt.right._ === "InstanceCallExpr"
                || stmt.right._ === "StaticCallExpr"
                || stmt.right._ === "PtrCallExpr"
            )) return [stmt.right.method.name];
            return [];
        });
        expect(calls.slice(-3)).toEqual(["receiver", "argument", "run"]);
    });

    it("snapshots a receiver before an argument mutates its binding", () => {
        const { file } = lower(`
            class Service { run(value: Service): void {} }
            function invoke(service: Service, replacement: Service): void {
                service.run(service = replacement);
            }
        `);
        const stmts = flattened(methodByName(file, "invoke"));
        const snapshot = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name.startsWith("%")
                && stmt.right._ === "Local"
                && stmt.right.name === "service",
        );
        const mutation = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === "service"
                && stmt.right._ === "Local"
                && stmt.right.name === "replacement",
        );
        const call = stmts.find((stmt) => stmt._ === "CallStmt" && stmt.expr.method.name === "run");
        expect(snapshot).toBeDefined();
        expect(stmts.indexOf(snapshot!)).toBeLessThan(stmts.indexOf(mutation!));
        expect(call).toMatchObject({
            expr: { instance: (snapshot as Extract<StmtDto, { _: "AssignStmt" }>).left },
        });
    });

    it("keeps the receiver for optional method calls", () => {
        const { file } = lower(`
            class Service { run?(value: number): number; }
            declare const service: Service;
            service.run?.(1);
        `);
        const stmts = flattened(defaultMethod(file));
        const methodRead = stmts.find(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceFieldRef" && stmt.right.field.name === "run",
        );
        const call = stmts.find(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceCallExpr" && stmt.right.method.name === "run",
        );
        expect(methodRead).toBeDefined();
        expect(call).toMatchObject({
            right: {
                _: "InstanceCallExpr",
                instance: (methodRead as Extract<StmtDto, { _: "AssignStmt" }>).right._ === "InstanceFieldRef"
                    ? (methodRead as Extract<StmtDto, { _: "AssignStmt" }>).right.instance
                    : undefined,
            },
        });
    });
});

describe("expression evaluation snapshots", () => {
    it("preserves an earlier call argument when a later argument reassigns it", () => {
        const { file } = lower(`
            declare function sink(first: number, second: number): void;
            function f(x: number): void {
                sink(x, x = 2);
            }
        `);
        const stmts = flattened(methodByName(file, "f"));
        const snapshot = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name.startsWith("%")
                && stmt.right._ === "Local"
                && stmt.right.name === "x",
        );
        const mutation = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === "x"
                && stmt.right._ === "Constant"
                && stmt.right.value === "2",
        );
        const call = stmts.find(
            (stmt) => stmt._ === "CallStmt" && stmt.expr._ === "StaticCallExpr" && stmt.expr.method.name === "sink",
        );
        expect(snapshot).toBeDefined();
        expect(stmts.indexOf(snapshot!)).toBeLessThan(stmts.indexOf(mutation!));
        const args = (call as Extract<StmtDto, { _: "CallStmt" }>).expr.args;
        expect(args[0]).toEqual((snapshot as Extract<StmtDto, { _: "AssignStmt" }>).left);
        expect(args[1]).toMatchObject({ _: "Local", name: "x" });
    });

    it("preserves an earlier call argument before a later call can mutate its capture", () => {
        const { file } = lower(`
            declare function sink(first: number, second: number): void;
            function f(x: number): void {
                const mutate = (): number => { x = 2; return 0; };
                sink(x, mutate());
            }
        `);
        const stmts = flattened(methodByName(file, "f"));
        const call = stmts.find(
            (stmt) => stmt._ === "CallStmt" && stmt.expr._ === "StaticCallExpr" && stmt.expr.method.name === "sink",
        ) as Extract<StmtDto, { _: "CallStmt" }>;
        const firstArgument = call.expr.args[0] as { name: string };
        const snapshot = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === firstArgument.name
                && stmt.right._ === "Local"
                && stmt.right.name === "x",
        );
        expect(firstArgument.name).toMatch(/^%/);
        expect(snapshot).toBeDefined();
    });

    it("preserves a binary left operand when the right operand reassigns it", () => {
        const { file } = lower(`
            function f(x: number): number {
                const y = x + (x = 2);
                return y;
            }
        `);
        const stmts = flattened(methodByName(file, "f"));
        const snapshot = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name.startsWith("%")
                && stmt.right._ === "Local"
                && stmt.right.name === "x",
        );
        const mutation = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === "x"
                && stmt.right._ === "Constant"
                && stmt.right.value === "2",
        );
        const addition = stmts.find(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "BinopExpr" && stmt.right.op === "+",
        );
        expect(snapshot).toBeDefined();
        expect(stmts.indexOf(snapshot!)).toBeLessThan(stmts.indexOf(mutation!));
        expect(addition).toMatchObject({
            right: { left: (snapshot as Extract<StmtDto, { _: "AssignStmt" }>).left, right: { _: "Local", name: "x" } },
        });
    });

    it("preserves a binary left operand before a right-hand call can mutate its capture", () => {
        const { file } = lower(`
            function f(x: number): number {
                const mutate = (): number => { x = 2; return 0; };
                return x + mutate();
            }
        `);
        const stmts = flattened(methodByName(file, "f"));
        const addition = stmts.find(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "BinopExpr" && stmt.right.op === "+",
        ) as Extract<StmtDto, { _: "AssignStmt" }>;
        const left = (addition.right as { left: { name: string } }).left;
        expect(left.name).toMatch(/^%/);
        expect(stmts.some(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === left.name
                && stmt.right._ === "Local"
                && stmt.right.name === "x",
        )).toBe(true);
    });

    it("preserves receiver, callee, and constructor arguments before later calls", () => {
        const { file } = lower(`
            class Pair { constructor(first: number, second: number) {} }
            class Service { run(value: number): void {} }
            function receiver(service: Service, replacement: Service): void {
                const mutate = (): number => { service = replacement; return 0; };
                service.run(mutate());
            }
            function callee(callback: (value: number) => void, replacement: (value: number) => void): void {
                const mutate = (): number => { callback = replacement; return 0; };
                callback(mutate());
            }
            function ctor(x: number): void {
                const mutate = (): number => { x = 2; return 0; };
                new Pair(x, mutate());
            }
        `);
        const receiverStmts = flattened(methodByName(file, "receiver"));
        const receiverCall = receiverStmts.find(
            (stmt) => stmt._ === "CallStmt" && stmt.expr._ === "InstanceCallExpr" && stmt.expr.method.name === "run",
        ) as Extract<StmtDto, { _: "CallStmt" }>;
        expect(receiverCall.expr.instance.name).toMatch(/^%/);
        expect(receiverStmts.some(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === receiverCall.expr.instance.name
                && stmt.right._ === "Local"
                && stmt.right.name === "service",
        )).toBe(true);

        const calleeStmts = flattened(methodByName(file, "callee"));
        const calleeCall = calleeStmts.find(
            (stmt) => stmt._ === "CallStmt" && stmt.expr._ === "PtrCallExpr",
        ) as Extract<StmtDto, { _: "CallStmt" }>;
        expect(calleeCall.expr.ptr).toMatchObject({ _: "Local", name: expect.stringMatching(/^%/) });
        expect(calleeStmts.some(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === (calleeCall.expr.ptr as { name: string }).name
                && stmt.right._ === "Local"
                && stmt.right.name === "callback",
        )).toBe(true);

        const ctorStmts = flattened(methodByName(file, "ctor"));
        const ctorCall = ctorStmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.right._ === "InstanceCallExpr"
                && stmt.right.method.name === "constructor",
        ) as Extract<StmtDto, { _: "AssignStmt" }>;
        const firstArgument = (ctorCall.right as { args: { name: string }[] }).args[0];
        expect(firstArgument.name).toMatch(/^%/);
        expect(ctorStmts.some(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === firstArgument.name
                && stmt.right._ === "Local"
                && stmt.right.name === "x",
        )).toBe(true);
    });

    it("preserves an array assignment index before the right-hand side reassigns it", () => {
        const { file } = lower(`
            function f(a: number[], i: number): void {
                a[i] = (i = 0);
            }
        `);
        const stmts = flattened(methodByName(file, "f"));
        const snapshot = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name.startsWith("%")
                && stmt.right._ === "Local"
                && stmt.right.name === "i",
        );
        const mutation = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === "i"
                && stmt.right._ === "Constant"
                && stmt.right.value === "0",
        );
        const store = stmts.find(
            (stmt) => stmt._ === "AssignStmt" && stmt.left._ === "ArrayRef",
        );
        expect(snapshot).toBeDefined();
        expect(stmts.indexOf(snapshot!)).toBeLessThan(stmts.indexOf(mutation!));
        expect(store).toMatchObject({
            left: { index: (snapshot as Extract<StmtDto, { _: "AssignStmt" }>).left },
        });
    });

    it("preserves an object assignment base before the right-hand side reassigns it", () => {
        const { file } = lower(`
            function f(obj: { x: number }, other: { x: number }): void {
                obj.x = ((obj = other), 1);
            }
        `);
        const stmts = flattened(methodByName(file, "f"));
        const snapshot = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name.startsWith("%")
                && stmt.right._ === "Local"
                && stmt.right.name === "obj",
        );
        const mutation = stmts.find(
            (stmt) => stmt._ === "AssignStmt"
                && stmt.left._ === "Local"
                && stmt.left.name === "obj"
                && stmt.right._ === "Local"
                && stmt.right.name === "other",
        );
        const store = stmts.find(
            (stmt) => stmt._ === "AssignStmt" && stmt.left._ === "InstanceFieldRef" && stmt.left.field.name === "x",
        );
        expect(snapshot).toBeDefined();
        expect(stmts.indexOf(snapshot!)).toBeLessThan(stmts.indexOf(mutation!));
        expect(store).toMatchObject({
            left: { instance: (snapshot as Extract<StmtDto, { _: "AssignStmt" }>).left },
        });
    });

    it("keeps an optional-chain continuation in the guarded branch", () => {
        const { file } = lower(`
            function f(a: { b?: { c: number } }): number | undefined {
                return a?.b.c;
            }
        `);
        const blocks = methodByName(file, "f").body!.cfg.blocks;
        const bAccessBlock = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceFieldRef" && stmt.right.field.name === "b",
        ));
        const cAccessBlock = blocks.find((block) => block.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceFieldRef" && stmt.right.field.name === "c",
        ));
        const nullishResults = blocks.flatMap((block) => block.stmts).filter(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "Constant" && stmt.right.value === "undefined",
        );
        const guards = blocks.flatMap((block) => block.stmts).filter((stmt) => stmt._ === "IfStmt");
        expect(bAccessBlock).toBeDefined();
        expect(cAccessBlock).toBeDefined();
        expect(cAccessBlock).toBe(bAccessBlock);
        expect(guards).toHaveLength(1);
        expect(nullishResults).toHaveLength(1);
    });
});
