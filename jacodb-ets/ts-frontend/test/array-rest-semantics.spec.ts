import { describe, expect, it } from "vitest";
import { consumers } from "./array-test-consumers";

describe("array iterator behavior after JSON round trip", () => {
    it("packs a fresh tail for empty, one-element and longer arrays", () => {
        const { ir, native } = consumers("function tail(input: number[]): number[] { const [first, ...rest] = input; return rest; }", "tail");

        for (const input of [[], [1], [1, 2, 3]]) {
            const result = ir(input);
            expect(result).toEqual(native(input));
            expect(result).not.toBe(input);
        }
    });

    for (const source of [
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

    it("closes on a default initializer throw and preserves the original exception", () => {
        const original = new Error("binding default");
        const { ir, native } = consumers(
            "function tail(input: any, fail: () => never) { const [a = fail(), ...rest] = input; return rest; }",
            "tail",
        );
        const fail = () => { throw original; };
        const run = (collect: any) => {
            let closed = 0;
            const input = { [Symbol.iterator]: () => ({ next: () => ({ value: undefined, done: false }), return() { closed++; throw new Error("close"); } }) };

            expect(() => collect(input, fail)).toThrow(original);
            expect(closed).toBe(1);
        };

        run(native);
        run(ir);
    });
});
