import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { beforeAll, describe, expect, it } from "vitest";
import { EtsFileDto } from "../src/dto/model";
import { serializeEtsFile } from "../src/serialize";
import { executeObjectIr } from "./object-runtime";
import { lower } from "./util";

const operations = [
    ["add", "+= 1"],
    ["multiply", "*= 2"],
    ["and", "&&= 7"],
    ["or", "||= 7"],
    ["nullish", "??= 7"],
] as const;
const shapes = ["dot", "array", "tuple", "computed", "nestedDot"] as const;
type Shape = typeof shapes[number];
type Effect = "getter" | "valueOf";
type Sample = "number" | "string" | "negativeZero" | "throws";

const source = [
    ...shapes.flatMap((shape) => operations.map(([name, operation]) => {
        const type = shape === "array" ? "number[]" : shape === "tuple" ? "[number, number]" : "any";
        const operand = shape === "dot" || shape === "nestedDot" ? "receiver.m" : "receiver[key]";
        const action = shape === "nestedDot"
            ? `const update = function() { return ${operand} ${operation}; }; const result = update();`
            : `const result = ${operand} ${operation};`;

        return `export function ${shape}_${name}(input: ${type}, replacement: ${type}, initialKey: any,
            replacementKey: any, prepare: any, state: any): unknown {
            let receiver: ${type} = input;
            let key: any = initialKey;
            prepare(function() { receiver = replacement; key = replacementKey; state.changed = true; });
            ${action}
            state.receiver = receiver;
            state.key = key;
            return result;
        }`;
    })),
    ...operations.slice(0, 2).map(([name, operation]) => `export function local_${name}(input: any, prepare: any, state: any): unknown {
        let value: any = input;
        prepare(function() { value = 999; });
        const result = value ${operation};
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
        // Keep logical assignment native: an older target rewrites it into a
        // second source-reference evaluation, which would invalidate the oracle.
        compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
    }).outputText;

    expect(diagnostics.messages).toEqual([]);
    native = runInNewContext(`${javascript}\nexports`, { exports: {} });
});

function observe(
    execute: (...args: unknown[]) => unknown,
    shape: Shape,
    effect: Effect,
    sample: Sample | { primitive: unknown },
    throwingGetter = false,
) {
    const events: string[] = [];
    const state: { changed?: boolean; receiver?: unknown; key?: unknown } = {};
    let mutate = () => { throw new Error("mutation callback was not prepared"); };
    const boxed = {
        valueOf() {
            events.push("valueOf");
            if (effect === "valueOf") mutate();
            if (sample === "throws") throw new RangeError("valueOf");
            return sample === "string" ? "3" : sample === "negativeZero" ? -0 : 3;
        },
    };
    const oldValue = typeof sample === "object" ? sample.primitive : boxed;
    const array = shape === "array" || shape === "tuple";
    const initialKey = array ? 0 : "m";
    const replacementKey = array ? 1 : "other";
    const originalValues = [oldValue, 100];
    const input: any = array ? [] : {};
    const replacement: any = array ? [500, 600] : { m: 500, other: 600 };

    for (const [index, key] of [initialKey, replacementKey].entries()) {
        Object.defineProperty(input, key, {
            configurable: true,
            enumerable: true,
            get() {
                events.push(`get:${key}`);
                if (effect === "getter") mutate();
                if (throwingGetter) throw new RangeError("getter");
                return originalValues[index];
            },
            set(value) {
                events.push(`put:${key}`);
                originalValues[index] = value;
            },
        });
    }

    const prepare = (callback: () => void) => { mutate = callback; };
    const encode = (value: unknown) => value === boxed ? "original boxed value" : value;
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
        original: originalValues.map(encode),
        replacement: array ? [...replacement] : [replacement.m, replacement.other],
    };
}

const compoundCases = shapes.flatMap((shape) => ["add", "multiply"].flatMap((name) =>
    (["getter", "valueOf"] as const).map((effect) => [shape, name, effect] as const),
));
const logicalCases = shapes.flatMap((shape) => (["and", "or", "nullish"] as const).flatMap((name) =>
    [true, false].map((assign) => [shape, name, assign] as const),
));

describe("read-modify-write assignment reference capture", () => {
    it.each(compoundCases)("%s %s retains its reference through %s mutation", (shape, name, effect) => {
        const method = `${shape}_${name}`;

        for (const sample of ["number", "string", "negativeZero", "throws"] as const) {
            const expected = observe(native[method], shape, effect, sample);
            const actual = observe((...args) => executeObjectIr(serialized, method, args), shape, effect, sample);

            expect(actual, sample).toEqual(expected);
            expect(actual.replacement, sample).toEqual([500, 600]);
            if (sample === "throws") {
                expect(actual.error).toBe("RangeError");
                expect(actual.original).toEqual(["original boxed value", 100]);
                expect(actual.events.some((event) => event.startsWith("put:"))).toBe(false);
            } else {
                expect(actual.changed, sample).toBe(true);
                expect(actual.receiverIsReplacement, sample).toBe(true);
                expect(actual.keyIsReplacement, sample).toBe(true);
            }
        }
    });

    it.each(logicalCases)("%s %s assigns=%s keeps the getter-selected reference", (shape, name, assign) => {
        const method = `${shape}_${name}`;
        const oldValue = name === "and" ? (assign ? 3 : 0)
            : name === "or" ? (assign ? 0 : 3) : (assign ? null : 3);
        const expected = observe(native[method], shape, "getter", { primitive: oldValue });
        const actual = observe((...args) => executeObjectIr(serialized, method, args), shape, "getter", { primitive: oldValue });
        const key = shape === "array" || shape === "tuple" ? 0 : "m";

        expect(actual).toEqual(expected);
        expect(actual.result).toBe(assign ? 7 : oldValue);
        expect(actual.original).toEqual([assign ? 7 : oldValue, 100]);
        expect(actual.replacement).toEqual([500, 600]);
        expect(actual.events).toEqual(assign ? [`get:${key}`, `put:${key}`] : [`get:${key}`]);
        expect(actual.changed).toBe(true);
        expect(actual.receiverIsReplacement).toBe(true);
        expect(actual.keyIsReplacement).toBe(true);
    });

    it.each(shapes)("%s stops before RHS coercion and Put when its getter throws", (shape) => {
        const method = `${shape}_add`;
        const expected = observe(native[method], shape, "getter", "number", true);
        const actual = observe((...args) => executeObjectIr(serialized, method, args), shape, "getter", "number", true);

        expect(actual).toEqual(expected);
        expect(actual.error).toBe("RangeError");
        expect(actual.events).toEqual([shape === "array" || shape === "tuple" ? "get:0" : "get:m"]);
        expect(actual.original).toEqual(["original boxed value", 100]);
        expect(actual.replacement).toEqual([500, 600]);
    });

    it.each(["add", "multiply"])("local %s retains the assigned binding during valueOf", (name) => {
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
        expect(actual.stored).toBe(name === "add" ? 4 : 6);
    });
});
