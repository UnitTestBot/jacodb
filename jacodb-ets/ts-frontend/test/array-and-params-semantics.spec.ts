import { describe, expect, it } from "vitest";
import * as ts from "typescript";
import { serializeEtsFile } from "../src/serialize";
import { executor } from "./execute";
import { lower } from "./util";

function consumers(source: string, name: string, externals: Record<string, any> = {}) {
    const { file, diagnostics } = lower(source);
    expect(diagnostics.messages).toEqual([]);
    const ir = executor(JSON.parse(serializeEtsFile(file)), externals);
    const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
    const native = new Function(...Object.keys(externals), `${js}; return ${name};`)(...Object.values(externals));
    return { ir: (...args: any[]) => ir.call(name, ...args), native, initialize: () => ir.initialize() };
}

describe("array iterator behavior after JSON round trip", () => {
    for (const source of [
        "function collect(input: any): any[] { return [...input]; }",
        "function collect(input: any): any[] { const [...rest] = input; return rest; }",
    ]) {
        it(`uses the selected iterator and caches next: ${source}`, () => {
            const { ir, native } = consumers(source, "collect");
            const input = () => {
                let nextReads = 0;
                let count = 0;
                const next = function (this: any) {
                    expect(this.marker).toBe(42);
                    return { value: ++count, done: count > 2 ? "finished" : 0 };
                };
                next.call = () => { throw new Error("A user-defined .call must not be read"); };
                return {
                    length: 100,
                    [Symbol.iterator]() {
                        return { marker: 42, get next() { expect(++nextReads).toBe(1); return next; } };
                    },
                };
            };

            expect(ir(input())).toEqual(native(input()));
            expect(ir(input())).toEqual([1, 2]);
        });

        it(`rejects invalid iterator results: ${source}`, () => {
            const { ir, native } = consumers(source, "collect");
            const malformed = () => ({ [Symbol.iterator]: () => ({ next: () => 1 }) });

            expect(() => native(malformed())).toThrow(TypeError);
            expect(() => ir(malformed())).toThrow(TypeError);
        });
    }

    it("observes changing array length and turns sparse iterator values into stored undefined", () => {
        const { ir, native } = consumers("function copy(input: number[]): number[] { return [0, ...input, 9]; }", "copy");
        const input = () => {
            const values = [1];
            Object.defineProperty(values, 0, { get() { values[1] = 2; return 1; } });
            return values;
        };

        expect(ir(input())).toEqual(native(input()));
        const sparse = new Array(2);
        const result = ir(sparse);
        expect(result).toEqual(native(sparse));
        expect(Object.hasOwn(result, 1)).toBe(true);
        expect(Object.hasOwn(result, 2)).toBe(true);
    });

    it("does not call next after done in a rest binding", () => {
        const { ir, native } = consumers("function tail(input: any) { const [a, b, ...rest] = input; return rest; }", "tail");
        const input = () => {
            let calls = 0;
            return { [Symbol.iterator]: () => ({ next() { if (++calls > 1) throw new Error("next after done"); return { done: true }; } }) };
        };

        expect(ir(input())).toEqual(native(input()));
    });

    it("skips an omitted binding without reading its value getter", () => {
        const { ir, native } = consumers("function tail(input: any) { const [, ...rest] = input; return rest; }", "tail");
        const input = () => {
            let calls = 0;
            return { [Symbol.iterator]: () => ({ next() {
                if (calls++ === 0) return { done: false, get value() { throw new Error("omitted value"); } };
                return { done: true };
            } }) };
        };

        expect(ir(input())).toEqual(native(input()));
    });

    it("evaluates the source once and drains it before the following element", () => {
        const source = "declare function mark(n: number): number; declare function get(): Iterable<number>; function copy() { return [mark(0), ...get(), mark(9)]; }";
        const observed = (consumer: "ir" | "native") => {
            const order: any[] = [];
            const externals = {
                mark(n: number) { order.push(n); return n; },
                get() {
                    order.push("source");
                    return { *[Symbol.iterator]() { order.push("first"); yield 1; order.push("second"); yield 2; order.push("done"); } };
                },
            };
            const functions = consumers(source, "copy", externals);
            return { result: functions[consumer](), order };
        };

        expect(observed("ir")).toEqual(observed("native"));
        expect(observed("ir").order).toEqual([0, "source", "first", "second", "done", 9]);
    });

    it("closes on a default initializer throw and preserves the original exception", () => {
        const original = new Error("binding default");
        const { ir, native } = consumers(
            "declare function fail(): never; function tail(input: any) { const [a = fail(), ...rest] = input; return rest; }",
            "tail", { fail: () => { throw original; } },
        );
        const run = (collect: any) => {
            let closed = 0;
            const input = { [Symbol.iterator]: () => ({ next: () => ({ value: undefined, done: false }), return() { closed++; throw new Error("close"); } }) };

            expect(() => collect(input)).toThrow(original);
            expect(closed).toBe(1);
        };

        run(native);
        run(ir);
    });
});

describe("default and rest parameter observable behavior", () => {
    it("binds earlier parameters before defaults and keeps falsy provided arguments", () => {
        const { ir, native } = consumers("function f(a = 3, b = a + 1) { return [a, b]; }", "f");

        for (const args of [[], [undefined], [0], [null], [false], [0, 7]]) {
            expect(ir(...args)).toEqual(native(...args));
        }
    });

    for (const source of ["function f(a = a) { return a; }", "function f(a = b, b = 2) { return a; }"]) {
        it(`preserves the parameter temporal dead zone: ${source}`, () => {
            const { ir, native } = consumers(source, "f");

            expect(() => native()).toThrow(ReferenceError);
            expect(() => ir()).toThrow(ReferenceError);
            expect(ir(7)).toEqual(native(7));
        });
    }

    it("resolves parameter defaults outside the function body's declarations", () => {
        const { ir, native, initialize } = consumers("const seed = 7; function f(a = seed) { let seed = 9; return a; }", "f");
        initialize();

        expect(ir()).toEqual(native());
        expect(ir()).toBe(7);
    });

    it("packs remaining raw arguments in order, including missing normal arguments", () => {
        const { ir, native } = consumers("function f(first: number, ...rest: number[]) { return rest; }", "f");

        for (const args of [[], [1], [1, 2], [1, 2, 3]]) {
            expect(ir(...args)).toEqual(native(...args));
        }
    });
});

describe("regular expression intrinsic", () => {
    it("retains escaped pattern text and flags despite a shadowed RegExp binding", () => {
        const { ir, native } = consumers("function matcher(RegExp: any) { return /a\\/b/gi; }", "matcher");
        const lowered = ir(() => { throw new Error("shadowed constructor"); });
        const expected = native(() => { throw new Error("shadowed constructor"); });

        expect(lowered.source).toEqual(expected.source);
        expect(lowered.flags).toEqual(expected.flags);
        expect(lowered.test("A/B")).toBe(true);
    });
});
