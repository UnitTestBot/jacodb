import { describe, expect, it } from "vitest";
import { AssignStmtDto, StmtDto } from "../src/dto/stmts";
import { syntaxKindName } from "../src/lowering/diagnostics";
import * as ts from "typescript";
import { validateEtsFile } from "../src/validate";
import { defaultMethod, lower, methodByName, singleBlockStmts } from "./util";

function allStmts(method: { body?: { cfg: { blocks: { stmts: StmtDto[] }[] } } }): StmtDto[] {
    return (method.body?.cfg.blocks ?? []).flatMap((block) => block.stmts);
}

describe("unsupported loop bindings do not break the whole file", () => {
    // `lower()` throws on unplaced labels via finalize(), so merely lowering is the assertion.
    const cases: Record<string, string> = {
        "array pattern with rest": "declare const xs: any[]; for (const [a, ...rest] of xs) { console.log(a); }",
        "member expression target": "declare const xs: any[]; const obj: any = {}; for (obj.x of xs) { console.log(obj.x); }",
        "computed property pattern": "declare const xs: any[]; const k = 'a'; for (const { [k]: v } of xs) { console.log(v); }",
        "rest in for-in": "declare const o: any; for (const [a, ...rest] in o) { console.log(a); }",
    };

    for (const [name, source] of Object.entries(cases)) {
        it(`lowers '${name}' into a raw fallback instead of failing`, () => {
            // `lower()` runs finalize() + validateEtsFile(), so an unplaced label would throw here.
            const { file, diagnostics } = lower(source);
            const stmts = file.classes.flatMap((c) => c.methods.flatMap((m) => allStmts(m)));
            expect(stmts.some((s) => s._ === "UnsupportedStmt")).toBe(true);
            expect(diagnostics.messages.some((msg) => msg.includes("unsupported loop binding"))).toBe(true);
        });
    }
});

describe("SyntaxKind names", () => {
    it("resolves real names instead of marker aliases", () => {
        expect(syntaxKindName(ts.SyntaxKind.VariableStatement)).toBe("VariableStatement");
        expect(syntaxKindName(ts.SyntaxKind.NumericLiteral)).toBe("NumericLiteral");
        expect(syntaxKindName(ts.SyntaxKind.ReturnStatement)).toBe("ReturnStatement");
    });
});

describe("pattern parameters", () => {
    it("gives distinct names to destructuring parameters", () => {
        const { file } = lower("function f({ a }: any, [b]: any[]): void {}");
        const names = methodByName(file, "f").signature.parameters.map((p) => p.name);
        expect(new Set(names).size).toBe(names.length);
    });

    it("unpacks an object pattern parameter from its ParameterRef before the body", () => {
        const { file } = lower("function g({ x }: { x: number }) { return x; }");
        const stmts = allStmts(methodByName(file, "g"));

        expect(stmts.some((stmt) =>
            stmt._ === "AssignStmt"
            && stmt.left._ === "Local"
            && stmt.left.name === "%pat0"
            && stmt.right._ === "ParameterRef"
            && stmt.right.index === 0,
        )).toBe(true);
        expect(stmts.some((stmt) =>
            stmt._ === "AssignStmt"
            && stmt.left._ === "Local"
            && stmt.left.name === "x"
            && stmt.right._ === "InstanceFieldRef"
            && stmt.right.instance._ === "Local"
            && stmt.right.instance.name === "%pat0"
            && stmt.right.field.name === "x",
        )).toBe(true);
    });

    it("unpacks object pattern parameters in closure prologues", () => {
        const { file } = lower("function wrap() { return ({ x }: { x: number }) => x; }");
        const stmts = allStmts(methodByName(file, "%AM0$wrap"));

        expect(stmts.some((stmt) =>
            stmt._ === "AssignStmt"
            && stmt.left._ === "Local"
            && stmt.left.name === "x"
            && stmt.right._ === "InstanceFieldRef"
            && stmt.right.instance._ === "Local"
            && stmt.right.instance.name === "%pat0"
            && stmt.right.field.name === "x",
        )).toBe(true);
    });
});

describe("source local names", () => {
    it("keeps source parameters out of validator-reserved local prefixes", () => {
        const { file } = lower("function f(_tmp0: number) { return _tmp0 + 1; }");

        expect(validateEtsFile(file)).toEqual([]);
    });
});

describe("static context", () => {
    it("uses StaticFieldRef for `this.x` inside a closure of a static method", () => {
        const { file } = lower(`
            class C {
                static value = 1;
                static run(): void {
                    const f = () => this.value;
                    f();
                }
            }
        `);
        const closure = methodByName(file, "%AM0$run");
        const reads = allStmts(closure).filter(
            (s): s is AssignStmtDto => s._ === "AssignStmt" && s.right._ === "StaticFieldRef",
        );
        expect(reads.length).toBeGreaterThan(0);
    });
});

describe("non-finite numeric literals", () => {
    it("degrades an overflowing literal type to number", () => {
        const { file } = lower("const huge: 1e999 = 1e999;");
        const json = JSON.stringify(file);
        expect(json).not.toContain('"literal":null');
    });
});

describe("function hoisting", () => {
    it("materializes a capture only after the captured local is assigned", () => {
        const { file } = lower(`
            function outer(): number {
                const seed = 1;
                function useSeed(): number { return seed; }
                return useSeed();
            }
        `);
        const stmts = singleBlockStmts(methodByName(file, "outer"));
        const seedAssign = stmts.findIndex(
            (s) => s._ === "AssignStmt" && s.left._ === "Local" && s.left.name === "seed",
        );
        const closureCreate = stmts.findIndex(
            (s): s is AssignStmtDto =>
                s._ === "AssignStmt" && s.right._ === "Local" && s.right.name.startsWith("%AM"),
        );
        // Both statements must exist — an unconditional assertion, unlike a guarded one.
        expect(seedAssign).toBeGreaterThanOrEqual(0);
        expect(closureCreate).toBeGreaterThanOrEqual(0);
        expect(seedAssign).toBeLessThan(closureCreate);
    });

    it("does not make a recursive nested function capture itself", () => {
        const { file } = lower(`
            function run(n: number): number {
                return fact(n);
                function fact(k: number): number {
                    return k <= 1 ? 1 : fact(k - 1);
                }
            }
        `);
        const closure = methodByName(file, "%AM0$run");
        expect(closure.signature.parameters.some((p) => p.type._ === "LexicalEnvType")).toBe(false);
    });
});
