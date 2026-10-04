import { describe, expect, it } from "vitest";
import { consumers } from "./array-test-consumers";
import { lower } from "./util";
import { serializeEtsFile } from "../src/serialize";

describe("default parameter observable behavior", () => {
    it("initializes destructured parameter bindings before later defaults", () => {
        const { ir, native } = consumers("function f([a, ...tail] = [1, 2], b = tail.length) { return [a, b]; }", "f");

        for (const args of [[], [undefined], [[0, 7, 8]]]) {
            expect(ir(...args)).toEqual(native(...args));
        }
    });

    it("records ordinary closure captures referenced only by a parameter default", () => {
        const source = "function make(seed: number) { return function(value = seed) { return value; }; }";
        const { file, diagnostics } = lower(source);
        const roundTripped = JSON.parse(serializeEtsFile(file));
        const nested = roundTripped.classes.flatMap((clazz) => clazz.methods)
            .find((method) => method.signature.name.startsWith("%AM"));
        const environment = nested!.body!.locals.find((local) => local.type._ === "LexicalEnvType");
        const statements = nested!.body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toEqual([]);
        expect(environment.type.closures.some((capture) => capture.name === "seed")).toBe(true);
        expect(statements.some((statement) => statement._ === "AssignStmt"
            && statement.right._ === "ClosureFieldRef" && statement.right.fieldName === "seed")).toBe(true);
        expect(statements.some((statement) => statement._ === "IfStmt")).toBe(true);
    });

    it("keeps closures over uninitialized parameters explicitly unsupported", () => {
        const { diagnostics } = lower("function f(a = function() { return a; }) { return a; }");

        expect(diagnostics.messages.some((message) => message.includes("uninitialized parameter"))).toBe(true);
    });

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

});
