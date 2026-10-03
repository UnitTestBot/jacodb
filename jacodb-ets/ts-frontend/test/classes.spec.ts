import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { Modifier } from "../src/dto/constants";
import { ClassDto, EtsFileDto, MethodDto } from "../src/dto/model";
import { StmtDto } from "../src/dto/stmts";
import { InstanceCallExprDto } from "../src/dto/values";
import { compile, lower, singleBlockStmts } from "./util";

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
    it("preserves a declared class constructor across reads, assignments, and returns", () => {
        const source = `
            class A { static marker = 7; }
            export function constructorValue(): typeof A { return A; }
            export function copy(): typeof A { const saved = A; return saved; }
            export function create(): A { return new A(); }
            export function createParenthesized(): A { return new (A)(); }
            export function createWrapped(): A { return new ((A as typeof A)!)(); }
            export function direct(value: object): boolean { return value instanceof A; }
            export function marker(): number { return A.marker; }
            export default A;
        `;
        const { file, diagnostics } = lower(source);
        const defaultClass = classByName(file, "%dflt");
        const classSignature = classByName(file, "A").signature;
        const read = singleBlockStmts(methodOf(defaultClass, "constructorValue"));
        const copy = singleBlockStmts(methodOf(defaultClass, "copy"));
        const create = singleBlockStmts(methodOf(defaultClass, "create"));
        const createParenthesized = singleBlockStmts(methodOf(defaultClass, "createParenthesized"));
        const createWrapped = singleBlockStmts(methodOf(defaultClass, "createWrapped"));
        const direct = singleBlockStmts(methodOf(defaultClass, "direct"));
        const marker = singleBlockStmts(methodOf(defaultClass, "marker"));

        expect(methodOf(defaultClass, "constructorValue").signature.returnType).toEqual({
            _: "ClassValueType", signature: classSignature,
        });
        expect(read).toContainEqual(expect.objectContaining({
            _: "ReturnStmt", arg: expect.objectContaining({ _: "ClassValueRef", signature: classSignature }),
        }));
        expect(copy).toContainEqual(expect.objectContaining({
            _: "AssignStmt", right: expect.objectContaining({ _: "ClassValueRef", signature: classSignature }),
        }));
        expect(copy).toContainEqual(expect.objectContaining({ _: "ReturnStmt", arg: expect.objectContaining({ name: "saved" }) }));
        expect(create.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr")).toBe(true);
        for (const stmts of [createParenthesized, createWrapped]) {
            expect(stmts).toContainEqual(expect.objectContaining({
                _: "AssignStmt",
                right: { _: "NewExpr", classType: { _: "ClassType", signature: classSignature } },
            }));
        }
        expect(direct.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceOfExpr")).toBe(true);
        expect(marker.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticFieldRef")).toBe(true);
        expect(defaultClass.fields).toContainEqual(expect.objectContaining({
            signature: expect.objectContaining({ name: "default", type: { _: "ClassValueType", signature: classSignature } }),
        }));
        expect(methodOf(defaultClass, "%dflt").body!.cfg.blocks.flatMap((block) => block.stmts)).toContainEqual(
            expect.objectContaining({
                _: "AssignStmt",
                left: expect.objectContaining({ _: "StaticFieldRef", field: expect.objectContaining({ name: "default" }) }),
                right: expect.objectContaining({ _: "ClassValueRef", signature: classSignature }),
            }),
        );
        expect(file.exportInfos).toContainEqual(expect.objectContaining({ exportName: "default", nameBeforeAs: "A" }));
        expect(diagnostics.messages).toEqual([]);

        const js = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const concrete = new Function("exports", `${js}\nreturn {
            same: constructorValue() === A && copy() === A,
            instance: new (constructorValue())() instanceof A,
            parenthesized: createParenthesized() instanceof A,
            wrapped: createWrapped() instanceof A,
            marker: marker(),
        };`)({}) as { same: boolean; instance: boolean; parenthesized: boolean; wrapped: boolean; marker: number };
        expect(concrete).toEqual({ same: true, instance: true, parenthesized: true, wrapped: true, marker: 7 });
    });

    it("preserves a constructor local or call result in dynamic new", () => {
        const source = `
            class A {}
            let reads = 0;
            function constructorValue(): typeof A { reads++; return A; }
            function constructorBySignature(): new () => A { reads++; return A; }
            export function create(): A { return new (constructorValue())(); }
            export function createAlias(): A {
                const ctor = constructorValue();
                return new ctor();
            }
            export function createBySignature(): A { return new (constructorBySignature())(); }
            export function createSignatureAlias(): A {
                const ctor: new () => A = constructorBySignature();
                return new ctor();
            }
            export function readCount(): number { return reads; }
        `;
        const { file, diagnostics } = lower(source);
        const defaultClass = classByName(file, "%dflt");
        const stmts = singleBlockStmts(methodOf(defaultClass, "create"));
        const aliasStmts = singleBlockStmts(methodOf(defaultClass, "createAlias"));
        const signatureStmts = singleBlockStmts(methodOf(defaultClass, "createBySignature"));
        const signatureAliasStmts = singleBlockStmts(methodOf(defaultClass, "createSignatureAlias"));

        expect(stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr"
            && stmt.right.method.name === "constructorValue")).toHaveLength(1);
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(false);
        expect(aliasStmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(false);
        expect(signatureStmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr"
            && stmt.right.method.name === "constructorBySignature")).toHaveLength(1);
        for (const methodStmts of [signatureStmts, signatureAliasStmts]) {
            expect(methodStmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(false);
        }
        for (const methodStmts of [stmts, aliasStmts, signatureStmts, signatureAliasStmts]) {
            const allocations = methodStmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr");
            expect(allocations).toHaveLength(1);
            expect(allocations[0]!.right).toEqual(expect.objectContaining({
                constructorValue: expect.objectContaining({ _: "Local" }),
            }));
        }
        expect(diagnostics.messages).toEqual([]);

        const js = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const concrete = new Function("exports", `${js}\nreturn {
            instance: create() instanceof A,
            aliasInstance: createAlias() instanceof A,
            signatureInstance: createBySignature() instanceof A,
            signatureAliasInstance: createSignatureAlias() instanceof A,
            reads: readCount(),
        };`)({}) as {
            instance: boolean; aliasInstance: boolean;
            signatureInstance: boolean; signatureAliasInstance: boolean; reads: number;
        };
        expect(concrete).toEqual({
            instance: true, aliasInstance: true,
            signatureInstance: true, signatureAliasInstance: true, reads: 4,
        });
    });

    it("snapshots the selected constructor before an argument changes its binding", () => {
        const source = `
            class A { value: number; constructor(value: number) { this.value = value; } }
            class B { value: number; constructor(value: number) { this.value = value; } }
            let selected: typeof A | typeof B = A;
            function pick(): typeof A | typeof B { return selected; }
            function argument(): number { selected = B; return 7; }
            export function check(): boolean {
                const value = new (pick())(argument());
                return value instanceof A && value.value === 7;
            }
            export function choose(flag: boolean): A | B {
                return new (flag ? A : B)(7);
            }
            export function localMutation(): boolean {
                let ctor: typeof A | typeof B = A;
                const value = new ctor((ctor = B) as unknown as number);
                return value instanceof A;
            }
            export function constructArray(ctor: new (length: number) => number[]): number[] {
                return new ctor(3);
            }
            export function indexArgument(ctor: typeof A | typeof B, values: number[]): A | B {
                return new ctor(values[0]);
            }
        `;
        const { file, diagnostics } = lower(source);
        const defaultClass = classByName(file, "%dflt");
        const check = methodOf(defaultClass, "check");
        const block = check.body!.cfg.blocks.find((candidate) => candidate.stmts.some(
            (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr",
        ))!;
        const stmts = block.stmts;
        const pickIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr"
            && stmt.right.method.name === "pick");
        const argumentIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr"
            && stmt.right.method.name === "argument");
        const allocationIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr");
        const pickCall = stmts[pickIndex]!;
        const allocation = stmts[allocationIndex]!;

        expect(pickIndex).toBeGreaterThanOrEqual(0);
        expect(argumentIndex).toBeGreaterThan(pickIndex);
        expect(allocationIndex).toBeGreaterThan(argumentIndex);
        expect(pickCall._).toBe("AssignStmt");
        expect(allocation._).toBe("AssignStmt");
        if (pickCall._ === "AssignStmt" && allocation._ === "AssignStmt" && allocation.right._ === "NewExpr") {
            expect(allocation.right.constructorValue).toEqual(pickCall.left);
        }

        const choiceAllocations = methodOf(defaultClass, "choose").body!.cfg.blocks.flatMap((choiceBlock) =>
            choiceBlock.stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr"),
        );
        expect(choiceAllocations).toHaveLength(1);
        expect(choiceAllocations[0]!.right).toEqual(expect.objectContaining({
            constructorValue: expect.objectContaining({ _: "Local" }),
        }));
        const mutationStmts = methodOf(defaultClass, "localMutation").body!.cfg.blocks.flatMap(
            (mutationBlock) => mutationBlock.stmts,
        );
        const mutationAllocation = mutationStmts.find((stmt) =>
            stmt._ === "AssignStmt" && stmt.right._ === "NewExpr",
        );
        const snapshot = mutationStmts.find((stmt) =>
            stmt._ === "AssignStmt" && stmt.right._ === "Local" && stmt.right.name === "ctor",
        );
        expect(snapshot).toBeDefined();
        if (mutationAllocation?._ === "AssignStmt" && mutationAllocation.right._ === "NewExpr"
            && snapshot?._ === "AssignStmt") {
            expect(mutationAllocation.right.constructorValue).toEqual(snapshot.left);
        }

        const arrayStmts = singleBlockStmts(methodOf(defaultClass, "constructArray"));
        expect(arrayStmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewArrayExpr")).toBe(false);
        expect(arrayStmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: expect.objectContaining({ _: "NewExpr", constructorValue: expect.objectContaining({ _: "Local" }) }),
        }));
        const indexedArgumentStmts = singleBlockStmts(methodOf(defaultClass, "indexArgument"));
        expect(indexedArgumentStmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef")).toBe(true);
        expect(indexedArgumentStmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr")).toBe(true);
        expect(indexedArgumentStmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(false);
        expect(diagnostics.messages).toEqual([]);

        const js = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const concrete = new Function("exports", `${js}\nreturn {
            result: check(), selectedIsB: selected === B, localMutation: localMutation(),
            indexedArgument: indexArgument(A, [7]).value,
        };`)({}) as {
            result: boolean; selectedIsB: boolean; localMutation: boolean; indexedArgument: number;
        };
        expect(concrete).toEqual({ result: true, selectedIsB: true, localMutation: true, indexedArgument: 7 });
    });

    it("keeps complete stable names for class decorators", () => {
        const { file } = lower(`
            @sealed
            @factory(1)
            @ns.decorator
            @very.long.namespace.decorator(1)
            class Decorated {}
        `);
        expect(classByName(file, "Decorated").decorators.map((decorator) => decorator.kind)).toEqual([
            "sealed",
            "factory",
            "ns.decorator",
            "very.long.namespace.decorator",
        ]);
    });

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
    it("evaluates a mutable class property's receiver before unsupported uses", () => {
        const source = `
            namespace N {
                export class A {
                    static marker = 7;
                    static read(): number { return 9; }
                }
            }
            let reads = 0;
            function getN(): typeof N { reads++; return N; }
            export function create(): N.A { return new (getN().A)(); }
            export function marker(): number { return getN().A.marker; }
            export function invoke(): number { return getN().A.read(); }
            export function readCount(): number { return reads; }
        `;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file } = lower(source);
        const defaultClass = classByName(file, "%dflt");
        for (const methodName of ["create", "marker", "invoke"]) {
            const stmts = singleBlockStmts(methodOf(defaultClass, methodName));
            const callIndices = stmts.flatMap((stmt, index) =>
                stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr"
                    && stmt.right.method.name === "getN" ? [index] : [],
            );
            const unsupportedIndex = stmts.findIndex(
                (stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue",
            );

            expect(callIndices).toHaveLength(1);
            expect(callIndices[0]).toBeLessThan(unsupportedIndex);
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr")).toBe(false);
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticFieldRef"
                && stmt.right.field.name === "marker")).toBe(false);
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr"
                && stmt.right.method.name === "read")).toBe(false);
        }

        const js = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const concrete = new Function("exports", `${js}\nreturn {
            created: create() instanceof N.A,
            marker: marker(),
            invoked: invoke(),
            reads: readCount(),
        };`)({}) as { created: boolean; marker: number; invoked: number; reads: number };
        expect(concrete).toEqual({ created: true, marker: 7, invoked: 9, reads: 3 });
    });

    it("keeps lexical class values but rejects mutable qualified constructor reads", () => {
        const source = `
            namespace N {
                export class A { static marker = 7; }
                export function direct(): typeof A { return A; }
            }
            class B { static marker = 8; }
            export function qualified(): typeof N.A { return N.A; }
            export function dynamic(holder: typeof N): typeof N.A { return holder.A; }
            export function marker(): number { return N.A.marker; }
            export function create(): N.A { return new N.A(); }
            export function createParenthesized(): N.A { return new (N.A)(); }
            export function createAsserted(): N.A { return new ((N.A as typeof N.A)!)(); }
            export function replace(): void { N.A = B; }
            export function check(value: object): boolean { return value instanceof N.A; }
            export function checkParenthesized(value: object): boolean { return value instanceof (N.A); }
            export function checkSatisfies(value: object): boolean {
                return value instanceof (N.A satisfies typeof N.A);
            }
        `;
        const { file, diagnostics } = lower(source);
        const namespace = file.namespaces[0]!;
        const classSignature = namespace.classes!.find((clazz) => clazz.signature.name === "A")!.signature;
        const direct = singleBlockStmts(methodOf(namespace.classes!.find((clazz) => clazz.signature.name === "%dflt")!, "direct"));
        const defaultClass = classByName(file, "%dflt");
        const qualified = singleBlockStmts(methodOf(defaultClass, "qualified"));
        const dynamic = singleBlockStmts(methodOf(defaultClass, "dynamic"));
        const marker = singleBlockStmts(methodOf(defaultClass, "marker"));
        const create = singleBlockStmts(methodOf(defaultClass, "create"));
        const createParenthesized = singleBlockStmts(methodOf(defaultClass, "createParenthesized"));
        const createAsserted = singleBlockStmts(methodOf(defaultClass, "createAsserted"));
        const replace = singleBlockStmts(methodOf(defaultClass, "replace"));
        const check = singleBlockStmts(methodOf(defaultClass, "check"));
        const checkParenthesized = singleBlockStmts(methodOf(defaultClass, "checkParenthesized"));
        const checkSatisfies = singleBlockStmts(methodOf(defaultClass, "checkSatisfies"));

        expect(direct).toContainEqual(expect.objectContaining({
            _: "ReturnStmt", arg: expect.objectContaining({ _: "ClassValueRef", signature: classSignature }),
        }));
        expect(dynamic.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceFieldRef"
            && stmt.right.field.name === "A")).toBe(true);
        for (const stmts of [
            qualified, marker, create, createParenthesized, createAsserted, replace,
            check, checkParenthesized, checkSatisfies,
        ]) {
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(true);
        }
        for (const stmts of [createParenthesized, createAsserted]) {
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr")).toBe(false);
        }
        for (const stmts of [check, checkParenthesized, checkSatisfies]) {
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "InstanceOfExpr")).toBe(false);
        }
        expect(diagnostics.messages).toEqual(expect.arrayContaining([
            expect.stringContaining("mutable namespace class property"),
            expect.stringContaining("instanceof through a mutable class property"),
        ]));

        const js = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const concrete = new Function("exports", `${js}\nreturn {
            before: qualified() === N.direct(),
            after: (replace(), qualified() === B),
            acceptsB: check(new B()),
            rejectsA: check(new (N.direct())()),
            parenthesized: createParenthesized() instanceof B && checkParenthesized(new B()),
            asserted: createAsserted() instanceof B && checkSatisfies(new B()),
        };`)({}) as {
            before: boolean; after: boolean; acceptsB: boolean; rejectsA: boolean;
            parenthesized: boolean; asserted: boolean;
        };
        expect(concrete).toEqual({
            before: true, after: true, acceptsB: true, rejectsA: false,
            parenthesized: true, asserted: true,
        });
    });

    it("rejects computed namespace constructors after evaluating receiver and key once", () => {
        const source = `
            namespace N {
                export class A { kind = "A"; }
                export namespace Inner { export class C { kind = "A"; } }
            }
            class B { kind = "B"; }
            let receiverReads = 0;
            let keyReads = 0;
            function getN(): typeof N { receiverReads++; return N; }
            function getKey(): "A" { keyReads++; return "A"; }
            export function computed(): N.A { return new N["A"](); }
            export function wrapped(): N.A { return new ((N["A"] as typeof N.A)!)(); }
            export function castReceiver(): N.A { return new ((N as any)["A"])(); }
            export function nestedClass(): N.Inner.C { return new (N["Inner"].C)(); }
            export function throughParameter(holder: typeof N): N.A { return new holder["A"](); }
            export function withEffects(): N.A { return new (getN()[getKey()])(); }
            export function replace(): void { N.A = B; }
            export function readCounts(): number[] { return [receiverReads, keyReads]; }
        `;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file, diagnostics } = lower(source);
        const defaultClass = classByName(file, "%dflt");
        for (const name of ["computed", "wrapped", "castReceiver", "nestedClass", "throughParameter", "withEffects"]) {
            const stmts = singleBlockStmts(methodOf(defaultClass, name));
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(true);
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr")).toBe(false);
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef")).toBe(false);
        }

        const effectStmts = singleBlockStmts(methodOf(defaultClass, "withEffects"));
        const receiverIndex = effectStmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "getN");
        const keyIndex = effectStmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "getKey");
        const unsupportedIndex = effectStmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "UnsupportedValue");
        expect(receiverIndex).toBeGreaterThanOrEqual(0);
        expect(keyIndex).toBeGreaterThan(receiverIndex);
        expect(unsupportedIndex).toBeGreaterThan(keyIndex);
        expect(diagnostics.messages).toEqual(expect.arrayContaining([
            expect.stringContaining("computed constructor access"),
        ]));

        const js = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const concrete = new Function("exports", `${js}\nconst before = {
            computed: computed().kind,
            wrapped: wrapped().kind,
            castReceiver: castReceiver().kind,
            nestedClass: nestedClass().kind,
            parameter: throughParameter(N).kind,
            effects: withEffects().kind,
        };
        replace();
        const after = {
            computed: computed().kind,
            wrapped: wrapped().kind,
            castReceiver: castReceiver().kind,
            nestedClass: nestedClass().kind,
            parameter: throughParameter(N).kind,
            effects: withEffects().kind,
        };
        return { before, after, counts: readCounts() };`)({}) as {
            before: Record<string, string>; after: Record<string, string>; counts: number[];
        };
        expect(concrete).toEqual({
            before: { computed: "A", wrapped: "A", castReceiver: "A", nestedClass: "A", parameter: "A", effects: "A" },
            after: { computed: "B", wrapped: "B", castReceiver: "B", nestedClass: "A", parameter: "B", effects: "B" },
            counts: [2, 2],
        });
    });

    it("rejects computed constructor callees without treating property reads as array reads", () => {
        const source = `
            class A { kind = "A"; }
            class B { kind = "B"; }
            let holder: { Ctor: typeof A | typeof B } = { Ctor: A };
            let nestedHolder: { slot: typeof holder } = { slot: holder };
            let receiverReads = 0;
            let keyReads = 0;
            function getHolder(): typeof holder { receiverReads++; return holder; }
            function getKey(): "Ctor" { keyReads++; return "Ctor"; }
            function pick(ctor: typeof A | typeof B): typeof A | typeof B { return ctor; }
            export function computed(): A | B { return new (getHolder()[getKey()])(); }
            export function wrapped(): A | B { return new ((holder["Ctor"] as typeof A | typeof B)!)(); }
            export function nested(): A | B { return new (nestedHolder["slot"]["Ctor"])(); }
            export function conditional(flag: boolean): A | B { return new (flag ? A : holder["Ctor"])(); }
            export function throughCall(): A | B { return new (pick(holder["Ctor"]))(); }
            export function array(constructors: Array<typeof A | typeof B>): A | B {
                return new constructors[0]();
            }
            export function replace(): void { holder.Ctor = B; }
            export function readCounts(): number[] { return [receiverReads, keyReads]; }
        `;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file, diagnostics } = lower(source);
        const defaultClass = classByName(file, "%dflt");
        for (const name of ["computed", "wrapped", "nested", "conditional", "throughCall", "array"]) {
            const stmts = methodOf(defaultClass, name).body!.cfg.blocks.flatMap((block) => block.stmts);
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(true);
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "ArrayRef")).toBe(false);
            expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr")).toBe(false);
        }

        const computedStmts = singleBlockStmts(methodOf(defaultClass, "computed"));
        const receiverIndex = computedStmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "getHolder");
        const keyIndex = computedStmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "getKey");
        const unsupportedIndex = computedStmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "UnsupportedValue");
        expect(receiverIndex).toBeGreaterThanOrEqual(0);
        expect(keyIndex).toBeGreaterThan(receiverIndex);
        expect(unsupportedIndex).toBeGreaterThan(keyIndex);
        expect(diagnostics.messages).toEqual(expect.arrayContaining([
            expect.stringContaining("computed constructor access"),
        ]));

        const js = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const concrete = new Function("exports", `${js}\nconst before = [
            computed().kind, wrapped().kind, nested().kind,
            conditional(false).kind, throughCall().kind, array([A, B]).kind,
        ];
        replace();
        const after = [
            computed().kind, wrapped().kind, nested().kind,
            conditional(false).kind, throughCall().kind, array([B, A]).kind,
        ];
        return { before, after, counts: readCounts() };`)({}) as {
            before: string[]; after: string[]; counts: number[];
        };
        expect(concrete).toEqual({
            before: ["A", "A", "A", "A", "A", "A"],
            after: ["B", "B", "B", "B", "B", "B"],
            counts: [2, 2],
        });
    });

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
