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
    for (let i = 0; i < tokens.length; i++) {
        const token = tokens[i];
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

const SOURCE_EXTENSIONS = [".ts", ".mts", ".cts", ".js", ".mjs", ".cjs", ".ets"];

function isSourceFilePath(filePath: string): boolean {
    if (filePath.endsWith(".d.ts")) return false;
    return SOURCE_EXTENSIONS.some((ext) => filePath.endsWith(ext));
}

/** Recursively collect source files under a directory. */
function collectSourceFiles(dir: string): string[] {
    const result: string[] = [];
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            if (entry.name === "node_modules" || entry.name.startsWith(".")) continue;
            result.push(...collectSourceFiles(full));
        } else if (entry.isFile() && isSourceFilePath(entry.name)) {
            result.push(full);
        }
    }
    return result.sort();
}

/** Compiler host that parses unknown extensions (.ets) as TypeScript. */
function createHost(): ts.CompilerHost {
    const host = ts.createCompilerHost(COMPILER_OPTIONS);
    const originalGetSourceFile = host.getSourceFile.bind(host);
    host.getSourceFile = (name, languageVersion, onError, shouldCreateNewSourceFile) => {
        if (name.endsWith(".ets")) {
            const text = host.readFile(name);
            if (text === undefined) return undefined;
            return ts.createSourceFile(name, text, languageVersion, true, ts.ScriptKind.TS);
        }
        return originalGetSourceFile(name, languageVersion, onError, shouldCreateNewSourceFile);
    };
    return host;
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

function main(argv: string[]): number {
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
        const sources = collectSourceFiles(inputPath);
        log(`found ${sources.length} source files`);
        if (sources.length === 0) {
            return 0;
        }

        const program = ts.createProgram(sources, COMPILER_OPTIONS, createHost());
        const relativeOf = (sf: ts.SourceFile): string =>
            path.relative(inputPath, path.resolve(sf.fileName)).split(path.sep).join("/");
        const fileSignatureFor = (sf: ts.SourceFile) => {
            if (!sf.isDeclarationFile && !path.relative(inputPath, path.resolve(sf.fileName)).startsWith("..")) {
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
    const program = ts.createProgram([inputPath], COMPILER_OPTIONS, createHost());
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
