import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { executeObjectIr } from "./object-runtime";
import { compile, lower, methodByName } from "./util";

function nodeResult(source: string, expression: string): unknown {
    const javascript = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;
    return runInNewContext(`${javascript}\n${expression}`, { exports: {} });
}

describe("object getter descriptors", () => {
    it("preserves the getter body and invokes it on a property read", () => {
        const source = `export function value(): number { return ({ get x() { return 1; } }).x; }`;
        const compiled = compile(source);
        expect(compiled.program.getSemanticDiagnostics(compiled.sourceFile)).toEqual([]);

        const { file, diagnostics } = lower(source);
        const stmts = methodByName(file, "value").body!.cfg.blocks.flatMap((block) => block.stmts);
        const descriptor = stmts.find((stmt) => stmt._ === "DefineAccessorStmt");

        expect(descriptor).toMatchObject({
            _: "DefineAccessorStmt",
            key: { _: "Constant", value: "x" },
            getter: { _: "Local", type: { _: "FunctionType" } },
        });
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef")).toBe(true);
        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value")).toBe(nodeResult(source, `exports.value()`));
    });

    it("keeps captured bindings and calls the getter with its dynamic receiver", () => {
        const source = `
            export function value(seed: number): number {
                const object = { y: 2, get x() { seed++; return seed + this.y; } };
                const first = object.x;
                return first * 10 + object.x;
            }
        `;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value", [3])).toBe(67);
        expect(nodeResult(source, `exports.value(3)`)).toBe(67);
    });

    it("evaluates a computed getter key at creation but runs its body only on Get", () => {
        const source = `
            export function value(key: string): number {
                let reads = 0;
                const object = { get [key]() { reads++; return 4; } };
                return reads * 100 + object[key] * 10 + reads;
            }
        `;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value", ["x"])).toBe(41);
        expect(nodeResult(source, `exports.value("x")`)).toBe(41);
    });

    it("defines a later data property without invoking or retaining the replaced getter", () => {
        const source = `
            export function value(): number {
                let reads = 0;
                const object = { get x() { reads++; return 4; }, ["x"]: 7 };
                return object.x * 10 + reads;
            }
        `;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value")).toBe(70);
        expect(nodeResult(source, `exports.value()`)).toBe(70);
    });

    it("runs a getter while copying spread and creates a data property on the target", () => {
        const source = `
            export function value(): number {
                let reads = 0;
                const original = { get x() { reads++; return 4; } };
                const copied = { ...original };
                return copied.x * 100 + copied.x * 10 + reads;
            }
        `;
        const { file, diagnostics } = lower(source);

        expect(diagnostics.messages).toEqual([]);
        expect(executeObjectIr(JSON.parse(JSON.stringify(file)), "value")).toBe(441);
        expect(nodeResult(source, `exports.value()`)).toBe(441);
    });

    it("keeps object setters explicitly unsupported", () => {
        const { file, diagnostics } = lower(`export function value() { return { set x(value: number) {} }; }`);
        const stmts = methodByName(file, "value").body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(true);
        expect(diagnostics.messages.some((message) => message.includes("SetAccessor"))).toBe(true);
    });
});

describe("object method materialization boundary", () => {
    it.each([
        ["direct and detached receiver", `export function run() {
            const object = { method() { return this; } };
            const detached = object.method;
            return [object.method(), detached()];
        }`],
        ["captured local", `export function run(seed: number) {
            return { method(value = seed) { return value + seed; } };
        }`],
        ["fresh identity", `export function run() {
            const make = () => ({ method() { return 1; } });
            return make().method === make().method;
        }`],
        ["method after data property", `export function run() {
            return { method: 1, method() { return 2; } };
        }`],
        ["data property after method", `export function run() {
            return { method() { return 1; }, method: 2 };
        }`],
        ["method after spread", `export function run(source: object) {
            return { ...source, method() { return 1; } };
        }`],
        ["spread after method", `export function run(source: object) {
            return { method() { return 1; }, ...source };
        }`],
    ])("rejects %s before object allocation or member effects", (_scenario, source) => {
        const { file, diagnostics } = lower(source);
        const stmts = file.classes.flatMap((clazz) => clazz.methods)
            .flatMap((method) => method.body?.cfg.blocks.flatMap((block) => block.stmts) ?? []);

        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: expect.objectContaining({ _: "UnsupportedValue" }),
        }));
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "NewExpr")).toBe(false);
        expect(stmts.some((stmt) => stmt._ === "DefineDataPropertyStmt" || stmt._ === "CopyDataPropertiesStmt")).toBe(false);
        expect(diagnostics.messages).toEqual(expect.arrayContaining([
            expect.stringContaining("object literal method needs a per-evaluation callable own property"),
        ]));
        expect(file.classes.some((clazz) => clazz.signature.name.startsWith("%AC"))).toBe(false);
    });

    it("keeps an ordinary function-valued data property on the supported store path", () => {
        const { file, diagnostics } = lower(`export function run(seed: number) {
            return { method: function (value: number) { return value + seed; } };
        }`);
        const stmts = methodByName(file, "run").body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toEqual([]);
        expect(stmts).toContainEqual(expect.objectContaining({
            _: "DefineDataPropertyStmt",
            key: expect.objectContaining({ _: "Constant", value: "method" }),
            value: expect.objectContaining({ _: "Local", type: expect.objectContaining({ _: "FunctionType" }) }),
        }));
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(false);
    });
});
