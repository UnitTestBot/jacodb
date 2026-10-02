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
        const { file, stmts } = moduleStatements(`
            let value = 1;
            export default value;
            value = 2;
        `);

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
});
