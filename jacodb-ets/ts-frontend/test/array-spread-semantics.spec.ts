import { describe, expect, it } from "vitest";
import { consumers } from "./array-test-consumers";

describe("array iterator behavior after JSON round trip", () => {
    it("keeps the dynamic spread length for empty, one-element and longer arrays", () => {
        const { ir, native } = consumers("function size(input: number[]): number { return [0, ...input].length; }", "size");

        for (const input of [[], [1], [1, 2, 3]]) {
            expect(ir(input)).toBe(native(input));
            expect(ir(input)).toBe(input.length + 1);
        }
    });

    for (const source of [
        "function collect(input: any): any[] { return [...input]; }",
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

});
