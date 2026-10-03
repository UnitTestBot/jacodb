/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import { execFileSync } from "node:child_process";
import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { Modifier } from "../src/dto/constants";
import { StmtDto } from "../src/dto/stmts";
import { defaultMethod, lower } from "./util";

function moduleStatements(source: string): { stmts: StmtDto[]; file: ReturnType<typeof lower>["file"] } {
    const { file, diagnostics } = lower(source);
    expect(diagnostics.messages).toEqual([]);

    return {
        file,
        stmts: defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts),
    };
}

describe("export default expressions", () => {
    it("keeps export equals explicitly unsupported across metadata, storage, and statements", () => {
        const { file, diagnostics } = lower("const value = 1; export = value;");
        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(file.exportInfos).toContainEqual(expect.objectContaining({ exportName: "value" }));
        expect(defaultClass.fields.some((field) => field.signature.name === "default")).toBe(false);
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(diagnostics.messages).toContainEqual(expect.stringContaining("export ="));
    });

    it.each([
        "this",
        "(this)",
        "() => this",
    ])("reports module-level lexical this in %s", (expression) => {
        const source = `export default ${expression};`;
        const { file, diagnostics } = lower(source);
        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toContainEqual(expect.stringContaining("module-level lexical this"));
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(defaultClass.fields.some((field) => field.signature.name === "default")).toBe(false);

        const moduleUrl = `data:text/javascript,${encodeURIComponent(source)}`;
        const exportedValue = expression.includes("=>") ? "module.default()" : "module.default";
        const oracle = execFileSync(process.execPath, [
            "--input-type=module",
            "-e",
            `const module = await import(${JSON.stringify(moduleUrl)}); process.stdout.write(String(${exportedValue} === undefined));`,
        ], { encoding: "utf8" });
        expect(oracle).toBe("true");
    });

    it("keeps this inside an object method bound to its ordinary receiver", () => {
        const source = `export default ({ read() { return this; } });`;
        const { file, stmts } = moduleStatements(source);

        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            left: expect.objectContaining({ _: "StaticFieldRef", field: expect.objectContaining({ name: "default" }) }),
        }));
        expect(file.exportInfos).toContainEqual(expect.objectContaining({ exportName: "default" }));

        const moduleUrl = `data:text/javascript,${encodeURIComponent(source)}`;
        const oracle = execFileSync(process.execPath, [
            "--input-type=module",
            "-e",
            `const module = await import(${JSON.stringify(moduleUrl)}); process.stdout.write(String(module.default.read() === module.default));`,
        ], { encoding: "utf8" });
        expect(oracle).toBe("true");
    });

    it("keeps this inside a nested ordinary function separate from module this", () => {
        const source = `export default () => { function inner() { return this; } return 1; };`;
        const { file, stmts } = moduleStatements(source);

        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            left: expect.objectContaining({ _: "StaticFieldRef", field: expect.objectContaining({ name: "default" }) }),
        }));
        expect(file.exportInfos).toContainEqual(expect.objectContaining({ exportName: "default" }));

        const moduleUrl = `data:text/javascript,${encodeURIComponent(source)}`;
        const oracle = execFileSync(process.execPath, [
            "--input-type=module",
            "-e",
            `const module = await import(${JSON.stringify(moduleUrl)}); process.stdout.write(String(module.default() === 1));`,
        ], { encoding: "utf8" });
        expect(oracle).toBe("true");
    });

    it.each([
        "({ [this]() { return 1; } })",
        "({ [this]: 1 })",
        "({ [key]() { return 1; } })",
        "({ [key]: 1 })",
    ])("rejects a computed object key in %s", (expression) => {
        const { file, diagnostics } = lower(`
            const key = "name";
            export default ${expression};
        `);

        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toContainEqual(expect.stringContaining("computed object key"));
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(defaultClass.fields.some((field) => field.signature.name === "default")).toBe(false);
    });

    it("matches the ES-module evaluation of a computed method name", () => {
        const source = `export default ({ [this]() { return 1; } });`;
        const moduleUrl = `data:text/javascript,${encodeURIComponent(source)}`;
        const oracle = execFileSync(process.execPath, [
            "--input-type=module",
            "-e",
            `const module = await import(${JSON.stringify(moduleUrl)}); process.stdout.write(String(module.default.undefined() === 1));`,
        ], { encoding: "utf8" });

        expect(oracle).toBe("true");
    });

    it("evaluates a call at its source position and stores the result in the default binding", () => {
        const source = `
            let count = 0;
            function sideEffect(): number { count++; return count; }
            export default sideEffect();
            count = 10;
            export const after = count;
        `;
        const { file, stmts } = moduleStatements(source);

        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        const calls = stmts.filter((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticCallExpr" && stmt.right.method.name === "sideEffect");
        const exportWrite = stmts.find((stmt) => stmt._ === "AssignStmt"
            && stmt.left._ === "StaticFieldRef" && stmt.left.field.name === "default");
        const laterWrite = stmts.find((stmt) => stmt._ === "AssignStmt"
            && stmt.left._ === "StaticFieldRef" && stmt.left.field.name === "count"
            && stmt.right._ === "Constant" && stmt.right.value === "10");

        expect(defaultClass.fields).toContainEqual(expect.objectContaining({
            signature: expect.objectContaining({ name: "default", type: { _: "NumberType" } }),
        }));
        expect(calls).toHaveLength(1);
        expect(exportWrite).toMatchObject({ right: (calls[0] as Extract<StmtDto, { _: "AssignStmt" }>).left });
        expect(stmts.indexOf(calls[0])).toBeLessThan(stmts.indexOf(exportWrite!));
        expect(stmts.indexOf(exportWrite!)).toBeLessThan(stmts.indexOf(laterWrite!));
        expect(file.exportInfos).toContainEqual({
            exportName: "default",
            exportType: 3,
            modifiers: Modifier.DEFAULT,
            isTypeOnly: false,
        });

        const output = ts.transpileModule(source, {
            compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
        }).outputText;
        const exports: Record<string, unknown> = {};
        runInNewContext(output, { exports });
        expect(exports).toMatchObject({ default: 1, after: 10 });
    });

    it("captures an identifier's value before a later assignment", () => {
        const source = `
            let value = 1;
            export default value;
            value = 2;
        `;
        const { file, stmts } = moduleStatements(source);

        const valueRead = stmts.find((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticFieldRef" && stmt.right.field.name === "value");
        const exportWrite = stmts.find((stmt) => stmt._ === "AssignStmt"
            && stmt.left._ === "StaticFieldRef" && stmt.left.field.name === "default");
        const laterWrite = stmts.find((stmt) => stmt._ === "AssignStmt"
            && stmt.left._ === "StaticFieldRef" && stmt.left.field.name === "value"
            && stmt.right._ === "Constant" && stmt.right.value === "2");

        expect(valueRead).toBeDefined();
        expect(exportWrite).toMatchObject({ right: (valueRead as Extract<StmtDto, { _: "AssignStmt" }>).left });
        expect(stmts.indexOf(valueRead!)).toBeLessThan(stmts.indexOf(exportWrite!));
        expect(stmts.indexOf(exportWrite!)).toBeLessThan(stmts.indexOf(laterWrite!));
        expect(file.exportInfos).toContainEqual({
            exportName: "default",
            nameBeforeAs: "value",
            exportType: 3,
            modifiers: Modifier.DEFAULT,
            isTypeOnly: false,
        });

        const output = ts.transpileModule(source, {
            compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
        }).outputText;
        const exports: Record<string, unknown> = {};
        runInNewContext(output, { exports });
        expect(exports.default).toBe(1);
    });

    it.each([
        "new Box()",
        "Box.count",
        "Box.callback",
        "Box.getCount()",
    ])("keeps supported class uses in default expression %s", (expression) => {
        const source = `
            class Box {
                static count = 1;
                static callback = (): number => 1;
                static getCount(): number { return this.count; }
            }
            export default ${expression};
        `;
        const { file, stmts } = moduleStatements(source);

        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            left: expect.objectContaining({ _: "StaticFieldRef", field: expect.objectContaining({ name: "default" }) }),
        }));
        if (expression === "Box.count" || expression === "Box.callback") {
            expect(stmts).toContainEqual(expect.objectContaining({
                _: "AssignStmt",
                right: expect.objectContaining({
                    _: "StaticFieldRef",
                    field: expect.objectContaining({ name: expression.split(".")[1] }),
                }),
            }));
        }
        if (expression.includes("getCount")) {
            expect(stmts).toContainEqual(expect.objectContaining({
                _: "AssignStmt",
                right: expect.objectContaining({
                    _: "StaticCallExpr",
                    method: expect.objectContaining({ name: "getCount" }),
                }),
            }));
        }
        expect(file.exportInfos).toContainEqual(expect.objectContaining({ exportName: "default" }));

        const output = ts.transpileModule(source, {
            compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
        }).outputText;
        const exports: Record<string, unknown> = {};
        runInNewContext(output, { exports });
        if (expression === "new Box()") {
            expect(typeof exports.default).toBe("object");
        } else if (expression === "Box.callback") {
            expect(typeof exports.default).toBe("function");
        } else {
            expect(exports.default).toBe(1);
        }
    });

    it("keeps a class reached through a namespace as a static receiver", () => {
        const { stmts } = moduleStatements(`
            namespace N {
                export class Box { static count = 1; }
            }
            export default N.Box.count;
        `);

        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: expect.objectContaining({
                _: "StaticFieldRef",
                field: expect.objectContaining({ name: "count" }),
            }),
        }));
    });

    it("constructs a class reached through a namespace and stores the instance", () => {
        const source = `
            namespace N {
                export class Box {
                    constructor(public value: number) {}
                }
            }
            export default new N.Box(7);
        `;
        const { stmts } = moduleStatements(source);

        expect(stmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: expect.objectContaining({
                _: "NewExpr",
                classType: expect.objectContaining({
                    signature: expect.objectContaining({ name: "Box", declaringNamespace: expect.objectContaining({ name: "N" }) }),
                }),
            }),
        }));

        const output = ts.transpileModule(source, {
            compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
        }).outputText;
        const exports: Record<string, unknown> = {};
        runInNewContext(output, { exports });
        expect(exports.default).toMatchObject({ value: 7 });
    });

    it("checks constructor arguments for unmaterialized declaration values", () => {
        const { file, diagnostics } = lower(`
            namespace N { export class Box { constructor(value: unknown) {} } }
            function factory(): number { return 1; }
            export default new N.Box(factory);
        `);

        expect(diagnostics.messages).toContainEqual(expect.stringContaining("has no EtsIR value reference"));
        expect(file.classes.find((clazz) => clazz.signature.name === "%dflt")!.fields
            .some((field) => field.signature.name === "default")).toBe(false);
    });

    it.each([
        "(Box).count",
        "(Box as typeof Box).count",
        "Box!.count",
        "(Box).getCount()",
        "(Box as typeof Box).getCount()",
        "Box?.count",
        "Box?.getCount()",
        "Box.getCount",
        "Box.getCount?.()",
        "Box.callback()",
        "Box.callback?.()",
        "Box.value",
        "Box.prototype",
        "N?.Box.count",
        "N.x",
        "N.f()",
    ])("reports an unmaterialized receiver in %s", (expression) => {
        const { file, diagnostics } = lower(`
            class Box {
                static count = 1;
                static callback = (): number => 1;
                static getCount(): number { return this.count; }
                static get value(): number { return 1; }
            }
            namespace N {
                export const x = 1;
                export function f(): number { return x; }
                export class Box { static count = 1; }
            }
            export default ${expression};
        `);

        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toContainEqual(expect.stringContaining("has no EtsIR value reference"));
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(defaultClass.fields.some((field) => field.signature.name === "default")).toBe(false);
    });

    it("marks a bare declaration reference unsupported until EtsIR can represent its value", () => {
        const { file, diagnostics } = lower(`
            function factory(): number { return 1; }
            export default factory;
        `);

        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);
        expect(diagnostics.messages).toContainEqual(expect.stringContaining("has no EtsIR value reference"));
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(file.classes.find((clazz) => clazz.signature.name === "%dflt")!.fields
            .some((field) => field.signature.name === "default")).toBe(false);
    });

    it("rejects a declaration value captured by an exported closure", () => {
        const { file, diagnostics } = lower(`
            function factory(): number { return 1; }
            export default () => factory;
        `);

        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toContainEqual(expect.stringContaining("has no EtsIR value reference"));
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(file.classes.find((clazz) => clazz.signature.name === "%dflt")!.fields
            .some((field) => field.signature.name === "default")).toBe(false);
    });

    it("keeps direct calls inside an exported closure supported", () => {
        const { file } = moduleStatements(`
            function factory(): number { return 1; }
            export default () => factory();
        `);

        const methods = file.classes.flatMap((clazz) => clazz.methods);
        expect(methods.flatMap((method) => method.body?.cfg.blocks.flatMap((block) => block.stmts) ?? []))
            .toContainEqual(expect.objectContaining({
                _: "AssignStmt",
                right: expect.objectContaining({ _: "StaticCallExpr", method: expect.objectContaining({ name: "factory" }) }),
            }));
        expect(file.exportInfos).toContainEqual(expect.objectContaining({ exportName: "default" }));
    });

    it("rejects optional static property access that lowers through an uninitialized class value", () => {
        const { file, diagnostics } = lower(`
            class Box { static count = 1; }
            export default Box?.count;
        `);

        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toContainEqual(expect.stringContaining("has no EtsIR value reference"));
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(file.classes.find((clazz) => clazz.signature.name === "%dflt")!.fields
            .some((field) => field.signature.name === "default")).toBe(false);
    });

    it.each([
        "(factory)",
        "factory as () => number",
        "<() => number>factory",
        "factory!",
        "factory satisfies () => number",
    ])("keeps wrapped function reference %s unsupported", (expression) => {
        const { file, diagnostics } = lower(`
            function factory(): number { return 1; }
            export default ${expression};
        `);

        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toContainEqual(expect.stringContaining("has no EtsIR value reference"));
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(file.classes.find((clazz) => clazz.signature.name === "%dflt")!.fields
            .some((field) => field.signature.name === "default")).toBe(false);
    });

    it.each([
        "(0, factory)",
        "[factory]",
        "({ factory })",
        "true ? factory : factory",
    ])("reports an unsupported declaration value inside %s", (expression) => {
        const { file, diagnostics } = lower(`
            function factory(): number { return 1; }
            export default ${expression};
        `);

        const stmts = defaultMethod(file).body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(diagnostics.messages).toContainEqual(expect.stringContaining("has no EtsIR value reference"));
        expect(stmts).toContainEqual(expect.objectContaining({ _: "UnsupportedStmt", kindName: "ExportAssignment" }));
        expect(stmts.some((stmt) => stmt._ === "AssignStmt"
            && stmt.left._ === "StaticFieldRef" && stmt.left.field.name === "default")).toBe(false);
    });
});
