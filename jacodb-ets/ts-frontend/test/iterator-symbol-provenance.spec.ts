import { describe, expect, it } from "vitest";
import { InstanceCallExprDto } from "../src/dto/values";
import { serializeEtsFile } from "../src/serialize";
import { executor } from "./execute";
import { lower, methodByName } from "./util";

describe("intrinsic iterator symbol provenance", () => {
    for (const body of [
        "return [...input];",
        "const [first, ...rest] = input; return rest;",
        "for (const value of input) { if (value > 0) return value; } return 0;",
    ]) {
        it(`preserves symbol identity through JSON: ${body}`, () => {
            const { file, diagnostics } = lower(`function collect(input: number[], Symbol: any) { ${body} }`);
            const roundTrip = JSON.parse(serializeEtsFile(file));
            const calls = methodByName(roundTrip, "collect").body!.cfg.blocks.flatMap((block) => block.stmts)
                .flatMap((statement) => statement._ === "AssignStmt" && statement.right._ === "InstanceCallExpr"
                    ? [statement.right as InstanceCallExprDto] : []);

            expect(diagnostics.messages).toEqual([]);
            expect(calls.filter((call) => call.wellKnownSymbol === "iterator")).toHaveLength(1);
        });
    }

    it("ignores string-named lookalikes while still consulting an overridden real symbol", () => {
        const { file } = lower("function collect(input: any, Symbol: any) { return [...input]; }");
        const run = executor(JSON.parse(serializeEtsFile(file)));
        const input: any = [1, 2];
        Object.assign(input, { "Symbol.iterator": () => { throw new Error("string key must not run"); } });
        input[Symbol.iterator] = function* () { yield 7; };

        expect(run.call("collect", input, { iterator: "Symbol.iterator" })).toEqual([7]);
    });
});
