import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { Modifier, NAMED_FUNCTION_REFERENCE_PREFIX } from "../src/dto/constants";
import { MethodDto } from "../src/dto/model";
import { AssignStmtDto, StmtDto } from "../src/dto/stmts";
import { defaultMethod, lower, lowerProject, methodByName } from "./util";

function statements(method: MethodDto): StmtDto[] {
    return method.body!.cfg.blocks.flatMap((block) => block.stmts);
}

function nativeResult(source: string, expression: string): unknown {
    const js = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;
    return new Function("exports", `${js}\nreturn ${expression};`)({});
}

describe("named function bindings", () => {
    it("hoists one mutable binding pointing at the original method before source statements", () => {
        const source = `
            const alias = named;
            const before = named(2);
            function named(value: number): number { return value + 1; }
            class Example { check(): boolean { return typeof named === "function" && named === named; } }
        `;
        const { file, diagnostics } = lower(source);
        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        const binding = defaultClass.fields.find((field) => field.signature.name === "named")!;
        const stmts = statements(defaultMethod(file));
        const initialization = stmts.find((stmt): stmt is AssignStmtDto => stmt._ === "AssignStmt"
            && stmt.left._ === "StaticFieldRef" && stmt.left.field.name === "named")!;
        const readIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticFieldRef" && stmt.right.field.name === "named");

        expect(binding.modifiers & Modifier.STATIC).toBe(Modifier.STATIC);
        expect(binding.modifiers & Modifier.CONST).toBe(0);
        expect(initialization.right).toEqual({
            _: "Local", name: `${NAMED_FUNCTION_REFERENCE_PREFIX}named`,
            type: { _: "FunctionType", signature: methodByName(file, "named").signature },
        });
        expect(stmts.indexOf(initialization)).toBeLessThan(readIndex);
        expect(defaultClass.methods.map((method) => method.signature.name)).toEqual(["%dflt", "named"]);
        expect(statements(methodByName(file, "check")).filter((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticFieldRef" && stmt.right.field.name === "named")).toHaveLength(3);
        expect(diagnostics.messages).toEqual([]);
        expect(nativeResult(source, "[before, alias === named, new Example().check()]")).toEqual([3, true, true]);
    });

    it("uses the same binding for aliases, reassignment and direct calls, selected before arguments", () => {
        const source = `
            function original(value: number): number { return value + 1; }
            function replacement(value: number): number { return value + 10; }
            function check(): number {
                const alias = original;
                const selected = original((original = replacement, 2));
                return selected * 100 + alias(2) * 10 + original(2);
            }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = statements(methodByName(file, "check"));
        const reads = stmts.filter((stmt): stmt is AssignStmtDto => stmt._ === "AssignStmt"
            && stmt.right._ === "StaticFieldRef" && stmt.right.field.name === "original");
        const mutationIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.left._ === "StaticFieldRef" && stmt.left.field.name === "original");
        const calls = stmts.filter((stmt): stmt is AssignStmtDto => stmt._ === "AssignStmt"
            && stmt.right._ === "PtrCallExpr");

        expect(reads).toHaveLength(3);
        expect(stmts.indexOf(reads[1])).toBeLessThan(mutationIndex);
        expect(stmts.indexOf(calls[0])).toBeGreaterThan(mutationIndex);
        expect(calls[0].right).toMatchObject({ ptr: reads[1].left });
        expect(calls[1].right).toMatchObject({ ptr: { name: "alias" } });
        expect(calls[2].right).toMatchObject({ ptr: reads[2].left });
        expect(diagnostics.messages).toEqual([]);
        expect(nativeResult(source, "check()")).toBe(342);
    });

    it("keeps parameter and block bindings separate from the scope function", () => {
        const source = `
            function named(): number { return 1; }
            function parameter(named: () => number): number { return named(); }
            function block(): number {
                const before = named();
                { const named = () => 2; return before + named(); }
            }
        `;
        const { file, diagnostics } = lower(source);
        const parameter = statements(methodByName(file, "parameter"));
        const block = statements(methodByName(file, "block"));

        expect(parameter).toContainEqual(expect.objectContaining({
            right: expect.objectContaining({ _: "PtrCallExpr", ptr: expect.objectContaining({ name: "named" }) }),
        }));
        expect(parameter.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticFieldRef")).toBe(false);
        expect(block.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticFieldRef"
            && stmt.right.field.name === "named")).toHaveLength(1);
        expect(block).toContainEqual(expect.objectContaining({
            right: expect.objectContaining({ _: "PtrCallExpr", ptr: expect.objectContaining({ name: "named" }) }),
        }));
        expect(diagnostics.messages).toEqual([]);
        expect(nativeResult(source, "[parameter(() => 5), block()]")).toEqual([5, 3]);
    });

    it("preserves live module state and emits a captured nested closure body once", () => {
        const source = `
            let state = 1;
            function factory(seed: number): () => number {
                let captured = seed;
                return () => { captured++; return captured + state; };
            }
            const alias = factory;
            state = 10;
        `;
        const { file, diagnostics } = lower(source);
        const methods = file.classes.flatMap((clazz) => clazz.methods);
        const closure = methodByName(file, "%AM0$factory");

        expect(methods.filter((method) => method.signature.name === "factory")).toHaveLength(1);
        expect(methods.filter((method) => method.signature.name.startsWith("%AM"))).toHaveLength(1);
        expect(statements(closure)).toContainEqual(expect.objectContaining({
            right: expect.objectContaining({ _: "StaticFieldRef", field: expect.objectContaining({ name: "state" }) }),
        }));
        expect(statements(closure).some((stmt) => stmt._ === "AssignStmt" && stmt.left._ === "ClosureFieldRef")).toBe(true);
        expect(diagnostics.messages).toEqual([]);
        expect(nativeResult(source, "alias(2)()")).toBe(13);
    });

    it("qualifies imported same-named functions by their original file", () => {
        const sources = {
            "/first.ts": "export function named(): number { return 1; }",
            "/second.ts": "export function named(): number { return 2; }",
            "/consumer.ts": `
                import { named as first } from "./first";
                import { named as second } from "./second";
                export function check(): number { const alias = first; return alias() + second(); }
            `,
        };
        const { file, diagnostics } = lowerProject(sources, "/consumer.ts");
        const reads = statements(methodByName(file, "check")).filter((stmt): stmt is AssignStmtDto =>
            stmt._ === "AssignStmt" && stmt.right._ === "StaticFieldRef");

        expect(reads.map((stmt) => stmt.right)).toMatchObject([
            { field: { name: "named", declaringClass: { declaringFile: { fileName: "/first.ts" } } } },
            { field: { name: "named", declaringClass: { declaringFile: { fileName: "/second.ts" } } } },
        ]);
        for (const stmt of reads) {
            if (stmt.right._ !== "StaticFieldRef") throw new Error("expected binding read");
            const type = stmt.right.field.type;
            expect(type).toMatchObject({ _: "FunctionType", signature: { declaringClass: stmt.right.field.declaringClass } });
        }
        expect(diagnostics.messages).toEqual([]);
    });

    it("initializes an overload binding from its body signature exactly once", () => {
        const source = `
            function named(value: number): number;
            function named(value: string): string;
            function named(value: number | string): number | string { return value; }
            const alias = named;
        `;
        const { file, diagnostics } = lower(source);
        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;
        const implementation = defaultClass.methods.find((method) => method.signature.name === "named" && method.body)!;
        const initializes = statements(defaultMethod(file)).filter((stmt) => stmt._ === "AssignStmt"
            && stmt.left._ === "StaticFieldRef" && stmt.left.field.name === "named");

        expect(defaultClass.fields.filter((field) => field.signature.name === "named")).toHaveLength(1);
        expect(initializes).toHaveLength(1);
        expect(initializes[0]).toMatchObject({ right: { type: { _: "FunctionType", signature: implementation.signature } } });
        expect(diagnostics.messages).toEqual([]);
        expect(nativeResult(source, "[alias(2), alias('value')]")).toEqual([2, "value"]);
    });

    it("keeps bodyless ambient function values explicitly unsupported", () => {
        const { file, diagnostics } = lower("declare function named(): number; const alias = named;");
        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;

        expect(defaultClass.fields.some((field) => field.signature.name === "named")).toBe(false);
        expect(statements(defaultMethod(file))).toContainEqual(expect.objectContaining({
            right: expect.objectContaining({ _: "UnsupportedValue", kindName: "Identifier" }),
        }));
        expect(diagnostics.messages).toContainEqual(expect.stringContaining("runtime value of declaration"));
    });

    it("rejects ambiguous duplicate implementation bodies instead of inventing function identity", () => {
        const { file, diagnostics } = lower(`
            function named(): number { return 1; }
            function named(): number { return 2; }
            const alias = named;
        `);
        const defaultClass = file.classes.find((clazz) => clazz.signature.name === "%dflt")!;

        expect(defaultClass.fields.some((field) => field.signature.name === "named")).toBe(false);
        expect(statements(defaultMethod(file))).toContainEqual(expect.objectContaining({
            right: expect.objectContaining({ _: "UnsupportedValue", kindName: "Identifier" }),
        }));
        expect(diagnostics.messages).toContainEqual(expect.stringContaining("ambiguous implementation bodies"));
    });

    it("uses the namespace owner for internal reads and pointer calls", () => {
        const source = `namespace Scope {
            export function named(): number { return 3; }
            export function check(): boolean { const alias = named; return alias === named && named() === 3; }
        }`;
        const { file, diagnostics } = lower(source);
        const defaultClass = file.namespaces[0].classes!.find((clazz) => clazz.signature.name === "%dflt")!;
        const check = defaultClass.methods.find((method) => method.signature.name === "check")!;
        const reads = statements(check).filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticFieldRef");

        expect(reads).toHaveLength(3);
        expect(reads).toEqual(expect.arrayContaining([expect.objectContaining({
            right: expect.objectContaining({ field: expect.objectContaining({
                declaringClass: expect.objectContaining({ declaringNamespace: expect.objectContaining({ name: "Scope" }) }),
            }) }),
        })]));
        expect(statements(check).some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr")).toBe(true);
        expect(diagnostics.messages).toEqual([]);
        expect(nativeResult(source, "Scope.check()")).toBe(true);
    });

    it("keeps namespace object properties distinct from the internal declaration binding", () => {
        const source = `namespace Scope {
            export function named(): number { return 1; }
            export function replace(): void { named = other; }
            function other(): number { return 2; }
        }
        Scope.replace();
        const result = Scope.named();`;
        const { file, diagnostics } = lower(source);
        const stmts = statements(defaultMethod(file));

        expect(nativeResult(source, "result")).toBe(1);
        expect(stmts).toContainEqual(expect.objectContaining({
            right: expect.objectContaining({ _: "UnsupportedValue", kindName: "Identifier", text: "Scope" }),
        }));
        expect(stmts.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticCallExpr")).toBe(false);
        expect(diagnostics.messages).toContainEqual(expect.stringContaining("runtime value of declaration 'Scope'"));
    });

    it("reads an imported namespace function through its live module binding and calls retained aliases", () => {
        const sources = {
            "/module.ts": `
                export function named(): number { return 1; }
                export function replace(): void { named = replacement; }
                function replacement(): number { return 2; }
            `,
            "/consumer.ts": `
                import * as namespace from "./module";
                import { replace } from "./module";
                export function check(): number {
                    const alias = namespace.named;
                    replace();
                    const current = namespace.named;
                    return alias() * 10 + current();
                }
            `,
        };
        const { file, diagnostics } = lowerProject(sources, "/consumer.ts");
        const stmts = statements(methodByName(file, "check"));
        const reads = stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticFieldRef");

        expect(reads).toHaveLength(3);
        expect(reads.map((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "StaticFieldRef" && stmt.right.field.name))
            .toEqual(["named", "replace", "named"]);
        expect(stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr"
            || stmt._ === "CallStmt" && stmt.expr._ === "PtrCallExpr")).toHaveLength(3);
        expect(diagnostics.messages).toEqual([]);
    });

    it("rejects namespace calls whose receiver differs from calling a retained alias", () => {
        const sources = {
            "/module.ts": "export function named(): boolean { return this === undefined; }",
            "/consumer.ts": `
                import * as namespace from "./module";
                export function check(): boolean[] {
                    const alias = namespace.named;
                    return [namespace.named(), alias(), namespace.named?.(), (namespace.named)()];
                }
            `,
        };
        const { file, diagnostics } = lowerProject(sources, "/consumer.ts");
        const stmts = statements(methodByName(file, "check"));
        const module = {};
        const compilerOptions = { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS };
        new Function("exports", ts.transpileModule(sources["/module.ts"], { compilerOptions }).outputText)(module);
        const consumer: { check?: () => boolean[] } = {};
        new Function("exports", "require", ts.transpileModule(sources["/consumer.ts"], { compilerOptions }).outputText)(
            consumer, () => module,
        );

        expect(consumer.check!()).toEqual([false, true, false, false]);
        expect(stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue"
            && stmt.right.kindName === "CallExpression")).toHaveLength(3);
        expect(stmts.filter((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr")).toHaveLength(1);
        expect(diagnostics.messages.filter((message) => message.includes("module namespace call receiver"))).toHaveLength(3);
    });
});
