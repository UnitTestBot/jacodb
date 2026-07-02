import { describe, expect, it } from "vitest";
import { Modifier } from "../src/dto/constants";
import { lower } from "./util";

describe("import infos", () => {
    it("builds infos for every import flavor", () => {
        const { file } = lower(`
            import def from "./mod-default";
            import { a, b as c } from "./mod-named";
            import * as ns from "./mod-namespace";
            import "./mod-sideeffect";
        `);
        expect(file.importInfos).toEqual([
            { importName: "def", importType: "Identifier", importFrom: "./mod-default", modifiers: 0 },
            { importName: "a", importType: "NamedImports", importFrom: "./mod-named", modifiers: 0 },
            { importName: "c", importType: "NamedImports", importFrom: "./mod-named", nameBeforeAs: "b", modifiers: 0 },
            { importName: "ns", importType: "NamespaceImport", importFrom: "./mod-namespace", modifiers: 0 },
            { importName: "", importType: "", importFrom: "./mod-sideeffect", modifiers: 0 },
        ]);
    });

    it("handles combined default + named imports", () => {
        const { file } = lower(`import def, { x } from "./m";`);
        expect(file.importInfos).toEqual([
            { importName: "def", importType: "Identifier", importFrom: "./m", modifiers: 0 },
            { importName: "x", importType: "NamedImports", importFrom: "./m", modifiers: 0 },
        ]);
    });
});

describe("export infos", () => {
    it("builds infos for exported declarations", () => {
        const { file } = lower(`
            export class C {}
            export enum E { A }
            export function f(): void {}
            export interface I {}
            export type T = number;
            export const v = 1;
            export namespace N {}
        `);
        const byName = Object.fromEntries(file.exportInfos.map((e) => [e.exportName, e]));
        expect(byName["C"].exportType).toBe(1); // CLASS
        expect(byName["E"].exportType).toBe(1); // CLASS (enum)
        expect(byName["f"].exportType).toBe(2); // METHOD
        expect(byName["I"].exportType).toBe(4); // TYPE
        expect(byName["T"].exportType).toBe(4); // TYPE
        expect(byName["v"].exportType).toBe(3); // LOCAL
        expect(byName["N"].exportType).toBe(0); // NAMESPACE
        expect(byName["C"].modifiers & Modifier.EXPORT).toBe(Modifier.EXPORT);
    });

    it("builds infos for re-exports with nameBeforeAs and exportFrom", () => {
        const { file } = lower(`
            export { X, Y as Z } from "./other";
            export * from "./star";
            export * as bundle from "./bundle";
        `);
        expect(file.exportInfos).toEqual([
            { exportName: "X", exportType: 9, modifiers: 0, exportFrom: "./other" },
            { exportName: "Z", exportType: 9, nameBeforeAs: "Y", modifiers: 0, exportFrom: "./other" },
            { exportName: "*", exportType: 9, modifiers: 0, exportFrom: "./star" },
            { exportName: "bundle", exportType: 0, nameBeforeAs: "*", modifiers: 0, exportFrom: "./bundle" },
        ]);
    });

    it("resolves local re-exports through the checker", () => {
        const { file } = lower(`
            class Local {}
            function helper(): void {}
            export { Local, helper };
        `);
        const byName = Object.fromEntries(file.exportInfos.map((e) => [e.exportName, e]));
        expect(byName["Local"].exportType).toBe(1);
        expect(byName["helper"].exportType).toBe(2);
    });

    it("handles export default", () => {
        const { file } = lower(`
            class Main {}
            export default Main;
        `);
        expect(file.exportInfos).toEqual([
            { exportName: "Main", exportType: 1, modifiers: Modifier.DEFAULT },
        ]);
    });

    it("handles export default declarations", () => {
        const { file } = lower(`export default class Widget {}`);
        const widget = file.exportInfos.find((e) => e.exportName === "Widget");
        expect(widget).toBeDefined();
        expect(widget!.exportType).toBe(1);
        expect(widget!.modifiers & Modifier.DEFAULT).toBe(Modifier.DEFAULT);
    });
});
