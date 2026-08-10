import { describe, expect, it } from "vitest";
import { Modifier } from "../src/dto/constants";
import { ClassDto, EtsFileDto, MethodDto } from "../src/dto/model";
import { StmtDto } from "../src/dto/stmts";
import { InstanceCallExprDto } from "../src/dto/values";
import { lower, singleBlockStmts } from "./util";

const FILE_SIG = { projectName: "proj", fileName: "test.ts" };

function classByName(file: EtsFileDto, name: string): ClassDto {
    const clazz = file.classes.find((c) => c.signature.name === name);
    if (clazz === undefined) throw new Error(`class '${name}' not found`);
    return clazz;
}

function methodOf(clazz: ClassDto, name: string): MethodDto {
    const method = clazz.methods.find((m) => m.signature.name === name);
    if (method === undefined) {
        throw new Error(`method '${name}' not found in ${clazz.signature.name}: ${clazz.methods.map((m) => m.signature.name)}`);
    }
    return method;
}

function constructorCallOf(stmt: StmtDto): InstanceCallExprDto | undefined {
    const value = stmt._ === "CallStmt"
        ? stmt.expr
        : stmt._ === "AssignStmt"
          ? stmt.right
          : undefined;
    return value?._ === "InstanceCallExpr" && value.method.name === "constructor" ? value : undefined;
}

describe("class lowering", () => {
    const source = `
        class Point {
            x: number = 1;
            y: number = 2;
            private static counter: number = 0;
            tag?: string;

            constructor(x: number) {
                this.x = x;
            }

            dist(): number {
                return this.x + this.y;
            }

            static reset(): void {
                Point.counter = 0;
            }
        }
    `;

    it("builds the ClassDto with fields and methods", () => {
        const { file } = lower(source);
        const clazz = classByName(file, "Point");
        expect(clazz.category).toBe(0);
        expect(clazz.superClassName).toBe("");
        expect(clazz.fields.map((f) => f.signature.name)).toEqual(["x", "y", "counter", "tag"]);

        const counter = clazz.fields.find((f) => f.signature.name === "counter")!;
        expect(counter.modifiers).toBe(Modifier.PRIVATE | Modifier.STATIC);
        const tag = clazz.fields.find((f) => f.signature.name === "tag")!;
        expect(tag.questionToken).toBe(true);

        expect(clazz.methods.map((m) => m.signature.name).sort()).toEqual([
            "%instInit",
            "%statInit",
            "constructor",
            "dist",
            "reset",
        ]);
    });

    it("synthesizes %instInit with instance field initializers", () => {
        const { file } = lower(source);
        const instInit = methodOf(classByName(file, "Point"), "%instInit");
        const stmts = singleBlockStmts(instInit);
        expect(stmts[0]).toMatchObject({ _: "AssignStmt", left: { name: "this" }, right: { _: "ThisRef" } });
        expect(stmts[1]).toMatchObject({
            _: "AssignStmt",
            left: {
                _: "InstanceFieldRef",
                instance: { _: "Local", name: "this" },
                field: { name: "x", type: { _: "NumberType" } },
            },
            right: { _: "Constant", value: "1" },
        });
        expect(stmts[2]).toMatchObject({ left: { field: { name: "y" } }, right: { value: "2" } });
        expect(stmts[stmts.length - 1]).toEqual({ _: "ReturnVoidStmt" });
    });

    it("synthesizes %statInit with static field initializers", () => {
        const { file } = lower(source);
        const statInit = methodOf(classByName(file, "Point"), "%statInit");
        expect(statInit.modifiers & Modifier.STATIC).toBe(Modifier.STATIC);
        const stmts = singleBlockStmts(statInit);
        expect(stmts[1]).toMatchObject({
            _: "AssignStmt",
            left: { _: "StaticFieldRef", field: { name: "counter" } },
            right: { _: "Constant", value: "0" },
        });
    });

    it("keeps static this accesses static in initializers, arrows and calls", () => {
        const { file } = lower(`
            class C {
                static x = 1;
                static y = this.x;
                static z: number;
                static { this.z = this.y; }
                static helper(): number { return this.x; }
                static make(): () => number { return () => this.x; }
                static use(): number { return this.helper(); }
            }
        `);
        const clazz = classByName(file, "C");
        const statInit = singleBlockStmts(methodOf(clazz, "%statInit"));
        expect(statInit.some((stmt) =>
            stmt._ === "AssignStmt"
            && (stmt.left._ === "InstanceFieldRef" || stmt.right._ === "InstanceFieldRef"),
        )).toBe(false);
        expect(statInit, "static initializer must read this.x as a static field").toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: expect.objectContaining({
                _: "StaticFieldRef",
                field: expect.objectContaining({ name: "x" }),
            }),
        }));

        const arrow = singleBlockStmts(methodOf(clazz, "%AM0$make"));
        expect(arrow, "static arrow must retain lexical static this").toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: expect.objectContaining({
                _: "StaticFieldRef",
                field: expect.objectContaining({ name: "x" }),
            }),
        }));

        const use = singleBlockStmts(methodOf(clazz, "use"));
        expect(use, "this.helper() in a static method must be a static call").toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: expect.objectContaining({
                _: "StaticCallExpr",
                method: expect.objectContaining({
                    name: "helper",
                    declaringClass: expect.objectContaining({ name: "C" }),
                }),
            }),
        }));
    });

    it("shapes the explicit constructor: prologue, %instInit call, body, return this", () => {
        const { file } = lower(source);
        const ctor = methodOf(classByName(file, "Point"), "constructor");
        expect(ctor.signature.returnType).toMatchObject({ _: "ClassType", signature: { name: "Point" } });
        const stmts = singleBlockStmts(ctor);
        expect(stmts[0]).toMatchObject({ left: { name: "x" }, right: { _: "ParameterRef", index: 0 } });
        expect(stmts[1]).toMatchObject({ left: { name: "this" }, right: { _: "ThisRef" } });
        expect(stmts[2]).toMatchObject({
            _: "CallStmt",
            expr: { _: "InstanceCallExpr", instance: { name: "this" }, method: { name: "%instInit" } },
        });
        expect(stmts[3]).toMatchObject({
            _: "AssignStmt",
            left: { _: "InstanceFieldRef", field: { name: "x" } },
            right: { _: "Local", name: "x" },
        });
        expect(stmts[stmts.length - 1]).toMatchObject({ _: "ReturnStmt", arg: { _: "Local", name: "this" } });
    });

    it("synthesizes a default constructor when absent", () => {
        const { file } = lower("class Empty {}");
        const ctor = methodOf(classByName(file, "Empty"), "constructor");
        const stmts = singleBlockStmts(ctor);
        expect(stmts).toHaveLength(3);
        expect(stmts[0]).toMatchObject({ right: { _: "ThisRef" } });
        expect(stmts[1]).toMatchObject({ _: "CallStmt", expr: { method: { name: "%instInit" } } });
        expect(stmts[2]).toMatchObject({ _: "ReturnStmt", arg: { name: "this" } });
    });

    it("handles inheritance and implements clauses", () => {
        const { file } = lower(`
            interface Shaped { area(): number; }
            class Base {}
            class Derived extends Base implements Shaped {
                area(): number { return 0; }
            }
        `);
        const derived = classByName(file, "Derived");
        expect(derived.superClassName).toBe("Base");
        expect(derived.implementedInterfaceNames).toEqual(["Shaped"]);
    });

    it("initializes derived instances only after the explicit super call", () => {
        const { file } = lower(`
            class Base { constructor(value: number) {} }
            class Derived extends Base {
                field = 1;
                constructor(value: number) {
                    const doubled = value * 2;
                    super(doubled);
                    this.field = doubled;
                }
            }
        `);
        const stmts = singleBlockStmts(methodOf(classByName(file, "Derived"), "constructor"));
        const superIndex = stmts.findIndex(
            (stmt) => constructorCallOf(stmt)?.method.declaringClass.name === "Base",
        );
        const initIndex = stmts.findIndex(
            (stmt) => stmt._ === "CallStmt" && stmt.expr.method.name === "%instInit",
        );
        const doubledIndex = stmts.findIndex(
            (stmt) => stmt._ === "AssignStmt" && stmt.left._ === "Local" && stmt.left.name === "doubled",
        );
        expect(doubledIndex).toBeLessThan(superIndex);
        expect(superIndex).toBeLessThan(initIndex);
        expect(constructorCallOf(stmts[superIndex])!.method.parameters).toMatchObject([
            { name: "value", type: { _: "NumberType" } },
        ]);
    });

    it("initializes derived fields after super on every conditional branch", () => {
        const { file } = lower(`
            class Derived extends Base {
                value = 42;
                constructor(flag: boolean) { if (flag) super(); else super(); }
            }
        `);
        const ctor = methodOf(classByName(file, "Derived"), "constructor");
        const superBlocks = ctor.body!.cfg.blocks.filter((block) => block.stmts.some((stmt) =>
            constructorCallOf(stmt) !== undefined,
        ));

        expect(superBlocks).toHaveLength(2);
        expect(superBlocks.every((block) => block.stmts.some((stmt) =>
            stmt._ === "CallStmt" && stmt.expr.method.name === "%instInit",
        ))).toBe(true);
    });

    it("initializes derived fields when returning a direct super call", () => {
        const { file } = lower(`
            class Base {}
            class Derived extends Base {
                value = 42;
                constructor() { return super(); }
            }
        `);
        const stmts = singleBlockStmts(methodOf(classByName(file, "Derived"), "constructor"));
        const superIndex = stmts.findIndex((stmt) =>
            stmt._ === "AssignStmt"
            && stmt.right._ === "InstanceCallExpr"
            && stmt.right.method.name === "constructor",
        );
        const initIndex = stmts.findIndex((stmt) =>
            stmt._ === "CallStmt" && stmt.expr.method.name === "%instInit",
        );
        const returnIndex = stmts.findIndex((stmt) => stmt._ === "ReturnStmt");

        expect(superIndex).toBeGreaterThanOrEqual(0);
        expect(superIndex).toBeLessThan(initIndex);
        expect(initIndex).toBeLessThan(returnIndex);
    });

    it("synthesizes a derived constructor that forwards base parameters before initialization", () => {
        const { file } = lower(`
            class Base { constructor(value: number, label?: string) {} }
            class Derived extends Base { field = 1; }
        `);
        const ctor = methodOf(classByName(file, "Derived"), "constructor");
        expect(ctor.signature.parameters).toMatchObject([
            { name: "value", type: { _: "NumberType" } },
            { name: "label", type: { _: "StringType" }, isOptional: true },
        ]);
        const stmts = singleBlockStmts(ctor);
        const superIndex = stmts.findIndex(
            (stmt) => constructorCallOf(stmt)?.method.declaringClass.name === "Base",
        );
        const initIndex = stmts.findIndex(
            (stmt) => stmt._ === "CallStmt" && stmt.expr.method.name === "%instInit",
        );
        expect(superIndex).toBeGreaterThanOrEqual(0);
        expect(superIndex).toBeLessThan(initIndex);
        expect(constructorCallOf(stmts[superIndex])).toMatchObject({
            args: [{ name: "value" }, { name: "label" }],
        });
    });

    it("places derived parameter properties after super and before instance fields", () => {
        const { file } = lower(`
            class Base { constructor() {} }
            class Derived extends Base {
                field = 1;
                constructor(public value: number) { super(); }
            }
        `);
        const stmts = singleBlockStmts(methodOf(classByName(file, "Derived"), "constructor"));
        const superIndex = stmts.findIndex((stmt) => constructorCallOf(stmt) !== undefined);
        const initIndex = stmts.findIndex((stmt) => stmt._ === "CallStmt" && stmt.expr.method.name === "%instInit");
        const propertyIndex = stmts.findIndex(
            (stmt) => stmt._ === "AssignStmt" && stmt.left._ === "InstanceFieldRef" && stmt.left.field.name === "value",
        );
        expect(superIndex).toBeLessThan(propertyIndex);
        expect(propertyIndex).toBeLessThan(initIndex);
    });

    it("preserves source order between static fields and static blocks", () => {
        const { file } = lower(`
            function mark(value: number): number { return value; }
            class Ordered {
                static first = mark(1);
                static { mark(2); }
                static third = mark(3);
            }
        `);
        const stmts = singleBlockStmts(methodOf(classByName(file, "Ordered"), "%statInit"));
        const marks = stmts.flatMap((stmt) => {
            const call = stmt._ === "CallStmt"
                ? stmt.expr
                : stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr"
                  ? stmt.right
                  : undefined;
            return call?.method.name === "mark" ? [call.args[0]] : [];
        });
        expect(marks).toMatchObject([
            { _: "Constant", value: "1" },
            { _: "Constant", value: "2" },
            { _: "Constant", value: "3" },
        ]);
    });

    it("lowers parameter properties into fields and constructor assignments", () => {
        const { file } = lower(`
            class Vec {
                constructor(private readonly x: number, public y: number) {}
            }
        `);
        const vec = classByName(file, "Vec");
        expect(vec.fields.map((f) => f.signature.name)).toEqual(["x", "y"]);
        const x = vec.fields[0];
        expect(x.modifiers & Modifier.PRIVATE).toBe(Modifier.PRIVATE);
        expect(x.modifiers & Modifier.READONLY).toBe(Modifier.READONLY);

        const ctor = methodOf(vec, "constructor");
        const stmts = singleBlockStmts(ctor);
        const fieldAssigns = stmts.filter(
            (s) => s._ === "AssignStmt" && s.left._ === "InstanceFieldRef",
        );
        expect(fieldAssigns).toHaveLength(2);
    });

    it("marks abstract classes and keeps abstract methods bodyless", () => {
        const { file } = lower(`
            abstract class A {
                abstract run(): void;
                helper(): number { return 1; }
            }
        `);
        const a = classByName(file, "A");
        expect(a.modifiers & Modifier.ABSTRACT).toBe(Modifier.ABSTRACT);
        const run = methodOf(a, "run");
        expect(run.body).toBeUndefined();
        expect(run.modifiers & Modifier.ABSTRACT).toBe(Modifier.ABSTRACT);
        expect(methodOf(a, "helper").body).toBeDefined();
    });
});

describe("interface lowering", () => {
    it("builds category-2 classes with bodyless methods", () => {
        const { file } = lower(`
            export interface Checker {
                limit?: number;
                check(value: string): boolean;
            }
        `);
        const checker = classByName(file, "Checker");
        expect(checker.category).toBe(2);
        expect(checker.modifiers & Modifier.EXPORT).toBe(Modifier.EXPORT);

        const limit = checker.fields.find((f) => f.signature.name === "limit")!;
        expect(limit.questionToken).toBe(true);

        const check = methodOf(checker, "check");
        expect(check.body).toBeUndefined();
        expect(check.signature.parameters).toEqual([{ name: "value", type: { _: "StringType" } }]);
        expect(check.signature.returnType).toEqual({ _: "BooleanType" });
    });

    it("represents index signatures as typed fields", () => {
        const { file, diagnostics } = lower(`
            interface Graph {
                [key: string]: string[];
            }
        `);
        expect(classByName(file, "Graph").fields).toMatchObject([
            {
                signature: {
                    name: "[key: string]",
                    type: { _: "ArrayType", elementType: { _: "StringType" }, dimensions: 1 },
                },
                questionToken: false,
                exclamationToken: false,
            },
        ]);
        expect(diagnostics.messages).toEqual([]);
    });
});

describe("enum lowering", () => {
    it("builds category-3 classes with static EnumValueType fields and %statInit values", () => {
        const { file } = lower(`
            enum Color { Red, Green = 10, Blue }
        `);
        const color = classByName(file, "Color");
        expect(color.category).toBe(3);
        expect(color.fields).toHaveLength(3);
        expect(color.fields[0]).toMatchObject({
            signature: {
                name: "Red",
                type: { _: "EnumValueType", signature: { name: "Color" }, name: "Red" },
            },
            modifiers: Modifier.STATIC,
        });

        const statInit = methodOf(color, "%statInit");
        const stmts = singleBlockStmts(statInit);
        const values = stmts
            .filter((s) => s._ === "AssignStmt" && s.left._ === "StaticFieldRef")
            .map((s) => (s as { right: { value: string } }).right.value);
        expect(values).toEqual(["0", "10", "11"]);
    });

    it("supports string enums", () => {
        const { file } = lower(`enum Dir { Up = "UP", Down = "DOWN" }`);
        const statInit = methodOf(classByName(file, "Dir"), "%statInit");
        const stmts = singleBlockStmts(statInit);
        const assigns = stmts.filter((s) => s._ === "AssignStmt" && s.left._ === "StaticFieldRef");
        expect(assigns[0]).toMatchObject({ right: { _: "Constant", value: "UP", type: { _: "StringType" } } });
    });
});

describe("namespace lowering", () => {
    it("builds NamespaceDto with its own %dflt class and declared classes", () => {
        const { file } = lower(`
            namespace Outer {
                export class Inner {}
                export function util(): number { return 1; }
                let counter = 0;
                namespace Nested {
                    export enum E { A }
                }
            }
        `);
        expect(file.namespaces).toHaveLength(1);
        const outer = file.namespaces[0];
        expect(outer.signature).toEqual({ name: "Outer", declaringFile: FILE_SIG });

        const classNames = outer.classes!.map((c) => c.signature.name);
        expect(classNames).toContain("%dflt");
        expect(classNames).toContain("Inner");

        const inner = outer.classes!.find((c) => c.signature.name === "Inner")!;
        expect(inner.signature.declaringNamespace).toEqual({ name: "Outer", declaringFile: FILE_SIG });

        const dflt = outer.classes!.find((c) => c.signature.name === "%dflt")!;
        expect(dflt.methods.map((m) => m.signature.name)).toContain("util");

        const nested = outer.namespaces![0];
        expect(nested.signature).toEqual({
            name: "Nested",
            declaringFile: FILE_SIG,
            declaringNamespace: { name: "Outer", declaringFile: FILE_SIG },
        });
        expect(nested.classes!.map((c) => c.signature.name)).toContain("E");
    });
});
