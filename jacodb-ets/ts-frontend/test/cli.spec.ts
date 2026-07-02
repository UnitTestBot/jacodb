import { describe, expect, it } from "vitest";
import { parseArgs } from "../src/index";

describe("parseArgs", () => {
    it("parses plain positional arguments", () => {
        const args = parseArgs(["in.ts", "out.json"]);
        expect(args).toMatchObject({ input: "in.ts", output: "out.json", verbose: false });
    });

    it("parses flags in any position", () => {
        const args = parseArgs(["-p", "-e", "in", "out", "-v"]);
        expect(args).toMatchObject({ project: true, entrypoints: true, verbose: true });
    });

    it("parses -t with a separate value token", () => {
        const args = parseArgs(["-t", "2", "in", "out"]);
        expect(args).toMatchObject({ typeInference: 2, input: "in", output: "out" });
    });

    it("tolerates the Kotlin quirk of a single '-t 1' token", () => {
        const args = parseArgs(["-t 1", "in", "out"]);
        expect(args).toMatchObject({ typeInference: 1, input: "in", output: "out" });
    });

    it("treats -t without numeric value as level 1", () => {
        const args = parseArgs(["in", "out", "-t"]);
        expect(args).toMatchObject({ typeInference: 1, input: "in", output: "out" });
    });

    it("rejects unknown options", () => {
        expect(typeof parseArgs(["--nope", "in", "out"])).toBe("string");
    });

    it("rejects wrong positional count", () => {
        expect(typeof parseArgs(["onlyone"])).toBe("string");
    });
});
