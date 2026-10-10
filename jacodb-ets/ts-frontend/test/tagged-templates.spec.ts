import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { serializeEtsFile } from "../src/serialize";
import { lower, methodByName, singleBlockStmts } from "./util";
import { executor } from "./execute";

function runTypeScript(source: string, expression: string): unknown {
    const js = ts.transpileModule(source, {
        compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
    }).outputText;

    return new Function("exports", `${js}\nreturn ${expression};`)({});
}

describe("tagged templates", () => {
    it("preserves the template object and tag call through JSON", () => {
        const source = "function first(parts: TemplateStringsArray): string { return parts[0]; }\n"
            + "export function value(): string { return first`hello`; }";
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const stmts = singleBlockStmts(methodByName(roundTrip, "value"));
        const creation = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "TemplateObjectExpr");
        const call = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr");

        expect(creation).toMatchObject({ right: { cooked: ["hello"], raw: ["hello"] } });
        expect(call).toMatchObject({ right: { method: { name: "first" }, args: [creation?._ === "AssignStmt" ? creation.left : undefined] } });
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "exports.value()")).toBe("hello");
    });

    it("captures a property tag and its receiver before substitutions", () => {
        const source = `
            class Tagger {
                marker = "receiver";
                tag(parts: TemplateStringsArray, value: string): string { return this.marker + parts.raw[0] + value; }
            }
            let events: string[] = [];
            function holder(tagger: Tagger): Tagger { events.push("receiver"); return tagger; }
            function substitution(): string { events.push("substitution"); return "value"; }
            export function value(tagger: Tagger): string { return holder(tagger).tag\`\\n\${substitution()}\`; }
            export function concrete(): string[] { return [value(new Tagger()), events.join(",")]; }
        `;
        const { file, diagnostics } = lower(source);
        const stmts = singleBlockStmts(methodByName(JSON.parse(serializeEtsFile(file)), "value"));
        const propertyIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PropertyRef");
        const templateIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "TemplateObjectExpr");
        const substitutionIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr"
            && stmt.right.method.name === "substitution");
        const property = stmts[propertyIndex];
        const call = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr"
            && stmt.right.method.name === "tag");

        expect(propertyIndex).toBeGreaterThanOrEqual(0);
        expect(templateIndex).toBeGreaterThan(propertyIndex);
        expect(substitutionIndex).toBeGreaterThan(templateIndex);
        expect(stmts[templateIndex]).toMatchObject({ right: { cooked: ["\n", ""], raw: ["\\n", ""] } });
        expect(call).toMatchObject({ right: {
            ptr: property?._ === "AssignStmt" ? property.left : undefined,
            receiver: property?._ === "AssignStmt" && property.right._ === "PropertyRef" ? property.right.instance : undefined,
            args: expect.any(Array),
        } });
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "exports.concrete()")).toEqual(["receiver\\nvalue", "receiver,substitution"]);
    });

    it("keeps one stable identity per source site and preserves frozen raw semantics", () => {
        const source = `
            let previous: TemplateStringsArray | undefined;
            function remember(parts: TemplateStringsArray): boolean {
                const same = previous === parts;
                previous = parts;
                return same && Object.isFrozen(parts) && Object.isFrozen(parts.raw);
            }
            export function sameSite(): boolean { return remember\`text\`; }
            export function otherSite(): boolean { return remember\`text\`; }
        `;
        const first = lower(source);
        const second = lower(source);
        const site = (file: typeof first.file, method: string): string | undefined => {
            const stmt = singleBlockStmts(methodByName(file, method))
                .find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "TemplateObjectExpr");
            return stmt?._ === "AssignStmt" && stmt.right._ === "TemplateObjectExpr" ? stmt.right.siteId : undefined;
        };

        expect(site(first.file, "sameSite")).toBeDefined();
        expect(site(first.file, "sameSite")).toBe(site(second.file, "sameSite"));
        expect(site(first.file, "sameSite")).not.toBe(site(first.file, "otherSite"));
        expect(first.diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "[exports.sameSite(), exports.sameSite(), exports.otherSite()]")).toEqual([false, true, false]);
    });

    it("preserves invalid escapes as undefined cooked entries and literal raw text", () => {
        const source = "function raw(parts: TemplateStringsArray): string { return String(parts[0]) + parts.raw[0]; }\n"
            + "export function value(): string { return raw`\\unicode`; }";
        const { file, diagnostics } = lower(source);
        const stmts = singleBlockStmts(methodByName(JSON.parse(serializeEtsFile(file)), "value"));

        expect(stmts).toContainEqual(expect.objectContaining({ right: expect.objectContaining({
            _: "TemplateObjectExpr", cooked: [null], raw: ["\\unicode"],
        }) }));
        expect(diagnostics.messages).toEqual([]);
        expect(runTypeScript(source, "exports.value()")).toBe("undefined\\unicode");
    });

    it("reads the current named tag and selects it before a substitution reassigns the binding", () => {
        const source = `
            function tag(parts: TemplateStringsArray, value: number): number { return value + 1; }
            function replacement(parts: TemplateStringsArray, value: number): number { return value + 10; }
            function reset(): number { tag = replacement; return 2; }
            export function check(): number {
                const alias = tag;
                const selected = tag\`text\${reset()}\`;
                return selected * 100 + alias\`text\${2}\` * 10 + tag\`text\${2}\`;
            }
        `;
        const { file, diagnostics } = lower(source);
        const serialized = JSON.parse(serializeEtsFile(file));
        const stmts = singleBlockStmts(methodByName(serialized, "check"));
        const selected = stmts.find((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "PtrCallExpr"
            && stmt.right.method.name === "tag")!;
        if (selected._ !== "AssignStmt" || selected.right._ !== "PtrCallExpr") throw new Error("Missing tag call");
        const selectedPtr = selected.right.ptr;
        const selectionIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.left._ === "Local" && stmt.left.name === selectedPtr.name);
        const mutationIndex = stmts.findIndex((stmt) => stmt._ === "AssignStmt"
            && stmt.right._ === "PtrCallExpr" && stmt.right.method.name === "reset");

        expect(selectionIndex).toBeGreaterThanOrEqual(0);
        expect(mutationIndex).toBeGreaterThan(selectionIndex);
        expect(stmts.indexOf(selected)).toBeGreaterThan(mutationIndex);
        expect(diagnostics.messages).toEqual([]);
        expect(executor(serialized).call("check")).toBe(342);
        expect(runTypeScript(source, "exports.check()")).toBe(342);
    });

});
