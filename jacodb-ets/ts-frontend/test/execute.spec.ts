import * as ts from "typescript";
import { describe, expect, it } from "vitest";
import { serializeEtsFile } from "../src/serialize";
import { executor } from "./execute";
import { lower } from "./util";

describe("concrete oracle method dispatch", () => {
    it("calls the static method of its declaring class after JSON round trip", () => {
        const source = `
            class A { static value(): number { return 1; } }
            class B { static value(): number { return 2; } }
            export function read(): number { return B.value(); }
        `;
        const { file, diagnostics } = lower(source);
        const roundTrip = JSON.parse(serializeEtsFile(file));
        const javascript = ts.transpileModule(source, {
            compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
        }).outputText;

        const nativeResult = new Function("exports", `${javascript}\nreturn exports.read();`)({});
        const oracleResult = executor(roundTrip).call("read");

        expect(diagnostics.messages).toEqual([]);
        expect(nativeResult).toBe(2);
        expect(oracleResult).toBe(nativeResult);
    });
});
