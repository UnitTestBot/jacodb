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
 * M1 status: skeleton emitter — parses no code yet, emits a minimal valid
 * EtsFileDto (default class `%dflt` with default method `%dflt`) so the whole
 * TS → JSON → Kotlin pipeline can be exercised end-to-end.
 */

import * as fs from "fs";
import * as path from "path";
import { DEFAULT_ARK_CLASS_NAME, DEFAULT_ARK_METHOD_NAME } from "./dto/constants";
import { EtsFileDto } from "./dto/model";
import { ClassSignatureDto, FileSignatureDto } from "./dto/signatures";
import { RETURN_VOID_STMT } from "./dto/stmts";
import { ClassTypeDto, VOID_TYPE } from "./dto/types";
import { serializeEtsFile } from "./serialize";
import { validateEtsFile } from "./validate";

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

/** M1 placeholder: minimal valid EtsFileDto for a source file (no parsing yet). */
export function buildSkeletonFile(projectName: string, fileName: string): EtsFileDto {
    const fileSignature: FileSignatureDto = { projectName, fileName };
    const classSignature: ClassSignatureDto = {
        name: DEFAULT_ARK_CLASS_NAME,
        declaringFile: fileSignature,
    };
    const classType: ClassTypeDto = { _: "ClassType", signature: classSignature };
    return {
        signature: fileSignature,
        namespaces: [],
        classes: [
            {
                signature: classSignature,
                modifiers: 0,
                decorators: [],
                category: 0,
                superClassName: "",
                implementedInterfaceNames: [],
                fields: [],
                methods: [
                    {
                        signature: {
                            declaringClass: classSignature,
                            name: DEFAULT_ARK_METHOD_NAME,
                            parameters: [],
                            returnType: VOID_TYPE,
                        },
                        modifiers: 0,
                        decorators: [],
                        body: {
                            locals: [{ name: "this", type: classType }],
                            cfg: {
                                blocks: [
                                    {
                                        id: 0,
                                        successors: [],
                                        predecessors: [],
                                        stmts: [
                                            {
                                                _: "AssignStmt",
                                                left: { _: "Local", name: "this", type: classType },
                                                right: { _: "ThisRef", type: classType },
                                            },
                                            RETURN_VOID_STMT,
                                        ],
                                    },
                                ],
                            },
                        },
                    },
                ],
            },
        ],
        importInfos: [],
        exportInfos: [],
    };
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

    if (parsed.project || parsed.multi) {
        process.stderr.write("error: project/multi modes are not implemented yet\n");
        return 2;
    }

    log(`input: ${parsed.input}`);
    log(`output: ${parsed.output}`);

    const fileName = path.basename(parsed.input);
    const file = buildSkeletonFile("", fileName);

    const violations = validateEtsFile(file);
    if (violations.length > 0) {
        for (const violation of violations) {
            process.stderr.write(`invariant violation: ${violation}\n`);
        }
        return 1;
    }

    fs.mkdirSync(path.dirname(path.resolve(parsed.output)), { recursive: true });
    fs.writeFileSync(parsed.output, serializeEtsFile(file));
    log("done");
    return 0;
}

if (require.main === module) {
    process.exit(main(process.argv.slice(2)));
}
