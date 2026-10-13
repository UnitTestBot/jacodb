import { describe, expect, it } from "vitest";
import { consumers } from "./array-test-consumers";

describe("array bindings without rest consume and close iterators", () => {
    it("reads UTF16 code points and keeps defaults after exhaustion", () => {
        const { ir, native } = consumers(
            "function bind(input: any) { const [first, second = 'end', third] = input; return [first, second, third]; }",
            "bind",
        );

        for (const input of ["", "\ud83d\ude00", "\ud800", "\udc00", "A\ud83d\ude00\ud800", "\udc00\ud800"]) {
            expect(ir(input)).toEqual(native(input));
        }
        expect(ir("\ud83d\ude00")).toEqual(["\ud83d\ude00", "end", undefined]);
    });

    it("uses undefined for sparse slots and applies binding defaults", () => {
        const { ir, native } = consumers(
            "function bind(input: any) { const [first = 7, second = 11, third] = input; return [first, second, third]; }",
            "bind",
        );
        const input = [, , 3];

        expect(ir(input)).toEqual(native(input));
        expect(ir(input)).toEqual([7, 11, 3]);
    });

    it("closes early with the iterator as receiver and reads return once", () => {
        const { ir, native } = consumers("function bind(input: any) { const [first] = input; return first; }", "bind");
        for (const consume of [native, ir]) {
            const events: string[] = [];
            const iterator = {
                get next() {
                    events.push("next lookup");
                    return function () {
                        expect(this).toBe(iterator);
                        events.push("next call");
                        return { value: 7, done: false };
                    };
                },
                get return() {
                    events.push("return lookup");
                    const close = function () {
                        expect(this).toBe(iterator);
                        events.push("return call");
                        return { get done() { throw new Error("close result done must not be read"); } };
                    };
                    close.call = () => { throw new Error("a user call property must not run"); };
                    return close;
                },
            };

            expect(consume({ [Symbol.iterator]: () => iterator })).toBe(7);
            expect(events).toEqual(["next lookup", "next call", "return lookup", "return call"]);
        }
    });

    it("acquires and closes an empty binding without calling next", () => {
        const { ir, native } = consumers("function bind(input: any) { const [] = input; return 7; }", "bind");
        for (const consume of [native, ir]) {
            const events: string[] = [];
            const iterator = {
                next() { throw new Error("next must not run"); },
                return() { events.push("closed"); return {}; },
            };

            expect(consume({ [Symbol.iterator]: () => iterator })).toBe(7);
            expect(events).toEqual(["closed"]);
        }
    });

    it("omitted elements do not read value and an exhausted iterator is not closed", () => {
        const { ir, native } = consumers(
            "function bind(input: any) { const [, second = 7, third] = input; return [second, third]; }",
            "bind",
        );
        for (const consume of [native, ir]) {
            let steps = 0;
            const iterator = {
                next() {
                    steps++;
                    if (steps > 2) throw new Error("next after exhaustion");
                    return { done: steps === 2, get value() { throw new Error("omitted or exhausted value"); } };
                },
                get return() { throw new Error("exhausted iterator must not close"); },
            };

            expect(consume({ [Symbol.iterator]: () => iterator })).toEqual([7, undefined]);
            expect(steps).toBe(2);
        }
    });

    it("accepts absent return and rejects noncallable or primitive close results", () => {
        const { ir, native } = consumers("function bind(input: any) { const [first] = input; return first; }", "bind");
        for (const consume of [native, ir]) {
            for (const close of [null, undefined]) {
                const iterator = { next: () => ({ value: 7, done: false }), return: close };
                expect(consume({ [Symbol.iterator]: () => iterator })).toBe(7);
            }
            for (const close of [1, () => 1]) {
                const iterator = { next: () => ({ value: 7, done: false }), return: close };
                expect(() => consume({ [Symbol.iterator]: () => iterator })).toThrow(TypeError);
            }
        }
    });

    it("preserves a default initializer throw over a cleanup throw", () => {
        const { ir, native } = consumers(
            "function bind(input: any, fail: () => never) { const [first = fail()] = input; return first; }",
            "bind",
        );
        const original = new Error("default failed");
        const fail = () => { throw original; };
        for (const consume of [native, ir]) {
            let closes = 0;
            const iterator = {
                next: () => ({ value: undefined, done: false }),
                return() { closes++; throw new Error("cleanup failed"); },
            };

            expect(() => consume({ [Symbol.iterator]: () => iterator }, fail)).toThrow(original);
            expect(closes).toBe(1);
        }
    });

    it("propagates a normal return getter failure", () => {
        const { ir, native } = consumers("function bind(input: any) { const [first] = input; return first; }", "bind");
        const failure = new Error("return lookup failed");
        for (const consume of [native, ir]) {
            let lookups = 0;
            const iterator = {
                next: () => ({ value: 7, done: false }),
                get return() { lookups++; throw failure; },
            };

            expect(() => consume({ [Symbol.iterator]: () => iterator })).toThrow(failure);
            expect(lookups).toBe(1);
        }
    });

    it("does not close when next or its value getter throws", () => {
        const { ir, native } = consumers("function bind(input: any) { const [first] = input; return first; }", "bind");
        const failure = new Error("iterator step failed");
        for (const consume of [native, ir]) {
            for (const failNext of [false, true]) {
                let closes = 0;
                const iterator = {
                    next() {
                        if (failNext) throw failure;
                        return { done: false, get value() { throw failure; } };
                    },
                    return() { closes++; return {}; },
                };

                expect(() => consume({ [Symbol.iterator]: () => iterator })).toThrow(failure);
                expect(closes).toBe(0);
            }
        }
    });

    it("closes nested bindings from inner to outer", () => {
        const { ir, native } = consumers("function bind(input: any) { const [[first]] = input; return first; }", "bind");
        for (const consume of [native, ir]) {
            const events: string[] = [];
            const inner = {
                next: () => ({ value: 7, done: false }),
                return() { events.push("inner"); return {}; },
            };
            const outer = {
                next: () => ({ value: { [Symbol.iterator]: () => inner }, done: false }),
                return() { events.push("outer"); return {}; },
            };

            expect(consume({ [Symbol.iterator]: () => outer })).toBe(7);
            expect(events).toEqual(["inner", "outer"]);
        }
    });

    it("advances holes before nested bindings without reading omitted values", () => {
        const { ir, native } = consumers(
            "function bind(data: any) { const [first, , [inner]] = data; return [first, inner]; }",
            "bind",
        );

        for (const consume of [native, ir]) {
            const events: string[] = [];
            const inner = {
                next() {
                    expect(this).toBe(inner);
                    events.push("inner next");
                    return { done: false, get value() { events.push("inner value"); return "a"; } };
                },
                return() { expect(this).toBe(inner); events.push("inner close"); return {}; },
            };
            const nested = {
                [Symbol.iterator]() { expect(this).toBe(nested); events.push("inner iterator"); return inner; },
            };
            let step = 0;
            const outer = {
                next() {
                    expect(this).toBe(outer);
                    const index = step++;
                    events.push(`outer next ${index}`);
                    if (index > 2) throw new Error("binding consumed past the nested element");

                    return {
                        done: false,
                        get value() {
                            if (index === 1) throw new Error("the omitted value must not be read");
                            events.push(`outer value ${index}`);
                            return index === 0 ? 1 : nested;
                        },
                    };
                },
                return() { expect(this).toBe(outer); events.push("outer close"); return {}; },
            };
            const data = {
                [Symbol.iterator]() { expect(this).toBe(data); events.push("outer iterator"); return outer; },
            };

            const result = consume(data);

            expect(result).toEqual([1, "a"]);
            expect(events).toEqual([
                "outer iterator", "outer next 0", "outer value 0", "outer next 1", "outer next 2", "outer value 2",
                "inner iterator", "inner next", "inner value", "inner close", "outer close",
            ]);
            expect(step).toBe(3);
        }
    });
});
