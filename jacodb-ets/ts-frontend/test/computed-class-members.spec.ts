import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { ClassDto, EtsFileDto, MethodDto } from "../src/dto/model";
import { serializeEtsFile } from "../src/serialize";
import { executor } from "./execute";
import { lower } from "./util";

function classNamed(file: EtsFileDto, name: string): ClassDto {
    const clazz = file.classes.find((candidate) => candidate.signature.name === name);
    if (clazz === undefined) throw new Error(`Missing class ${name}`);
    return clazz;
}

function hasUnsupported(method: MethodDto): boolean {
    return method.body?.cfg.blocks.some((block) => block.stmts.some((statement) =>
        statement._ === "UnsupportedStmt"
        || (statement._ === "AssignStmt" && statement.right._ === "UnsupportedValue"),
    )) ?? false;
}

function nativeExports(source: string): any {
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;
    return new Function("exports", `${javascript}; return exports;`)({});
}

describe("constant computed class members", () => {
    it.each([
        { declaration: "values", name: "values" },
        { declaration: "['values']", name: "values" },
        { declaration: "[`values`]", name: "values" },
        { declaration: "[1e2]", name: "100" },
        { declaration: "[-3]", name: "-3" },
        { declaration: "[-0]", name: "0" },
        { declaration: "[Symbol.iterator]", name: "Symbol.iterator", wellKnownSymbol: "iterator" },
    ])("retains lifecycle and ordinary bodies for $declaration", ({ declaration, name, wellKnownSymbol }) => {
        const source = `
            export class Counter {
                ${declaration}(value: number) { return value - 1; }
                score(value: number) { return value < 0 ? -value : value + 1; }
            }
            export function construct() { return new Counter(); }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file)) as EtsFileDto;
        const clazz = classNamed(roundTrip, "Counter");
        const key = wellKnownSymbol === "iterator" ? Symbol.iterator : name;
        const native = nativeExports(source);
        const run = executor({ ...roundTrip, classes: [clazz] });

        expect(clazz.methods.find((method) => method.signature.name === name)?.signature.wellKnownSymbol)
            .toBe(wellKnownSymbol);
        expect(clazz.methods.every((method) => !hasUnsupported(method))).toBe(true);
        expect(classNamed(roundTrip, "%dflt").methods.every((method) => !hasUnsupported(method))).toBe(true);
        expect(file.exportInfos.some((info) => info.exportName === "Counter")).toBe(true);
        expect(diagnostics.messages).toEqual([]);
        for (const value of [-3, 0, 8]) {
            expect(run.call("score", value)).toBe(native.Counter.prototype.score.call({}, value));
            expect(run.call(name, value)).toBe(native.Counter.prototype[key].call({}, value));
        }
    });

    it("retains symbol and string lookalikes as different member identities", () => {
        const source = `
            export class Keys {
                [Symbol.iterator]() { return 7; }
                ['Symbol.iterator']() { return 9; }
            }
        `;
        const { file } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file)) as EtsFileDto;
        const clazz = classNamed(roundTrip, "Keys");
        const members = clazz.methods.filter((method) => method.signature.name === "Symbol.iterator");
        const native = nativeExports(source);

        expect(members).toHaveLength(2);
        expect(members.map((method) => method.signature.wellKnownSymbol)).toEqual(["iterator", undefined]);
        for (const member of members) {
            const run = executor({ ...roundTrip, classes: [{ ...clazz, methods: [member] }] });
            const key = member.signature.wellKnownSymbol === "iterator" ? Symbol.iterator : "Symbol.iterator";

            expect(run.call("Symbol.iterator")).toBe(native.Keys.prototype[key].call({}));
        }
    });

    it("preserves initializer order and numeric property keys", () => {
        const source = `
            export class Keys {
                static trace = 0;
                static ['first'] = (Keys.trace = Keys.trace * 10 + 1);
                static [1e2] = (Keys.trace = Keys.trace * 10 + 2);
                static [-0] = (Keys.trace = Keys.trace * 10 + 3);
                static result() { return Keys.trace; }
            }
        `;
        const { file } = lower(source);
        const clazz = classNamed(file, "Keys");
        const run = executor({ ...file, classes: [clazz] });

        run.call("%statInit");

        expect(clazz.fields.map((field) => field.signature.name)).toEqual(["trace", "first", "100", "0"]);
        expect(run.call("result")).toBe(nativeExports(source).Keys.result());
        expect(run.call("result")).toBe(123);
    });

    it("keeps the last method for a repeated runtime key", () => {
        const source = `
            export class Keys {
                values() { return 1; }
                ['values']() { return 2; }
                static ['values']() { return 3; }
            }
        `;
        const { file } = lower(source);
        const clazz = classNamed(file, "Keys");
        const methods = clazz.methods.filter((method) => method.signature.name === "values");
        const native = nativeExports(source);

        expect(methods).toHaveLength(2);
        expect(executor({ ...file, classes: [{ ...clazz, methods: [methods[0]] }] }).call("values"))
            .toBe(native.Keys.prototype.values.call({}));
        expect(executor({ ...file, classes: [{ ...clazz, methods: [methods[1]] }] }).call("values"))
            .toBe(native.Keys.values());
    });

    it("shares storage for equal string and numeric keys while retaining both initializers", () => {
        const source = `
            export class Keys {
                static trace = 0;
                static ['100'] = (Keys.trace = Keys.trace * 10 + 1);
                static [1e2] = (Keys.trace = Keys.trace * 10 + 2);
                static result() { return Keys['100'] + Keys.trace; }
            }
        `;
        const { file } = lower(source);
        const clazz = classNamed(file, "Keys");
        const initializers = clazz.methods.find((method) => method.signature.name === "%statInit")!
            .body!.cfg.blocks.flatMap((block) => block.stmts);
        const assignments = initializers.filter((statement) => statement._ === "AssignStmt"
            && statement.left._ === "StaticFieldRef" && statement.left.field.name === "100");

        expect(clazz.fields.filter((field) => field.signature.name === "100")).toHaveLength(1);
        expect(assignments).toHaveLength(2);
        expect(nativeExports(source).Keys.result()).toBe(24);
    });

    it.each([
        "get ['values']() { return 1; }",
        "get values() { return 1; } set values(value: number) {} ['values']() { return 2; }",
        "['values']() { return 2; } get values() { return 1; }",
    ])("keeps computed accessor definitions and collisions explicitly unsupported: %s", (members) => {
        const { file } = lower(`export class Counter { ${members} }`);
        const clazz = classNamed(file, "Counter");
        const constructor = clazz.methods.find((method) => method.signature.name === "constructor")!;

        expect(hasUnsupported(constructor)).toBe(true);
        expect(file.exportInfos.some((info) => info.exportName === "Counter")).toBe(false);
    });

    it.each([
        "[key()]() { return 1; }",
        "[Symbol.iterator]() { return 1; }",
        "['constructor']() { return 1; }",
    ])("leaves unsupported definition-time evaluation explicit: %s", (member) => {
        const source = `
            let effects = 0;
            function key() { effects++; return 'values'; }
            const Symbol = { get iterator() { effects++; return 'values'; } };
            export class Counter { ${member} score(value: number) { return value + 1; } }
            export function count() { return effects; }
        `;
        const { file } = lower(source);
        const clazz = classNamed(file, "Counter");

        expect(clazz.methods.find((method) => method.signature.name === "constructor")?.body?.cfg.blocks
            .flatMap((block) => block.stmts)).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt" }));
        expect(clazz.methods.find((method) => method.signature.name === "score")).toBeDefined();
        expect(clazz.methods.some((method) => method.signature.wellKnownSymbol !== undefined)).toBe(false);
        expect(file.exportInfos.some((info) => info.exportName === "Counter")).toBe(false);
        expect(nativeExports(source).count()).toBe(member.startsWith("['constructor']") ? 0 : 1);
    });
});
