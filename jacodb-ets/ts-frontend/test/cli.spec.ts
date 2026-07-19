import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import * as ts from "typescript";
import { afterEach, describe, expect, it } from "vitest";
import { main, parseArgs, resolveProjectInputs } from "../src/index";

const tempDirs: string[] = [];

afterEach(() => {
    for (const dir of tempDirs.splice(0)) {
        fs.rmSync(dir, { recursive: true, force: true });
    }
});

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

describe("project mode", () => {
    it("honors tsconfig include/exclude and compiler options, including TSX", () => {
        const projectDir = fs.mkdtempSync(path.join(os.tmpdir(), "ets-frontend-project-"));
        tempDirs.push(projectDir);
        fs.mkdirSync(path.join(projectDir, "src"));
        fs.writeFileSync(
            path.join(projectDir, "tsconfig.json"),
            JSON.stringify({
                compilerOptions: { strict: true, target: "ES2017", jsx: "react-jsx" },
                include: ["src/**/*.tsx"],
                exclude: ["src/ignored.tsx"],
            }),
        );
        fs.writeFileSync(
            path.join(projectDir, "src", "included.tsx"),
            "export const view = <section>ready</section>;",
        );
        fs.writeFileSync(path.join(projectDir, "src", "ignored.tsx"), "export const ignored = 1;");
        fs.writeFileSync(path.join(projectDir, "outside.ts"), "export const outside = 2;");

        const inputs = resolveProjectInputs(projectDir);
        expect(inputs.configPath).toBe(path.join(projectDir, "tsconfig.json"));
        expect(inputs.sources.map((file) => path.basename(file))).toEqual(["included.tsx"]);
        expect(inputs.options.strict).toBe(true);
        expect(inputs.options.target).toBe(ts.ScriptTarget.ES2017);
        expect(inputs.options.jsx).toBe(ts.JsxEmit.ReactJSX);

        const outputDir = path.join(projectDir, "ir");
        expect(main(["--project", projectDir, outputDir])).toBe(0);
        expect(fs.existsSync(path.join(outputDir, "src", "included.tsx.json"))).toBe(true);
        expect(fs.existsSync(path.join(outputDir, "src", "ignored.tsx.json"))).toBe(false);
        expect(fs.existsSync(path.join(outputDir, "outside.ts.json"))).toBe(false);
    });

    it("keeps multi mode independent from tsconfig filtering", () => {
        const projectDir = fs.mkdtempSync(path.join(os.tmpdir(), "ets-frontend-multi-"));
        tempDirs.push(projectDir);
        fs.writeFileSync(path.join(projectDir, "tsconfig.json"), JSON.stringify({ files: [] }));
        fs.writeFileSync(path.join(projectDir, "component.jsx"), "export const component = 1;");

        const inputs = resolveProjectInputs(projectDir, false);
        expect(inputs.configPath).toBeUndefined();
        expect(inputs.sources.map((file) => path.basename(file))).toEqual(["component.jsx"]);
    });
});
