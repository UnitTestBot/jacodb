import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { serializeEtsFile } from "../src/serialize";
import { executeObjectIr } from "./object-runtime";
import { lower } from "./util";

function runners(expression: string, setup = ""): ((args: unknown[]) => unknown)[] {
    const source = `export function value(object: any, key: () => any, arg: () => number): any {
        ${setup}
        return ${expression};
    }`;
    const { file, diagnostics } = lower(source);
    const serialized = JSON.parse(serializeEtsFile(file));
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;
    const native = runInNewContext(`${javascript}\nexports.value`, { exports: {} });

    expect(diagnostics.messages).toEqual([]);
    return [(args) => native(...args), (args) => executeObjectIr(serialized, "value", args)];
}

describe("computed access evaluation order", () => {
    const expressions = [
        "object[key()]",
        "object[key()](arg())",
        "object[key()]?.(arg())",
        "new (object[key()])(arg())",
        "object[key()]`text${arg()}`",
    ];

    for (const expression of expressions) {
        it.each([null, undefined])(`rejects a nullish receiver before coercion: ${expression}`, (receiver) => {
            for (const run of runners(expression)) {
                const events: string[] = [];
                const key = () => {
                    events.push("key");
                    return {
                        [Symbol.toPrimitive]: () => {
                            events.push("coerce");
                            throw new RangeError("key coercion must not run");
                        },
                    };
                };
                const arg = () => { events.push("arg"); return 7; };

                let failure: unknown;
                try { run([receiver, key, arg]); } catch (error) { failure = error; }

                expect(failure).toMatchObject({ name: "TypeError" });
                expect(events).toEqual(["key"]);
            }
        });
    }

    it.each([null, undefined])("skips the entire key expression for an optional receiver", (receiver) => {
        for (const run of runners("object?.[key()]?.(arg())")) {
            const events: string[] = [];
            const key = () => { events.push("key"); throw new RangeError("unexpected key"); };
            const arg = () => { events.push("arg"); return 7; };

            const result = run([receiver, key, arg]);

            expect(result).toBeUndefined();
            expect(events).toEqual([]);
        }
    });

    it("evaluates the key expression before rejecting the receiver", () => {
        for (const run of runners("object[key()]?.(arg())")) {
            const events: string[] = [];
            const key = () => { events.push("key"); throw new RangeError("key expression"); };
            const arg = () => { events.push("arg"); return 7; };

            expect(() => run([null, key, arg])).toThrow("key expression");
            expect(events).toEqual(["key"]);
        }
    });

    it("converts once and selects the getter before arguments on a present receiver", () => {
        for (const run of runners("object[key()]?.(arg())")) {
            const events: string[] = [];
            const object = {
                base: 3,
                get m() {
                    events.push("get");
                    return function(this: { base: number }, arg: number) {
                        events.push("call");
                        return this.base + arg;
                    };
                },
            };
            const key = () => {
                events.push("key");
                return { [Symbol.toPrimitive]: () => { events.push("coerce"); return "m"; } };
            };
            const arg = () => { events.push("arg"); return 7; };

            const result = run([object, key, arg]);

            expect(result).toBe(10);
            expect(events).toEqual(["key", "coerce", "get", "arg", "call"]);
        }
    });

    it.each([null, undefined])("evaluates the assigned value before rejecting the receiver", (receiver) => {
        for (const run of runners("object[key()] = arg()")) {
            const events: string[] = [];
            const key = () => {
                events.push("key");
                return { [Symbol.toPrimitive]: () => { events.push("coerce"); return "m"; } };
            };
            const arg = () => { events.push("arg"); return 7; };

            let failure: unknown;
            try { run([receiver, key, arg]); } catch (error) { failure = error; }

            expect(failure).toMatchObject({ name: "TypeError" });
            expect(events).toEqual(["key", "arg"]);
        }
    });

    it.each([
        { expression: "object[key()] = arg()", expected: 7, stored: { m: 7, other: 1 }, events: ["key", "arg", "coerce"] },
        { expression: "object[key()] += arg()", expected: 10, stored: { m: 3, other: 10 }, events: ["key", "coerce", "arg", "coerce"] },
        { expression: "object[key()] ||= arg()", expected: 3, stored: { m: 3, other: 1 }, events: ["key", "coerce"] },
        { expression: "object[key()] &&= arg()", expected: 7, stored: { m: 3, other: 7 }, events: ["key", "coerce", "arg", "coerce"] },
        { expression: "object[key()]++", expected: 3, stored: { m: 3, other: 4 }, events: ["key", "coerce", "coerce"] },
    ])("converts a computed write key at each Get and Put: $expression", (scenario) => {
        for (const run of runners(scenario.expression)) {
            const events: string[] = [];
            const object = { m: 3, other: 1 };
            let conversion = 0;
            const key = () => {
                events.push("key");
                return {
                    [Symbol.toPrimitive]: () => {
                        events.push("coerce");
                        return conversion++ === 0 ? "m" : "other";
                    },
                };
            };
            const arg = () => { events.push("arg"); return 7; };

            const result = run([object, key, arg]);

            expect(result).toBe(scenario.expected);
            expect(events).toEqual(scenario.events);
            expect(object).toEqual(scenario.stored);
        }
    });

    it("saves the assigned value before key conversion changes its binding", () => {
        const setup = `let rhs = 3;
            const keyValue = { toString: function() { rhs = 9; return "m"; } };`;
        for (const run of runners("object[keyValue as any] = rhs", setup)) {
            const object = { m: 1 };

            const result = run([object]);

            expect(result).toBe(3);
            expect(object.m).toBe(3);
        }
    });
});
