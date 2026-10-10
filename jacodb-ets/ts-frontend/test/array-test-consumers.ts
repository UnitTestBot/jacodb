import { describe, expect, it } from "vitest";
import * as ts from "typescript";
import { serializeEtsFile } from "../src/serialize";
import { executor } from "./execute";
import { lower } from "./util";

export function consumers(source: string, name: string, externals: Record<string, any> = {}) {
    const { file, diagnostics } = lower(source);
    expect(diagnostics.messages).toEqual([]);
    const ir = executor(JSON.parse(serializeEtsFile(file)), externals);
    const js = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2020 } }).outputText;
    const native = new Function(...Object.keys(externals), `${js}; return ${name};`)(...Object.values(externals));
    return { ir: (...args: any[]) => ir.call(name, ...args), native, initialize: () => ir.initialize() };
}
