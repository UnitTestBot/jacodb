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
