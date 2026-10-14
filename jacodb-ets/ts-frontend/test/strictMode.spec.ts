import { describe, expect, it } from "vitest";
import { lower, methodByName } from "./util";

describe("lexical strict-mode metadata", () => {
    it("distinguishes sloppy functions, own directives, and their callers", () => {
        const { file } = lower(`
            function sloppy() { return 1; }
            function strictCaller() { "use strict"; return sloppy(); }
            function sloppyCaller() { return strictCaller(); }
        `);

        expect(methodByName(file, "sloppy").isStrictMode).toBe(false);
        expect(methodByName(file, "strictCaller").isStrictMode).toBe(true);
        expect(methodByName(file, "sloppyCaller").isStrictMode).toBe(false);
        expect(methodByName(file, "%dflt").isStrictMode).toBe(false);
    });

    it("marks every function in an external module strict", () => {
        const { file } = lower(`export function run() { return () => 1; }`);
        const methods = file.classes.flatMap(clazz => clazz.methods);

        expect(methods.length).toBeGreaterThan(1);
        expect(methods.every(method => method.isStrictMode === true)).toBe(true);
    });

    it("inherits a script directive and a function directive into closures", () => {
        const { file } = lower(`"other directive"; "use strict";
            function run() { function nested() { return 1; } return () => nested(); }
        `);
        const methods = file.classes.flatMap(clazz => clazz.methods);

        expect(methods.every(method => method.isStrictMode === true)).toBe(true);

        const ownDirective = lower(`function run() {
            "use strict"; function nested() { return 1; } return () => nested();
        }`).file;
        const functionMethods = ownDirective.classes.flatMap(clazz => clazz.methods)
            .filter(method => method.signature.name !== "%dflt");

        expect(functionMethods.length).toBeGreaterThan(1);
        expect(functionMethods.every(method => method.isStrictMode === true)).toBe(true);
        expect(methodByName(ownDirective, "%dflt").isStrictMode).toBe(false);
    });

    it("keeps an unsupported sloppy object method distinct from strict class code", () => {
        const { file, diagnostics } = lower(`
            function object() { return { sloppy() { return 1; } }; }
            class Box {
                value = 1;
                static value = 2;
                method() { return () => this.value; }
            }
        `);
        const box = file.classes.find(clazz => clazz.signature.name === "Box")!;

        expect(methodByName(file, "object").isStrictMode).toBe(false);
        const objectStmts = methodByName(file, "object").body!.cfg.blocks.flatMap((block) => block.stmts);
        expect(objectStmts).toContainEqual(expect.objectContaining({
            _: "AssignStmt",
            right: expect.objectContaining({ _: "UnsupportedValue", kindName: "ObjectLiteralExpression" }),
        }));
        expect(diagnostics.messages).toEqual(expect.arrayContaining([
            expect.stringContaining("object literal method needs a per-evaluation callable own property"),
        ]));
        expect(box.methods.length).toBeGreaterThan(3);
        expect(box.methods.every(method => method.isStrictMode === true)).toBe(true);
    });

    it("does not treat escaped, parenthesized, or late strings as strict directives", () => {
        const { file } = lower(String.raw`
            function escaped() { "use\x20strict"; return 1; }
            function parenthesized() { ("use strict"); return 1; }
            function late() { const value = 1; "use strict"; return value; }
            function nestedBlock() { { "use strict"; } return 1; }
        `);

        for (const name of ["escaped", "parenthesized", "late", "nestedBlock"]) {
            expect(methodByName(file, name).isStrictMode).toBe(false);
        }
    });

    it("preserves outer strictness for namespace and enum initializer code", () => {
        const { file } = lower(`"use strict"; namespace N { export function run() { return 1; } }
            enum Value { First = 1 }
        `);
        const namespaceMethods = file.namespaces[0].classes!.flatMap(clazz => clazz.methods);

        expect(namespaceMethods.every(method => method.isStrictMode === true)).toBe(true);
        expect(methodByName(file, "%statInit").isStrictMode).toBe(true);
    });
});
