import { describe, expect, it } from "vitest";
import { runInNewContext } from "node:vm";
import * as ts from "typescript";
import { executeObjectIr } from "./object-runtime";
import { lower, methodByName } from "./util";

describe("unsupported expressions inside optional chains", () => {
    it.each([
        "receiver?.run(...args)",
        "receiver.run?.(...args)",
        "receiver?.run?.(...args)",
        "receiver?.child?.run?.(...args)",
        "(receiver?.run)?.(...args)",
        "receiver?.run(...args).field",
    ])("keeps an explicit fallback and completes the CFG for %s", (call) => {
        const { file, diagnostics } = lower(`
            function read(receiver: any, args: any[]) {
                ${call};
                return 17;
            }
        `);

        const method = methodByName(file, "read");
        const statements = method.body!.cfg.blocks.flatMap((block) => block.stmts);
        expect(statements.some((stmt) => stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue")).toBe(true);
        expect(statements.some((stmt) => stmt._ === "ReturnStmt" && stmt.arg._ === "Constant"
            && stmt.arg.value === "17")).toBe(true);
        expect(diagnostics.messages.some((message) => message.includes("spread argument has no statically known length")))
            .toBe(true);
    });

    it.each([null, undefined, { run: undefined }])("retains native short-circuit behavior for %s", (receiver) => {
        const source = `
            export function read(receiver: any, args: any[]) {
                receiver?.run?.(...args);
                return 17;
            }
        `;
        const { file } = lower(source);
        const serialized = JSON.parse(JSON.stringify(file));
        const javascript = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;
        const native = runInNewContext(`${javascript}\nexports.read`, { exports: {} });
        const args = { [Symbol.iterator]() { throw new Error("Unreachable argument iteration"); } };

        expect(native(receiver, args)).toBe(17);
        expect(executeObjectIr(serialized, "read", [receiver, args])).toBe(17);
    });

    it("preserves the supported getter prefix and retains the unsupported call boundary", () => {
        const { file } = lower(`
            export function read(receiver: any, args: any[]) {
                receiver?.run(...args);
                return 17;
            }
        `);
        let getterReads = 0;
        let calls = 0;
        const receiver = {
            get run() {
                getterReads++;
                return () => calls++;
            },
        };

        expect(() => executeObjectIr(file, "read", [receiver, []])).toThrow("UnsupportedValue");
        expect(getterReads).toBe(1);
        expect(calls).toBe(0);
    });

    it("completes diamonds inside a lifted callback while keeping other class members", () => {
        const { file } = lower(`
            class Example {
                build() { return (receiver: any, args: any[]) => receiver?.run(...args); }
                supported() { return 17; }
            }
        `);

        expect(methodByName(file, "%AM0$build").body!.cfg.blocks.some((block) => block.stmts.some((stmt) =>
            stmt._ === "AssignStmt" && stmt.right._ === "UnsupportedValue",
        ))).toBe(true);
        expect(methodByName(file, "supported").body).toBeDefined();
    });

    it("completes optional fallbacks inside a try with a finally continuation", () => {
        const { file } = lower(`
            function read(receiver: any, args: any[]) {
                let result = 0;
                try { receiver?.run(...args); }
                finally { result = 17; }
                return result;
            }
        `);

        expect(methodByName(file, "read").body!.cfg.blocks.length).toBeGreaterThan(1);
    });
});
