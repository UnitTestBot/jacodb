import { describe, expect, it } from "vitest";
import { TypeDto } from "../src/dto/types";
import { CallExprDto } from "../src/dto/values";
import { serializeEtsFile } from "../src/serialize";
import { lower, methodByName } from "./util";

function resultTypes(source: string): TypeDto[] {
    const { file, diagnostics } = lower(source);
    expect(diagnostics.messages).toEqual([]);
    const roundTrip = JSON.parse(serializeEtsFile(file));
    const method = methodByName(roundTrip, "caller");
    const calls = method.body!.cfg.blocks.flatMap((block) => block.stmts).flatMap((statement) => {
        const value = statement._ === "AssignStmt" ? statement.right : undefined;
        const isCall = value?._ === "StaticCallExpr" || value?._ === "InstanceCallExpr" || value?._ === "PtrCallExpr";
        return isCall ? [value as CallExprDto] : [];
    });
    const result = calls.find((call) => call.method.name === "choice")!.method.returnType;
    return result._ === "UnionType" ? result.types : [result];
}

describe("declared nullish call results", () => {
    it("keeps null and undefined beside finite literals without strictNullChecks", () => {
        const types = resultTypes(`
            declare class External { static choice(): "known" | 7 | true | null | undefined; }
            function caller() { return External.choice(); }
        `);

        expect(types).toEqual(expect.arrayContaining([
            { _: "LiteralType", literal: "known" }, { _: "LiteralType", literal: 7 },
            { _: "LiteralType", literal: true }, { _: "NullType" }, { _: "UndefinedType" },
        ]));
        expect(types).toHaveLength(5);
    });

    it("preserves generic instantiation while restoring declared alias nullish members", () => {
        const types = resultTypes(`
            type Maybe<T> = T | null | undefined;
            declare class External { static choice<T>(): Maybe<T>; }
            function caller() { return External.choice<"kept">(); }
        `);

        expect(types).toEqual(expect.arrayContaining([
            { _: "LiteralType", literal: "kept" }, { _: "NullType" }, { _: "UndefinedType" },
        ]));
        expect(types).toHaveLength(3);
    });

    it("does not turn a nullable generic constraint into a return alternative", () => {
        const types = resultTypes(`
            declare class External { static choice<T extends number | null>(): T; }
            function caller() { return External.choice<7>(); }
        `);

        expect(types).toEqual([{ _: "LiteralType", literal: 7 }]);
    });
});
