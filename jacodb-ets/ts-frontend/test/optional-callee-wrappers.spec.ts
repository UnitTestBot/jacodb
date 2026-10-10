import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { executeObjectIr } from "./object-runtime";
import { lower } from "./util";

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
