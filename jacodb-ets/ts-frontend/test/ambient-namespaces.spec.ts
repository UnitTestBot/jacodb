import { describe, expect, it } from "vitest";
import * as ts from "typescript";
import { ClassDto, NamespaceDto } from "../src/dto/model";
import { ClassCategory } from "../src/dto/constants";
import { lower, singleBlockStmts } from "./util";

function namespaceClasses(namespaces: readonly NamespaceDto[]): ClassDto[] {
    return namespaces.flatMap((namespace) => [
        ...(namespace.classes ?? []),
        ...namespaceClasses(namespace.namespaces ?? []),
    ]);
}

describe("ambient namespace containers", () => {
    it("keeps repeated type-only namespace declarations without duplicate runtime containers", () => {
        const source = `
            export interface Sink<A> { readonly value: A; }
            export declare namespace Sink {
                export interface Service<A> { readonly value: A; }
            }
            export class Entry {
                classify(value: number): number { return value > 0 ? 1 : 2; }
            }
            export declare namespace Sink {
                export interface WithState<S> { readonly state: S; }
            }
        `;

        const { file, diagnostics } = lower(source);
        const nestedClasses = namespaceClasses(file.namespaces);
        const allClasses = [...file.classes, ...nestedClasses];
        const signatures = allClasses.map((declaration) => JSON.stringify(declaration.signature));

        expect(diagnostics.messages).toEqual([]);
        expect(file.namespaces).toHaveLength(2);
        expect(nestedClasses.map((declaration) => declaration.signature.name)).toEqual(["Service", "WithState"]);
        expect(nestedClasses.every((declaration) => declaration.category === ClassCategory.INTERFACE)).toBe(true);
        expect(new Set(signatures).size).toBe(signatures.length);
        expect(file.classes.find((declaration) => declaration.signature.name === "Entry")?.methods)
            .toEqual(expect.arrayContaining([expect.objectContaining({
                signature: expect.objectContaining({ name: "classify" }),
                body: expect.any(Object),
            })]));

        const emitted = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        expect(emitted).not.toContain("Sink");
    });

    it("inherits ambient context through nested namespaces", () => {
        const source = `
            declare namespace Outer {
                namespace Inner { interface First { value: number; } }
                namespace Inner { interface Second { value: string; } }
            }
        `;

        const { file } = lower(source);
        const nestedClasses = namespaceClasses(file.namespaces);

        expect(nestedClasses.map((declaration) => declaration.signature.name)).toEqual(["First", "Second"]);
        expect(nestedClasses.every((declaration) => declaration.category === ClassCategory.INTERFACE)).toBe(true);
    });

    it("preserves dotted ambient scopes without runtime containers at any level", () => {
        const source = `
            declare namespace Outer.Inner { interface First { value: number; } }
            declare namespace Outer.Inner { interface Second { value: string; } }
        `;

        const { file, diagnostics } = lower(source);
        const nestedClasses = namespaceClasses(file.namespaces);

        expect(diagnostics.messages).toEqual([]);
        expect(file.namespaces.map((namespace) => namespace.signature.name)).toEqual(["Outer", "Outer"]);
        expect(file.namespaces.every((namespace) => namespace.classes?.length === 0)).toBe(true);
        expect(nestedClasses.map((declaration) => declaration.signature.name)).toEqual(["First", "Second"]);
        for (const declaration of nestedClasses) {
            expect(declaration.category).toBe(ClassCategory.INTERFACE);
            expect(declaration.signature.declaringNamespace).toMatchObject({
                name: "Inner",
                declaringNamespace: { name: "Outer" },
            });
        }
    });

    it("treats namespaces in declaration files as ambient without an explicit declare modifier", () => {
        const source = `
            export namespace API { interface First { value: number; } }
            export namespace API { interface Second { value: string; } }
        `;

        const { file } = lower(source, "proj", "types.d.ts");
        const nestedClasses = namespaceClasses(file.namespaces);

        expect(nestedClasses.map((declaration) => declaration.signature.name)).toEqual(["First", "Second"]);
        expect(nestedClasses.every((declaration) => declaration.category === ClassCategory.INTERFACE)).toBe(true);
    });

    it("retains ambient value declarations without inventing a default initializer", () => {
        const source = `
            declare namespace API {
                const version: number;
                function compute(value: number): number;
                class Entry { method(value: number): number; }
            }
        `;

        const { file } = lower(source);
        const nestedClasses = namespaceClasses(file.namespaces);
        const container = nestedClasses.find((declaration) => declaration.signature.name === "%dflt");
        const entry = nestedClasses.find((declaration) => declaration.signature.name === "Entry");

        expect(container?.fields.map((field) => field.signature.name)).toEqual(["version"]);
        expect(container?.methods.map((method) => method.signature.name)).toEqual(["compute"]);
        expect(container?.methods.every((method) => method.body === undefined)).toBe(true);
        expect(entry?.methods.find((method) => method.signature.name === "method")?.body).toBeUndefined();
    });

    it("preserves executable and empty runtime namespace initializers", () => {
        const source = `
            namespace Runtime { export const value = 42; }
            namespace Empty {}
        `;

        const { file } = lower(source);
        const runtime = file.namespaces.find((namespace) => namespace.signature.name === "Runtime");
        const empty = file.namespaces.find((namespace) => namespace.signature.name === "Empty");
        const runtimeInitializer = runtime?.classes?.find((declaration) => declaration.signature.name === "%dflt")
            ?.methods.find((method) => method.signature.name === "%dflt");
        const emptyInitializer = empty?.classes?.find((declaration) => declaration.signature.name === "%dflt")
            ?.methods.find((method) => method.signature.name === "%dflt");

        expect(runtimeInitializer).toBeDefined();
        expect(emptyInitializer).toBeDefined();
        expect(singleBlockStmts(runtimeInitializer!)).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: { _: "Constant", value: "42", type: { _: "NumberType" } },
        }));
        expect(singleBlockStmts(emptyInitializer!).map((statement) => statement._))
            .toEqual(["AssignStmt", "ReturnVoidStmt"]);
    });
});
