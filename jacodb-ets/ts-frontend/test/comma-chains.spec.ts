import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { serializeEtsFile } from "../src/serialize";
import { executor } from "./execute";
import { lower, methodByName } from "./util";

describe("comma expression lowering", () => {
    it("lowers the unchanged Binaryen expression shared by the Fun and LiteralMacro source graphs", () => {
        const expression = readFileSync(new URL("fixtures/binaryen-comma-expression.txt", import.meta.url), "utf8");
        const source = `export function initialize(A, j, jA) { return (${expression}); }`;

        const { file } = lower(source, "proj", "binaryen-excerpt.js");
        const serialized = JSON.parse(serializeEtsFile(file));
        const initialize = methodByName(serialized, "initialize");
        const statements = initialize.body!.cfg.blocks.flatMap((block) => block.stmts);

        expect(statements.filter((statement) => statement._ === "AssignStmt").length).toBeGreaterThan(1400);
        expect(statements.some((statement) => statement._ === "ReturnStmt")).toBe(true);
    });

    it("evaluates a deep left-associated sequence once in source order and returns its last value", () => {
        const updates = Array.from({ length: 2000 }, () => "count = count + 1");
        const source = `export function value() { let count = 0; return (${updates.join(",")}, count); }`;

        const { file, diagnostics } = lower(source, "proj", "deep-comma.js");
        const actual = executor(JSON.parse(serializeEtsFile(file))).call("value");
        const native = new Function(`${source.replace("export ", "")} return value();`)();

        expect(diagnostics.messages).toEqual([]);
        expect(actual).toBe(2000);
        expect(actual).toBe(native);
    });

    it("keeps discarded calls and the last result in order across nested comma operands", () => {
        const source = `export function value(record) { return (record(1), (record(2), record(3))); }`;
        const actualEvents: number[] = [];
        const nativeEvents: number[] = [];
        const record = (events: number[]) => (value: number): number => {
            events.push(value);
            return value * 10;
        };

        const { file, diagnostics } = lower(source, "proj", "ordered-comma.js");
        const actual = executor(JSON.parse(serializeEtsFile(file))).call("value", record(actualEvents));
        const native = new Function("record", `${source.replace("export ", "")} return value(record);`)(record(nativeEvents));

        expect(diagnostics.messages).toEqual([]);
        expect(actualEvents).toEqual([1, 2, 3]);
        expect(actualEvents).toEqual(nativeEvents);
        expect(actual).toBe(30);
        expect(actual).toBe(native);
    });

    it("stops evaluating later operands after an earlier operand throws", () => {
        const source = `export function value(record) { return (record(1), record(2), record(3)); }`;
        const events: number[] = [];
        const record = (value: number): number => {
            events.push(value);
            if (value === 2) throw new RangeError("stop");
            return value;
        };

        const { file } = lower(source, "proj", "throwing-comma.js");
        const run = (): unknown => executor(JSON.parse(serializeEtsFile(file))).call("value", record);

        expect(run).toThrowError(new RangeError("stop"));
        expect(events).toEqual([1, 2]);
    });

    it("attributes discarded calls to their own source operands", () => {
        const source = `export function value(record) { return (record(1), record(2), record(3)); }`;

        const { file } = lower(source, "proj", "comma-origins.js");
        const body = methodByName(file, "value").body!;
        const callOrigins = body.stmtOrigins!.filter(({ blockId, stmtIndex }) =>
            body.cfg.blocks[blockId].stmts[stmtIndex]._ === "CallStmt",
        );

        expect(callOrigins.map(({ source: span }) => source.slice(span.startOffset, span.endOffset)))
            .toEqual(["record(1)", "record(2)"]);
    });
});
