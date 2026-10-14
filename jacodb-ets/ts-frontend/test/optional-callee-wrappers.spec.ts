import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { StmtDto } from "../src/dto/stmts";
import { executeObjectIr } from "./object-runtime";
import { lower, methodByName } from "./util";

function executions(source: string): ((...args: unknown[]) => unknown)[] {
    const { file, diagnostics } = lower(source);
    const serialized = JSON.parse(JSON.stringify(file));
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;

    expect(diagnostics.messages).toEqual([]);
    const native = runInNewContext(`${javascript}\nexports.value`, { exports: {} });
    return [native, (...args) => executeObjectIr(serialized, "value", args)];
}

function emitsCall(stmt: StmtDto): boolean {
    if (stmt._ === "CallStmt") return true;
    if (stmt._ !== "AssignStmt") return false;

    const kind = stmt.right._;
    return kind === "StaticCallExpr" || kind === "InstanceCallExpr" || kind === "PtrCallExpr";
}

function emitsPropertyRead(stmt: StmtDto): boolean {
    if (stmt._ !== "AssignStmt") return false;

    const kind = stmt.right._;
    return kind === "InstanceFieldRef" || kind === "PropertyRef" || kind === "ArrayRef";
}

describe("transparent optional callees", () => {
    it.each([
        "(object.m)?.()",
        "(object.m)()",
        "(object['m'])?.()",
        "(object['m'] as any)?.()",
        "(object['m'] as any)()",
        "(<any>object['m'])?.()",
        "object['m']!?.()",
        "(object['m'] satisfies any)?.()",
        "(object?.['m'])?.()",
        "(object?.['m'])()",
        "(object?.['m'] as any)?.()",
        "object?.['m']!()",
        "(object?.['m'])!()",
        "((object?.['m'] as any)!)?.()",
    ])("keeps the getter's original receiver and reads it once: %s", (call) => {
        const source = `
            export function value(): number {
                let reads = 0;
                let object: any = {
                    base: 3,
                    get m() {
                        reads++;
                        object = { base: 9 };
                        return function() { return this.base * 10 + reads; };
                    }
                };
                return ${call};
            }
        `;

        for (const execute of executions(source)) {
            expect(execute()).toBe(31);
        }
    });

    it.each([
        "(object?.[key()])?.(argument())",
        "(object?.[key()] as any)?.(argument())",
        "(object?.[key()] satisfies any)?.(argument())",
        "object?.[key()]!?.(argument())",
        "object?.[key()]!(argument())",
        "object?.[key()]!.n?.(argument())",
    ])("keeps inner guards and skips keys and arguments on null: %s", (call) => {
        const source = `
            export function value(): number {
                let order = 0;
                const object: any = null;
                const key = function() { order = order * 10 + 1; return "m"; };
                const argument = function() { order = order * 10 + 2; return 0; };
                ${call};
                return order;
            }
        `;

        for (const execute of executions(source)) {
            expect(execute()).toBe(0);
        }
    });

    it("skips arguments after a grouped getter returns an absent callee", () => {
        const source = `
            export function value(): number {
                let order = 0;
                const object: any = { get m() { order = order * 10 + 2; return undefined; } };
                const key = function() { order = order * 10 + 1; return "m"; };
                const argument = function() { order = order * 10 + 3; return 0; };
                (object?.[key()] as any)?.(argument());
                return order;
            }
        `;

        for (const execute of executions(source)) {
            expect(execute()).toBe(12);
        }
    });

    it("retains the nested receiver before key coercion, getter and argument effects", () => {
        const source = `
            export function value(): number {
                let order = 0;
                let object: any = {
                    child: {
                        base: 3,
                        get m() {
                            order = order * 10 + 2;
                            object = null;
                            return function(a: number) { return this.base * 100 + a; };
                        }
                    }
                };
                const key = { toString: function() {
                    order = order * 10 + 1;
                    object = { child: { base: 9 } };
                    return "m";
                } };
                const argument = function() { order = order * 10 + 3; return order; };
                return ((object?.child?.[key as any]) as any)?.(argument());
            }
        `;

        for (const execute of executions(source)) {
            expect(execute()).toBe(423);
        }
    });

    it.each(["null", "{ child: null }"])("skips nested keys and arguments for %s", (initial) => {
        const source = `
            export function value(): number {
                let order = 0;
                const object: any = ${initial};
                const key = function() { order = order * 10 + 1; return "m"; };
                const argument = function() { order = order * 10 + 2; return 0; };
                (object?.child?.[key()] as any)?.(argument());
                return order;
            }
        `;

        for (const execute of executions(source)) {
            expect(execute()).toBe(0);
        }
    });

    it.each([
        ["(object?.[key()])(argument())", 2],
        ["(object?.[key()])!(argument())", 2],
        ["(object?.[key()] as any)(argument())", 2],
        ["(object?.[key()]).n?.(argument())", 0],
        ["(object?.[key()] as any).n?.(argument())", 0],
    ])("retains the chain boundary and throwing order: %s", (call, expectedOrder) => {
        const source = `
            export function value(object: any, state: { order: number }): unknown {
                const key = function() { state.order = state.order * 10 + 1; return "m"; };
                const argument = function() { state.order = state.order * 10 + 2; return 0; };
                return ${call};
            }
        `;

        for (const execute of executions(source)) {
            const state = { order: 0 };
            let thrown: unknown;

            try {
                execute(null, state);
            } catch (error) {
                thrown = error;
            }

            expect(thrown).toHaveProperty("name", "TypeError");
            expect(state.order).toBe(expectedOrder);
        }
    });
});

describe("optional super callees", () => {
    it.each([
        { call: "super.m?.()", kindName: "CallExpression", typeKind: "NumberType" },
        { call: "(super.m as any)?.()", kindName: "CallExpression", typeKind: "AnyType" },
        { call: "super['m']?.()", kindName: "CallExpression", typeKind: "NumberType" },
        {
            call: "(super[this.key()] as any)?.(this.argument())",
            kindName: "CallExpression",
            typeKind: "AnyType",
        },
        { call: "super.m?.().toString()", kindName: "CallExpression", typeKind: "StringType" },
    ])("uses an explicit fallback before lowering the property or call: $call", ({ call, kindName, typeKind }) => {
        const source = `
            class Base { m(): number { return 1; } }
            class Derived extends Base {
                override m(): number { return 2; }
                key(): "m" { return "m"; }
                argument(): number { return 0; }
                f(): unknown { return ${call}; }
            }
        `;

        const { file, diagnostics } = lower(source);
        const body = methodByName(file, "f").body!;
        const stmts = body.cfg.blocks.flatMap((block) => block.stmts);
        const rawAssign = stmts.find((stmt): stmt is Extract<StmtDto, { _: "AssignStmt" }> =>
            stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue",
        );
        const startOffset = source.indexOf(call);
        const precedingLines = source.slice(0, startOffset).split("\n");
        const line = precedingLines.length;
        const column = precedingLines[precedingLines.length - 1].length + 1;
        const rawOrigin = body.stmtOrigins?.find(({ blockId, stmtIndex }) =>
            body.cfg.blocks[blockId].stmts[stmtIndex] === rawAssign,
        );

        expect(diagnostics.messages).toEqual([
            `test.ts:${line}:${column}: unsupported expression: optional super property call needs superclass-relative property lookup`,
        ]);
        expect(rawAssign).toBeDefined();
        expect(rawAssign?.right).toEqual({
            _: "UnsupportedValue",
            kindName,
            text: call,
            type: { _: typeKind },
        });
        expect(rawOrigin?.source).toMatchObject({
            startOffset,
            endOffset: startOffset + call.length,
            nodeKind: kindName,
        });
        expect(stmts.some(emitsPropertyRead)).toBe(false);
        expect(stmts.some(emitsCall)).toBe(false);
    });

    it("still lowers an ordinary super call before optional invocation of its result", () => {
        const source = `
            class Base { m(): () => number { return () => 1; } }
            class Derived extends Base {
                override m(): () => number { return () => 2; }
                f(): number | undefined {
                    const direct = super.m();
                    return super.m()?.();
                }
            }
        `;

        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "f").body!.cfg.blocks.flatMap((block) => block.stmts);
        const superCalls = stmts.filter((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "InstanceCallExpr"
            && stmt.right.isSuperCall === true);

        expect(diagnostics.messages).toEqual([]);
        expect(superCalls).toHaveLength(2);
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue"))
            .toBe(false);
    });
});

describe("direct super getter calls", () => {
    it("falls back before reading the getter or evaluating call arguments", () => {
        const call = "super.m(this.argument())";
        const source = `
            class Base {
                get m(): (value: number) => number { return (value) => value + 1; }
            }
            class Derived extends Base {
                argument(): number { return 2; }
                f(): number { return ${call}; }
            }
        `;

        const { file, diagnostics } = lower(source);
        const body = methodByName(file, "f").body!;
        const stmts = body.cfg.blocks.flatMap((block) => block.stmts);
        const rawAssign = stmts.find((stmt): stmt is Extract<StmtDto, { _: "AssignStmt" }> =>
            stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue",
        );
        const startOffset = source.indexOf(call);
        const precedingLines = source.slice(0, startOffset).split("\n");
        const line = precedingLines.length;
        const column = precedingLines[precedingLines.length - 1].length + 1;
        const rawOrigin = body.stmtOrigins?.find(({ blockId, stmtIndex }) =>
            body.cfg.blocks[blockId].stmts[stmtIndex] === rawAssign,
        );

        expect(diagnostics.messages).toEqual([
            `test.ts:${line}:${column}: unsupported expression: super getter call needs superclass-relative property lookup`,
        ]);
        expect(rawAssign?.right).toEqual({
            _: "UnsupportedValue",
            kindName: "CallExpression",
            text: call,
            type: { _: "NumberType" },
        });
        expect(rawOrigin?.source).toMatchObject({
            startOffset,
            endOffset: startOffset + call.length,
            nodeKind: "CallExpression",
        });
        expect(stmts.some(emitsPropertyRead)).toBe(false);
        expect(stmts.some(emitsCall)).toBe(false);
    });
});

describe("nonoptional super property reads", () => {
    it.each([
        { expression: "super.x", kindName: "PropertyAccessExpression" },
        { expression: "super['x']", kindName: "ElementAccessExpression" },
        { expression: "super[this.key()]", kindName: "ElementAccessExpression" },
        { expression: "super['m'](this.argument())", kindName: "CallExpression" },
        { expression: "super[this.methodKey()](this.argument())", kindName: "CallExpression" },
    ])("falls back before a superclass-relative read or computed call: $expression", ({ expression, kindName }) => {
        const source = `
            class Base {
                get x(): number { return 1; }
                m(value: number): number { return value + 1; }
            }
            class Derived extends Base {
                override get x(): number { return 2; }
                override m(value: number): number { return value + 2; }
                key(): "x" { return "x"; }
                methodKey(): "m" { return "m"; }
                argument(): number { return 5; }
                f(): number { return ${expression}; }
            }
        `;

        const { file, diagnostics } = lower(source);
        const body = methodByName(file, "f").body!;
        const stmts = body.cfg.blocks.flatMap((block) => block.stmts);
        const rawAssign = stmts.find((stmt): stmt is Extract<StmtDto, { _: "AssignStmt" }> =>
            stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue",
        );
        const startOffset = source.indexOf(expression);
        const precedingLines = source.slice(0, startOffset).split("\n");
        const line = precedingLines.length;
        const column = precedingLines[precedingLines.length - 1].length + 1;
        const rawOrigin = body.stmtOrigins?.find(({ blockId, stmtIndex }) =>
            body.cfg.blocks[blockId].stmts[stmtIndex] === rawAssign,
        );

        expect(diagnostics.messages).toEqual([
            `test.ts:${line}:${column}: unsupported expression: super property read needs superclass-relative property lookup`,
        ]);
        expect(rawAssign?.right).toEqual({
            _: "UnsupportedValue",
            kindName,
            text: expression,
            type: { _: "NumberType" },
        });
        expect(rawOrigin?.source).toMatchObject({
            startOffset,
            endOffset: startOffset + expression.length,
            nodeKind: kindName,
        });
        expect(stmts.some(emitsPropertyRead)).toBe(false);
        expect(stmts.some(emitsCall)).toBe(false);
    });
});
