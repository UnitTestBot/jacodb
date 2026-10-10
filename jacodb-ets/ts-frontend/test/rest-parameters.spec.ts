import { describe, expect, it } from "vitest";
import { consumers } from "./array-test-consumers";
import { lower, methodByName } from "./util";
import { serializeEtsFile } from "../src/serialize";

describe("rest parameter caller contract", () => {
    it("keeps isRest through JSON and uses it for calls with zero and multiple raw arguments", () => {
        const source = "function count(...values: number[]): number { return values.length; }";
        const { file, diagnostics } = lower(source);
        const roundTripped = JSON.parse(serializeEtsFile(file));
        const method = methodByName(roundTripped, "count");
        const { ir, native } = consumers(source, "count");

        expect(diagnostics.messages).toEqual([]);
        expect(method.signature.parameters).toHaveLength(1);
        expect(method.signature.parameters[0].isRest).toBe(true);
        for (const args of [[], [1], [1, 2]]) {
            expect(ir(...args)).toBe(native(...args));
            expect(ir(...args)).toBe(args.length);
        }
    });

    it("passes raw arguments through frontend calls to the packing consumer", () => {
        const source = "function count(first: number, ...rest: number[]) { return rest.length; } function invoke() { return count(1, 2, 3); }";
        const { ir, native } = consumers(source, "invoke");

        expect(ir()).toBe(native());
        expect(ir()).toBe(2);
    });

    it("packs remaining raw arguments in order, including missing normal arguments", () => {
        const { ir, native } = consumers("function f(first: number, ...rest: number[]) { return rest; }", "f");

        for (const args of [[], [1], [1, 2], [1, 2, 3]]) {
            expect(ir(...args)).toEqual(native(...args));
        }
    });
});
