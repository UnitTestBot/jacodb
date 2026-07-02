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
 * File-level lowering: ts.SourceFile -> EtsFileDto.
 *
 * Structure conventions (ArkAnalyzer-compatible):
 *  - every file (and every namespace) gets a default class `%dflt`;
 *  - loose top-level statements form its default method `%dflt`;
 *  - function declarations become methods of the enclosing `%dflt`;
 *  - classes/interfaces/enums become ClassDto entries;
 *  - namespaces become NamespaceDto entries (recursively);
 *  - imports/exports become Import/ExportInfoDto entries (M6).
 */

import * as ts from "typescript";
import { DEFAULT_ARK_CLASS_NAME, DEFAULT_ARK_METHOD_NAME } from "../dto/constants";
import { ClassDto, EtsFileDto, MethodDto, NamespaceDto } from "../dto/model";
import { ClassSignatureDto, FileSignatureDto, NamespaceSignatureDto } from "../dto/signatures";
import { VOID_TYPE } from "../dto/types";
import { TypeConverter } from "../types/convert";
import { ClassBuilder } from "./classBuilder";
import { Diagnostics } from "./diagnostics";
import { LoweringContext, MethodContext } from "./methodBuilder";
import { StmtLowerer } from "./stmtLowering";

export interface BuildFileOptions {
    projectName: string;
    /** Project-relative file name, e.g. `src/foo.ts`. */
    fileName: string;
    /** Resolve signatures for OTHER project files (project mode); defaults to %unk. */
    fileSignatureFor?: (sf: ts.SourceFile) => FileSignatureDto;
}

export function buildEtsFile(
    program: ts.Program,
    sourceFile: ts.SourceFile,
    options: BuildFileOptions,
    diagnostics: Diagnostics = new Diagnostics(),
): EtsFileDto {
    const fileSignature: FileSignatureDto = {
        projectName: options.projectName,
        fileName: options.fileName,
    };
    const fileSignatureFor = (sf: ts.SourceFile): FileSignatureDto => {
        if (sf === sourceFile) {
            return fileSignature;
        }
        if (options.fileSignatureFor !== undefined) {
            return options.fileSignatureFor(sf);
        }
        return { projectName: "%unk", fileName: "%unk" };
    };

    const checker = program.getTypeChecker();
    const converter = new TypeConverter(checker, fileSignatureFor);
    const ctx: LoweringContext = { checker, converter, fileSignatureFor, diagnostics };

    const builder = new FileBuilder(ctx, fileSignature);
    return builder.build(sourceFile);
}

/** Lowered contents of a statement scope (source file or namespace body). */
interface ScopeContents {
    classes: ClassDto[];
    namespaces: NamespaceDto[];
}

class FileBuilder {
    private readonly classBuilder: ClassBuilder;

    constructor(
        private readonly ctx: LoweringContext,
        private readonly fileSignature: FileSignatureDto,
    ) {
        this.classBuilder = new ClassBuilder(ctx);
    }

    build(sourceFile: ts.SourceFile): EtsFileDto {
        const contents = this.buildScope(sourceFile.statements, undefined);
        return {
            signature: this.fileSignature,
            namespaces: contents.namespaces,
            classes: contents.classes,
            importInfos: [],
            exportInfos: [],
        };
    }

    /**
     * Lower the statements of a scope (file or namespace body):
     * a `%dflt` class collecting loose statements + functions, plus
     * declared classes/interfaces/enums and nested namespaces.
     */
    private buildScope(
        statements: readonly ts.Statement[],
        declaringNamespace: NamespaceSignatureDto | undefined,
    ): ScopeContents {
        const classes: ClassDto[] = [];
        const namespaces: NamespaceDto[] = [];

        const defaultClassSignature: ClassSignatureDto = {
            name: DEFAULT_ARK_CLASS_NAME,
            declaringFile: this.fileSignature,
        };
        if (declaringNamespace !== undefined) {
            defaultClassSignature.declaringNamespace = declaringNamespace;
        }

        const methods: MethodDto[] = [this.buildDefaultMethod(defaultClassSignature, statements)];

        for (const statement of statements) {
            if (ts.isFunctionDeclaration(statement) && statement.name !== undefined) {
                methods.push(this.classBuilder.buildMethodFromDecl(defaultClassSignature, statement));
            } else if (ts.isClassDeclaration(statement)) {
                classes.push(this.classBuilder.buildClass(statement));
            } else if (ts.isInterfaceDeclaration(statement)) {
                classes.push(this.classBuilder.buildInterface(statement));
            } else if (ts.isEnumDeclaration(statement)) {
                classes.push(this.classBuilder.buildEnum(statement));
            } else if (ts.isModuleDeclaration(statement)) {
                const namespace = this.buildNamespace(statement);
                if (namespace !== undefined) {
                    namespaces.push(namespace);
                }
            }
        }

        classes.unshift({
            signature: defaultClassSignature,
            modifiers: 0,
            decorators: [],
            category: 0,
            superClassName: "",
            implementedInterfaceNames: [],
            fields: [],
            methods,
        });

        return { classes, namespaces };
    }

    private buildNamespace(decl: ts.ModuleDeclaration): NamespaceDto | undefined {
        if (!ts.isIdentifier(decl.name)) {
            this.ctx.diagnostics.warn(decl, "string-named modules are not supported");
            return undefined;
        }
        // `namespace A.B {}` nests; resolve the innermost body.
        let body = decl.body;
        let signature: NamespaceSignatureDto = {
            name: decl.name.text,
            declaringFile: this.fileSignature,
        };
        const outer = this.ctx.converter.namespaceSignatureOf(decl);
        if (outer !== undefined) {
            signature.declaringNamespace = outer;
        }

        while (body !== undefined && ts.isModuleDeclaration(body)) {
            const innerSignature: NamespaceSignatureDto = {
                name: ts.isIdentifier(body.name) ? body.name.text : "%unk",
                declaringFile: this.fileSignature,
                declaringNamespace: signature,
            };
            signature = innerSignature;
            body = body.body;
        }
        if (body === undefined || !ts.isModuleBlock(body)) {
            // Ambient namespace without a body.
            return { signature, classes: [], namespaces: [] };
        }

        const contents = this.buildScope(body.statements, signature);
        return {
            signature,
            classes: contents.classes,
            namespaces: contents.namespaces,
        };
    }

    /** Loose scope statements -> `%dflt` method. */
    private buildDefaultMethod(
        declaringClass: ClassSignatureDto,
        statements: readonly ts.Statement[],
    ): MethodDto {
        const m = new MethodContext(this.ctx, declaringClass, DEFAULT_ARK_METHOD_NAME);
        m.emitPrologue([]);
        const lowerer = new StmtLowerer(m);
        for (const statement of statements) {
            lowerer.lowerStatement(statement);
        }
        return {
            signature: {
                declaringClass,
                name: DEFAULT_ARK_METHOD_NAME,
                parameters: [],
                returnType: VOID_TYPE,
            },
            modifiers: 0,
            decorators: [],
            body: m.build(),
        };
    }
}
