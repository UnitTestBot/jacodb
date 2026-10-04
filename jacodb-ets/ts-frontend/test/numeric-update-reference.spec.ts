import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { beforeAll, describe, expect, it } from "vitest";
import { EtsFileDto } from "../src/dto/model";
import { serializeEtsFile } from "../src/serialize";
import { executeObjectIr } from "./object-runtime";
import { lower } from "./util";

const updates = [
    ["postIncrement", (operand: string) => `${operand}++`],
    ["postDecrement", (operand: string) => `${operand}--`],
    ["preIncrement", (operand: string) => `++${operand}`],
    ["preDecrement", (operand: string) => `--${operand}`],
] as const;
const shapes = ["dot", "array", "tuple"] as const;
type Shape = typeof shapes[number];
type Effect = "getter" | "numeric" | "key";
type Sample = "number" | "bigint" | "negativeZero" | "throws";

const source = [
    ...shapes.flatMap((shape) => updates.map(([name, update]) => {
        const type = shape === "array" ? "(number | bigint)[]"
            : shape === "tuple" ? "[number | bigint, number | bigint]" : "any";
        const operand = shape === "dot" ? "receiver.m" : "receiver[key]";

        return `export function ${shape}_${name}(input: ${type}, replacement: ${type}, initialKey: any,
            replacementKey: any, prepare: any, state: any): unknown {
            let receiver: ${type} = input;
            let key: any = initialKey;
            prepare(function() { receiver = replacement; key = replacementKey; state.changed = true; });
            const result = ${update(operand)};
            state.receiver = receiver;
            state.key = key;
            return result;
        }`;
    })),
    ...updates.map(([name, update]) => `export function local_${name}(input: any, prepare: any, state: any): unknown {
        let value: any = input;
        prepare(function() { value = 999; });
        const result = ${update("value")};
        state.stored = value;
        return result;
    }`),
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
    shape: Shape,
    effect: Effect,
    sample: Sample,
    throwingEffect?: "getter" | "putKey",
) {
    const events: string[] = [];
    const state: { changed?: boolean; receiver?: unknown; key?: unknown } = {};
    let mutate = () => { throw new Error("mutation callback was not prepared"); };
    const numeric = {
        [Symbol.toPrimitive](hint: string) {
            events.push(`numeric:${hint}`);
            if (effect === "numeric") mutate();
            if (sample === "throws") throw new RangeError("numeric");
            return sample === "bigint" ? 3n : sample === "negativeZero" ? -0 : 3;
        },
    };
    const original: any = shape === "dot" ? { m: numeric, other: 100 } : [numeric, 100];
    const replacement: any = shape === "dot" ? { m: 500, other: 600 } : [500, 600];
    const input = new Proxy(original, {
        get(target, name) {
            events.push(`get:${String(name)}`);
            if (effect === "getter") mutate();
            if (throwingEffect === "getter") throw new RangeError("getter");
            return Reflect.get(target, name);
        },
        set(target, name, value) {
            events.push(`put:${String(name)}`);
            return Reflect.set(target, name, value);
        },
    });
    let conversions = 0;
    const initialKey = {
        [Symbol.toPrimitive](hint: string) {
            events.push(`key:${hint}`);
            conversions++;
            if (effect === "key") mutate();
            if (throwingEffect === "putKey" && conversions === 2) throw new RangeError("key");
            return conversions === 1 ? "0" : "1";
        },
    };
    const replacementKey = "0";
    const prepare = (callback: () => void) => { mutate = callback; };
    const encode = (value: unknown) => value === numeric ? "original numeric object" : value;
    let result: unknown;
    let error: string | undefined;

    try {
        result = execute(input, replacement, initialKey, replacementKey, prepare, state);
    } catch (failure) {
        error = (failure as Error).name;
    }

    return {
        result,
        error,
        events,
        changed: state.changed,
        receiverIsReplacement: state.receiver === replacement,
        keyIsReplacement: state.key === replacementKey,
        original: shape === "dot" ? [encode(original.m), original.other] : original.map(encode),
        replacement: shape === "dot" ? [replacement.m, replacement.other] : [...replacement],
    };
}

const cases = shapes.flatMap((shape) => updates.flatMap(([name]) => {
    const effects: Effect[] = shape === "dot" ? ["getter", "numeric"] : ["getter", "numeric", "key"];
    return effects.map((effect) => [shape, name, effect] as const);
}));

describe("numeric update reference capture", () => {
    it.each(cases)("%s %s retains its reference through %s mutation", (shape, name, effect) => {
        const method = `${shape}_${name}`;

        for (const sample of ["number", "bigint", "negativeZero", "throws"] as const) {
            const expected = observe(native[method], shape, effect, sample);
            const actual = observe((...args) => executeObjectIr(serialized, method, args), shape, effect, sample);

            expect(actual, sample).toEqual(expected);
            expect(actual.replacement, sample).toEqual([500, 600]);
            if (expected.error !== undefined) {
                expect(actual.events.some((event) => event.startsWith("put:")), sample).toBe(false);
                expect(actual.original, sample).toEqual(["original numeric object", 100]);
            } else {
                expect(actual.changed, sample).toBe(true);
                expect(actual.receiverIsReplacement, sample).toBe(true);
                expect(actual.keyIsReplacement, sample).toBe(true);
            }
        }
    });

    it.each(["array", "tuple"] as const)("%s stops before Put if the saved key's second conversion throws", (shape) => {
        const method = `${shape}_postIncrement`;
        const expected = observe(native[method], shape, "numeric", "number", "putKey");
        const actual = observe((...args) => executeObjectIr(serialized, method, args), shape, "numeric", "number", "putKey");

        expect(expected.error).toBe("RangeError");
        expect(expected.events).toEqual(["key:string", "get:0", "numeric:number", "key:string"]);
        expect(actual).toEqual(expected);
    });

    it("stops before ToNumeric and Put when a getter replaces the receiver and throws", () => {
        const method = "dot_postIncrement";
        const expected = observe(native[method], "dot", "getter", "number", "getter");
        const actual = observe((...args) => executeObjectIr(serialized, method, args), "dot", "getter", "number", "getter");

        expect(expected.error).toBe("RangeError");
        expect(expected.events).toEqual(["get:m"]);
        expect(actual).toEqual(expected);
    });

    it.each(updates.map(([name]) => [name] as const))("local %s writes the original binding after coercion replaces its value", (name) => {
        const method = `local_${name}`;
        const observeLocal = (execute: (...args: unknown[]) => unknown) => {
            const state: { stored?: unknown } = {};
            let mutate = () => { throw new Error("mutation callback was not prepared"); };
            const input = { valueOf() { mutate(); return 3; } };
            const prepare = (callback: () => void) => { mutate = callback; };

            return { result: execute(input, prepare, state), stored: state.stored };
        };

        const expected = observeLocal(native[method]);
        const actual = observeLocal((...args) => executeObjectIr(serialized, method, args));

        expect(actual).toEqual(expected);
        expect(actual.stored).toBe(name.endsWith("Increment") ? 4 : 2);
    });
});
