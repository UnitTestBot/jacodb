/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 * CLI entry point.
 *
 * Usage: `node dist/index.js [-p] [-e] [-t N] [--multi] [-v] <input> <output>`
 *
 * Parses TS/JS with the real TypeScript compiler and emits EtsIR JSON
 * consumable by the Kotlin side (`EtsFileDto.loadFromJson`).
 */

import * as fs from "fs";
import * as path from "path";
import * as ts from "typescript";
import { Diagnostics } from "./lowering/diagnostics";
import { buildEtsFile } from "./lowering/fileBuilder";
import { serializeEtsFile } from "./serialize";
import { validateEtsFile } from "./validate";

export const COMPILER_OPTIONS: ts.CompilerOptions = {
    target: ts.ScriptTarget.ES2020,
    module: ts.ModuleKind.CommonJS,
    strict: false,
    allowJs: true,
    checkJs: false,
    noEmit: true,
    skipLibCheck: true,
    jsx: ts.JsxEmit.Preserve,
};

interface CliArgs {
    input: string;
    output: string;
    project: boolean;
    multi: boolean;
    entrypoints: boolean;
    typeInference: number | undefined;
    verbose: boolean;
}

/**
 * Hand-rolled arg parsing.
 * NOTE: the Kotlin side historically passes `-t N` as a SINGLE argv token `"-t N"`,
 * so tokens containing spaces are pre-split here.
 * Everything after a `--` separator is treated as positional, which is the escape
 * hatch for paths starting with a dash.
 */
export function parseArgs(argv: string[]): CliArgs | string {
    const tokens = argv.flatMap((a) => (a.startsWith("-") && a.includes(" ") ? a.split(/\s+/) : [a]));
    const positional: string[] = [];
    const args: CliArgs = {
        input: "",
        output: "",
        project: false,
        multi: false,
        entrypoints: false,
        typeInference: undefined,
        verbose: false,
    };
    let positionalOnly = false;
    for (let i = 0; i < tokens.length; i++) {
        const token = tokens[i];
        if (positionalOnly) {
            positional.push(token);
            continue;
        }
        if (token === "--") {
            positionalOnly = true;
            continue;
        }
        switch (token) {
            case "-p":
            case "--project":
                args.project = true;
                break;
            case "-m":
            case "--multi":
                args.multi = true;
                break;
            case "-e":
            case "--entrypoints":
                args.entrypoints = true;
                break;
            case "-v":
            case "--verbose":
                args.verbose = true;
                break;
            case "-t":
            case "--type-inference": {
                const next = tokens[i + 1];
                if (next !== undefined && /^\d+$/.test(next)) {
                    args.typeInference = parseInt(next, 10);
                    i++;
                } else {
                    args.typeInference = 1;
                }
                break;
            }
            default:
                if (token.startsWith("-")) {
                    return `unknown option: ${token}`;
                }
                positional.push(token);
        }
    }
    if (positional.length !== 2) {
        return `expected exactly 2 positional arguments <input> <output>, got ${positional.length}`;
    }
    args.input = positional[0];
    args.output = positional[1];
    return args;
}

const SOURCE_EXTENSIONS = [".ts", ".tsx", ".mts", ".cts", ".js", ".jsx", ".mjs", ".cjs"];

function isSourceFilePath(filePath: string): boolean {
    const lower = filePath.toLowerCase();
    if (/\.d\.[mc]?ts$/.test(lower)) return false;
    return SOURCE_EXTENSIONS.some((ext) => lower.endsWith(ext));
}

/** Recursively collect source files under a directory. */
function collectSourceFiles(dir: string): string[] {
    const result: string[] = [];
    // NB: an explicit loop instead of `push(...spread)` — spreading a large array into
    // arguments overflows the call stack (RangeError) on very big subtrees.
    const walk = (current: string): void => {
        for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
            const full = path.join(current, entry.name);
            if (entry.isDirectory()) {
                if (entry.name === "node_modules" || entry.name.startsWith(".")) continue;
                walk(full);
            } else if (entry.isFile() && isSourceFilePath(entry.name)) {
                result.push(full);
            }
        }
    };
    walk(dir);
    return result.sort();
}

export interface ProjectInputs {
    sources: string[];
    options: ts.CompilerOptions;
    configPath?: string;
}

/** Resolve root files and compiler options, honoring tsconfig.json in project mode. */
export function resolveProjectInputs(inputDir: string, honorTsConfig: boolean = true): ProjectInputs {
    // Deliberately NOT `ts.findConfigFile`: it walks UP the directory tree, so analyzing a
    // directory in /tmp or inside a monorepo would pick up a foreign tsconfig whose
    // fileNames are then filtered away to nothing.
    const localConfig = path.join(path.resolve(inputDir), "tsconfig.json");
    const configPath = honorTsConfig && ts.sys.fileExists(localConfig) ? localConfig : undefined;
    if (configPath === undefined) {
        return { sources: collectSourceFiles(inputDir), options: COMPILER_OPTIONS };
    }

    const loaded = ts.readConfigFile(configPath, ts.sys.readFile);
    if (loaded.error !== undefined) {
        throw new Error(formatDiagnostic(loaded.error));
    }
    const parsed = ts.parseJsonConfigFileContent(
        loaded.config,
        ts.sys,
        path.dirname(configPath),
        { noEmit: true },
        configPath,
    );
    if (parsed.errors.length > 0) {
        throw new Error(parsed.errors.map(formatDiagnostic).join("\n"));
    }

    const sources = [...new Set(
        parsed.fileNames.filter((file) => isSourceFilePath(file) && isWithinDirectory(inputDir, file)),
    )]
        .map((file) => path.resolve(file))
        .sort();
    return {
        sources,
        options: { ...parsed.options, noEmit: true },
        configPath,
    };
}

function isWithinDirectory(directory: string, file: string): boolean {
    const relative = path.relative(path.resolve(directory), path.resolve(file));
    return relative !== "" && !relative.startsWith(`..${path.sep}`) && relative !== ".." && !path.isAbsolute(relative);
}

function formatDiagnostic(diagnostic: ts.Diagnostic): string {
    const message = ts.flattenDiagnosticMessageText(diagnostic.messageText, "\n");
    if (diagnostic.file === undefined || diagnostic.start === undefined) return message;
    const position = diagnostic.file.getLineAndCharacterOfPosition(diagnostic.start);
    return `${diagnostic.file.fileName}:${position.line + 1}:${position.character + 1}: ${message}`;
}

interface EmitResult {
    violations: string[];
    warnings: string[];
}

/** Lower one source file of `program` and write its JSON next to `outputPath`. */
function emitOne(
    program: ts.Program,
    sourceFile: ts.SourceFile,
    projectName: string,
    fileName: string,
    outputPath: string,
    fileSignatureFor: ((sf: ts.SourceFile) => { projectName: string; fileName: string }) | undefined,
): EmitResult {
    const diagnostics = new Diagnostics();
    const file = buildEtsFile(program, sourceFile, { projectName, fileName, fileSignatureFor }, diagnostics);
    const violations = validateEtsFile(file);
    if (violations.length === 0) {
        fs.mkdirSync(path.dirname(path.resolve(outputPath)), { recursive: true });
        fs.writeFileSync(outputPath, serializeEtsFile(file));
    }
    return { violations, warnings: diagnostics.messages };
}

export function main(argv: string[]): number {
    const parsed = parseArgs(argv);
    if (typeof parsed === "string") {
        process.stderr.write(`error: ${parsed}\n`);
        process.stderr.write("usage: node index.js [-p] [-e] [-t N] [--multi] [-v] <input> <output>\n");
        return 2;
    }
    const log = (msg: string) => {
        if (parsed.verbose) {
            process.stderr.write(`[ets-frontend] ${msg}\n`);
        }
    };
    log(`input: ${parsed.input}`);
    log(`output: ${parsed.output}`);

    const inputPath = path.resolve(parsed.input);
    if (!fs.existsSync(inputPath)) {
        process.stderr.write(`error: input does not exist: ${inputPath}\n`);
        return 1;
    }

    // Directory mode: -p (project) and --multi behave the same here — one shared
    // program over all files, one JSON per source file mirroring the input tree.
    if (parsed.project || parsed.multi) {
        if (!fs.statSync(inputPath).isDirectory()) {
            process.stderr.write(`error: directory expected in project/multi mode: ${inputPath}\n`);
            return 1;
        }
        const projectName = path.basename(inputPath);
        let projectInputs: ProjectInputs;
        try {
            // --multi intentionally remains a raw recursive conversion mode;
            // only --project applies tsconfig include/exclude and options.
            projectInputs = resolveProjectInputs(inputPath, parsed.project);
        } catch (error) {
            process.stderr.write(`error: failed to load project configuration: ${String(error)}\n`);
            return 1;
        }
        const { sources, options } = projectInputs;
        if (projectInputs.configPath !== undefined) {
            log(`using tsconfig: ${projectInputs.configPath}`);
        }
        log(`found ${sources.length} source files`);
        if (sources.length === 0) {
            // Silently producing an empty scene hides real misconfigurations
            // (ArkTS-only project, SDK of *.d.ts files, foreign tsconfig).
            process.stderr.write(`error: no source files found under: ${inputPath}\n`);
            if (projectInputs.configPath !== undefined) {
                process.stderr.write(`note: using tsconfig: ${projectInputs.configPath}\n`);
            }
            process.stderr.write(`note: recognized extensions: ${SOURCE_EXTENSIONS.join(", ")}\n`);
            return 1;
        }

        const program = ts.createProgram(sources, options);
        const relativeOf = (sf: ts.SourceFile): string =>
            path.relative(inputPath, path.resolve(sf.fileName)).split(path.sep).join("/");
        const fileSignatureFor = (sf: ts.SourceFile) => {
            const relative = path.relative(inputPath, path.resolve(sf.fileName));
            const isProjectFile = !relative.startsWith("..")
                && !path.isAbsolute(relative)
                && !relative.split(path.sep).includes("node_modules");
            if (isProjectFile) {
                return { projectName, fileName: relativeOf(sf) };
            }
            return { projectName: "%unk", fileName: "%unk" };
        };

        let hadErrors = false;
        for (const source of sources) {
            const sourceFile = program.getSourceFile(source);
            if (sourceFile === undefined) {
                process.stderr.write(`error: could not load: ${source}\n`);
                hadErrors = true;
                continue;
            }
            const relative = path.relative(inputPath, source).split(path.sep).join("/");
            const outputFile = path.join(parsed.output, `${relative}.json`);
            const result = emitOne(program, sourceFile, projectName, relative, outputFile, fileSignatureFor);
            for (const warning of result.warnings) {
                log(`warning: ${warning}`);
            }
            for (const violation of result.violations) {
                process.stderr.write(`invariant violation in ${relative}: ${violation}\n`);
                hadErrors = true;
            }
            log(`emitted: ${outputFile}`);
        }
        return hadErrors ? 1 : 0;
    }

    // Single-file mode.
    const program = ts.createProgram([inputPath], COMPILER_OPTIONS);
    const sourceFile = program.getSourceFile(inputPath);
    if (sourceFile === undefined) {
        process.stderr.write(`error: could not load source file: ${inputPath}\n`);
        return 1;
    }
    const result = emitOne(program, sourceFile, "", path.basename(inputPath), parsed.output, undefined);
    for (const warning of result.warnings) {
        log(`warning: ${warning}`);
    }
    if (result.violations.length > 0) {
        for (const violation of result.violations) {
            process.stderr.write(`invariant violation: ${violation}\n`);
        }
        return 1;
    }
    log("done");
    return 0;
}

if (require.main === module) {
    process.exit(main(process.argv.slice(2)));
}
