import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { beforeAll, describe, expect, it } from "vitest";
import { EtsFileDto } from "../src/dto/model";
import { serializeEtsFile } from "../src/serialize";
import { executeObjectIr } from "./object-runtime";
import { executor } from "./execute";
import { lower } from "./util";

const updates = [
    ["postIncrement", (operand: string) => `${operand}++`],
    ["postDecrement", (operand: string) => `${operand}--`],
    ["preIncrement", (operand: string) => `++${operand}`],
    ["preDecrement", (operand: string) => `--${operand}`],
] as const;
const locations = ["local", "numberLocal", "property", "dot", "array"] as const;

const samples: { name: string; create: (events: string[]) => unknown }[] = [
    { name: "undefined", create: () => undefined },
    { name: "null", create: () => null },
    { name: "boolean", create: () => true },
    { name: "numeric string", create: () => "3" },
    { name: "empty string", create: () => "" },
    { name: "nonnumeric string", create: () => "bad" },
    { name: "negative zero", create: () => -0 },
    { name: "NaN", create: () => NaN },
    { name: "Infinity", create: () => Infinity },
    { name: "negative Infinity", create: () => -Infinity },
    { name: "fraction", create: () => 3.5 },
    { name: "large number", create: () => 2 ** 53 },
    { name: "subnormal", create: () => Number.MIN_VALUE },
    { name: "bigint", create: () => 3n },
    { name: "large bigint", create: () => 9007199254740993n },
    { name: "boxed number", create: () => Object(3) },
    { name: "boxed negative zero", create: () => Object(-0) },
    { name: "boxed bigint", create: () => Object(3n) },
    { name: "symbol", create: () => Symbol("value") },
    { name: "number primitive", create: (events) => ({
        [Symbol.toPrimitive](hint: string) { events.push(`numeric:${hint}`); return 3; },
    }) },
    { name: "bigint primitive", create: (events) => ({
        [Symbol.toPrimitive](hint: string) { events.push(`numeric:${hint}`); return 3n; },
    }) },
    { name: "symbol primitive", create: (events) => ({
        [Symbol.toPrimitive](hint: string) { events.push(`numeric:${hint}`); return Symbol("value"); },
    }) },
    { name: "nonprimitive conversion", create: (events) => ({
        [Symbol.toPrimitive](hint: string) { events.push(`numeric:${hint}`); return {}; },
    }) },
    { name: "throwing conversion", create: (events) => ({
        [Symbol.toPrimitive](hint: string) { events.push(`numeric:${hint}`); throw new RangeError("numeric"); },
    }) },
    { name: "valueOf string", create: (events) => ({
        valueOf() { events.push("valueOf"); return "3"; },
    }) },
    { name: "toString fallback", create: (events) => ({
        valueOf() { events.push("valueOf"); return {}; },
        toString() { events.push("toString"); return "3"; },
    }) },
];

const source = [
    ...locations.flatMap((location) => updates.map(([name, update]) => {
        if (location === "local" || location === "numberLocal") {
            const type = location === "numberLocal" ? "number" : "any";
            return `export function ${location}_${name}(value: ${type}, state: any): unknown {
                const result = ${update("value")};
                state.stored = value;
                return result;
            }`;
        }
        const type = location === "array" ? "(number | bigint)[]" : "any";
        const operand = location === "dot" ? "object.m" : "object[key()]";
        return `export function ${location}_${name}(object: ${type}, key: any): unknown {
            return ${update(operand)};
        }`;
    })),
    `export function capturedPost(state: any): unknown {
        let value: any = { valueOf: function() { value = 9; return 3; } };
        const result = value++;
        state.stored = value;
        return result;
    }`,
    `export function caughtNumeric(value: any, state: any): unknown {
        try {
            const result = value++;
            state.stored = value;
            return result;
        } catch {
            state.original = value;
            return 7;
        }
    }`,
].join("\n");

let serialized: EtsFileDto;
let native: Record<string, (...args: unknown[]) => unknown>;

beforeAll(() => {
    const { file, diagnostics } = lower(source);
    serialized = JSON.parse(serializeEtsFile(file));
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;

    expect(diagnostics.messages).toEqual([]);
    native = runInNewContext(`${javascript}\nexports`, { exports: {} });
});

function observe(
    execute: (...args: unknown[]) => unknown,
    location: typeof locations[number],
    sample: typeof samples[number],
    changingKey = false,
    throwOnPutKey = false,
): { result?: unknown; error?: string; stored?: unknown; unchanged?: boolean; events: string[] } {
    const events: string[] = [];
    const input = sample.create(events);
    const state: { stored?: unknown } = {};
    const data = location === "array" ? [input, 100] : { m: input, other: 100 };
    let conversions = 0;
    const keyObject = {
        [Symbol.toPrimitive](hint: string) {
            events.push(`key:${hint}`);
            conversions++;
            if (throwOnPutKey && conversions === 2) throw new RangeError("put key");
            return location === "array"
                ? (changingKey && conversions === 2 ? "1" : "0")
                : (changingKey && conversions === 2 ? "other" : "m");
        },
    };
    const key = () => { events.push("key expression"); return keyObject; };
    const object = new Proxy(data, {
        get(target, name) { events.push(`get:${String(name)}`); return Reflect.get(target, name); },
        set(target, name, value) { events.push(`put:${String(name)}`); return Reflect.set(target, name, value); },
    });
    const local = location === "local" || location === "numberLocal";
    const stored = () => local ? state.stored : Reflect.get(data,
        location === "array" ? (changingKey ? "1" : "0") : (changingKey ? "other" : "m"));
    const initialStored = stored();

    try {
        const result = local ? execute(input, state) : execute(object, key);
        return { result, stored: stored(), events };
    } catch (error) {
        return { error: (error as Error).name, stored: stored(), unchanged: Object.is(stored(), initialStored), events };
    }
}

describe("numeric update results and effects", () => {
    it.each(locations.flatMap((location) => updates.map(([name]) => [location, name] as const)))(
        "%s %s matches native numeric conversion, result and store", (location, name) => {
            const method = `${location}_${name}`;

            for (const sample of samples) {
                const expected = observe(native[method], location, sample);
                const actual = observe((...args) => executeObjectIr(serialized, method, args), location, sample);

                // A throwing conversion leaves the original object, which belongs to a separate fixture.
                if (expected.error !== undefined) {
                    expect(actual.error, sample.name).toBe(expected.error);
                    expect(actual.events, sample.name).toEqual(expected.events);
                    expect(actual.unchanged, sample.name).toBe(true);
                    expect(actual.events.some((event) => event.startsWith("put:")), sample.name).toBe(false);
                } else {
                    expect(actual.result, sample.name).toBe(expected.result);
                    expect(actual.stored, sample.name).toBe(expected.stored);
                    expect(actual.events, sample.name).toEqual(expected.events);
                }
            }
        },
    );

    it.each(["postIncrement", "postDecrement"])("preserves both key conversions around %s", (name) => {
        const sample = samples.find((candidate) => candidate.name === "number primitive")!;
        const method = `property_${name}`;
        const expected = observe(native[method], "property", sample, true);
        const actual = observe((...args) => executeObjectIr(serialized, method, args), "property", sample, true);

        expect(expected.events).toEqual(["key expression", "key:string", "get:m", "numeric:number", "key:string", "put:other"]);
        expect(expected.result).toBe(3);
        expect(actual).toEqual(expected);
    });

    it("does not Put when the second key conversion throws after ToNumeric", () => {
        const sample = samples.find((candidate) => candidate.name === "number primitive")!;
        const expected = observe(native.property_postIncrement, "property", sample, false, true);
        const actual = observe((...args) => executeObjectIr(serialized, "property_postIncrement", args), "property", sample, false, true);

        expect(expected.error).toBe("RangeError");
        expect(actual.error).toBe(expected.error);
        expect(actual.events).toEqual(["key expression", "key:string", "get:m", "numeric:number", "key:string"]);
    });

    it("keeps a converted local when its valueOf replaces the captured binding", () => {
        for (const execute of [native.capturedPost, (...args: unknown[]) => executeObjectIr(serialized, "capturedPost", args)]) {
            const state: { stored?: unknown } = {};

            expect(execute(state)).toBe(3);
            expect(state.stored).toBe(4);
        }
    });

    it("routes a throwing ToNumeric to catch before updating the local", () => {
        const ir = executor(serialized);

        for (const execute of [native.caughtNumeric, (...args: unknown[]) => ir.call("caughtNumeric", ...args)]) {
            const events: string[] = [];
            const input = { [Symbol.toPrimitive](hint: string) {
                events.push(hint);
                throw new RangeError("numeric");
            } };
            const state: { stored?: unknown; original?: unknown } = {};

            expect(execute(input, state)).toBe(7);
            expect(state.original).toBe(input);
            expect(state.stored).toBeUndefined();
            expect(events).toEqual(["number"]);
        }
    });
});
