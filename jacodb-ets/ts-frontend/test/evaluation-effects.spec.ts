import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { executeObjectIr } from "./object-runtime";
import { lower } from "./util";

function expectSameResult(source: string, expected: unknown): void {
    const { file, diagnostics } = lower(source);
    const serialized = JSON.parse(JSON.stringify(file));
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;

    expect(diagnostics.messages).toEqual([]);
    expect(runInNewContext(`${javascript}\nexports.value()`, { exports: {} })).toBe(expected);
    expect(executeObjectIr(serialized, "value")).toBe(expected);
}

describe("implicit effects preserve evaluated values", () => {
    it("keeps the receiver selected before property-key coercion", () => {
        expectSameResult(`
            export function value(): number {
                let object: any = { x: 3 };
                const key = { toString: function() { object = { x: 9 }; return "x"; } };
                return object[key as any];
            }
        `, 3);
    });

    it("keeps an earlier call argument before a getter changes its binding", () => {
        expectSameResult(`
            export function value(): number {
                let first = 1;
                const object = { get x() { first = 9; return 2; } };
                const combine = function(a: number, b: number) { return a * 10 + b; };
                return combine(first, object.x);
            }
        `, 12);
    });

    it("keeps the left operand before a getter changes its binding", () => {
        expectSameResult(`
            export function value(): number {
                let first = 1;
                const object = { get x() { first = 9; return 2; } };
                return first + object.x;
            }
        `, 3);
    });

    it("keeps an earlier argument before computed literal key coercion", () => {
        expectSameResult(`
            export function value(): number {
                let first = 1;
                const key = { toString: function() { first = 9; return "x"; } };
                const combine = function(a: number, b: any) { return a * 10 + b.x; };
                return combine(first, { [key as any]: 2 });
            }
        `, 12);
    });

    it("keeps an earlier argument before a computed getter name is coerced", () => {
        expectSameResult(`
            export function value(): number {
                let first = 1;
                const key = { toString: function() { first = 9; return "x"; } };
                const combine = function(a: number, b: any) { return a * 10 + b.x; };
                return combine(first, { get [key as any]() { return 2; } });
            }
        `, 12);
    });

    it("keeps an earlier argument before spread invokes a getter", () => {
        expectSameResult(`
            export function value(): number {
                let first = 1;
                const object = { get x() { first = 9; return 2; } };
                const combine = function(a: number, b: any) { return a * 10 + b.x; };
                return combine(first, { ...object });
            }
        `, 12);
    });
});

describe("getter-selected calls", () => {
    it.each(["object.m(argument())", "object?.m(argument())"])(
        "reads a getter behind a class method declaration before arguments: %s", (call) => {
            expectSameResult(`
                class Callable { m(value: number): number { return value; } }
                export function value(): number {
                    let order = 0;
                    const object: Callable = {
                        get m() { order = order * 10 + 1; return function(a: number) { return a; }; }
                    };
                    const argument = function() { order = order * 10 + 2; return 0; };
                    ${call};
                    return order;
                }
            `, 12);
        },
    );

    it.each(["object.m(argument())", "object?.m(argument())"])(
        "reads a getter behind a method signature before arguments: %s", (call) => {
            expectSameResult(`
                interface Callable { m(value: number): number; }
                export function value(): number {
                    let order = 0;
                    const object: Callable = {
                        get m() { order = order * 10 + 1; return function(a: number) { return a; }; }
                    };
                    const argument = function() { order = order * 10 + 2; return 0; };
                    ${call};
                    return order;
                }
            `, 12);
        },
    );

    it.each([
        "object.m(argument())",
        "object.m?.(argument())",
        "object?.m(argument())",
        "object?.m?.(argument())",
        "object['m'](argument())",
    ])("reads the callee once before arguments: %s", (call) => {
        expectSameResult(`
            export function value(): number {
                let order = 0;
                const object = {
                    get m() { order = order * 10 + 1; return function(a: number) { return a; }; }
                };
                const argument = function() { order = order * 10 + 2; return 0; };
                ${call};
                return order;
            }
        `, 12);
    });

    it.each(["object.m()", "object.m?.()", "object?.m?.()", "object['m']()", "object['m']?.()", "object?.['m']?.()"])(
        "retains this when the getter replaces the receiver binding: %s", (call) => {
            expectSameResult(`
                export function value(): number {
                    let object: any = {
                        base: 3,
                        get m() { object = { base: 9 }; return function() { return this.base; }; }
                    };
                    return ${call};
                }
            `, 3);
        },
    );

    it("skips arguments when the getter returns an absent optional callee", () => {
        expectSameResult(`
            export function value(): number {
                let order = 0;
                const object = {
                    get m(): ((a: number) => number) | undefined { order++; return undefined; }
                };
                const argument = function() { order = order * 10 + 2; return 0; };
                object.m?.(argument());
                return order;
            }
        `, 1);
    });
});
