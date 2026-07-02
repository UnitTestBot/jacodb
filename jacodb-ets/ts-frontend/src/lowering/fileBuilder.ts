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
 *  - every file gets a default class `%dflt`;
 *  - loose top-level statements form its default method `%dflt`;
 *  - top-level function declarations become methods of `%dflt`;
 *  - classes/interfaces/enums become ClassDto entries (M5);
 *  - imports/exports become Import/ExportInfoDto entries (M6).
 */

import * as ts from "typescript";
import { DEFAULT_ARK_CLASS_NAME, DEFAULT_ARK_METHOD_NAME, Modifier } from "../dto/constants";
import { ClassDto, EtsFileDto, MethodDto } from "../dto/model";
import { ClassSignatureDto, FileSignatureDto, MethodParameterDto } from "../dto/signatures";
import { TypeDto, UNKNOWN_TYPE, VOID_TYPE } from "../dto/types";
import { TypeConverter } from "../types/convert";
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

class FileBuilder {
    constructor(
        private readonly ctx: LoweringContext,
        private readonly fileSignature: FileSignatureDto,
    ) {}

    build(sourceFile: ts.SourceFile): EtsFileDto {
        const defaultClass = this.buildDefaultClass(sourceFile);
        return {
            signature: this.fileSignature,
            namespaces: [],
            classes: [defaultClass],
            importInfos: [],
            exportInfos: [],
        };
    }

    private buildDefaultClass(sourceFile: ts.SourceFile): ClassDto {
        const signature: ClassSignatureDto = {
            name: DEFAULT_ARK_CLASS_NAME,
            declaringFile: this.fileSignature,
        };

        const methods: MethodDto[] = [this.buildDefaultMethod(signature, sourceFile)];

        for (const statement of sourceFile.statements) {
            if (ts.isFunctionDeclaration(statement) && statement.name !== undefined) {
                methods.push(this.buildMethodFromFunction(signature, statement));
            } else if (
                ts.isClassDeclaration(statement) ||
                ts.isInterfaceDeclaration(statement) ||
                ts.isEnumDeclaration(statement) ||
                ts.isModuleDeclaration(statement)
            ) {
                // M5: classes/interfaces/enums/namespaces.
                this.ctx.diagnostics.warn(statement, `${ts.SyntaxKind[statement.kind]} is not lowered yet (M5)`);
            }
        }

        return {
            signature,
            modifiers: 0,
            decorators: [],
            category: 0,
            superClassName: "",
            implementedInterfaceNames: [],
            fields: [],
            methods,
        };
    }

    /** Loose top-level statements -> `%dflt` method. */
    private buildDefaultMethod(declaringClass: ClassSignatureDto, sourceFile: ts.SourceFile): MethodDto {
        const m = new MethodContext(this.ctx, declaringClass, DEFAULT_ARK_METHOD_NAME);
        m.emitPrologue([]);
        const lowerer = new StmtLowerer(m);
        for (const statement of sourceFile.statements) {
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

    buildMethodFromFunction(
        declaringClass: ClassSignatureDto,
        decl: ts.FunctionDeclaration,
    ): MethodDto {
        const name = decl.name !== undefined ? decl.name.text : DEFAULT_ARK_METHOD_NAME;
        const m = new MethodContext(this.ctx, declaringClass, name);

        const parameters: MethodParameterDto[] = [];
        const prologueParams: { name: string; type: TypeDto }[] = [];
        for (const p of decl.parameters) {
            const paramName = ts.isIdentifier(p.name) ? p.name.text : "%pat";
            const paramType =
                p.type !== undefined
                    ? this.ctx.converter.convertTypeNode(p.type)
                    : this.ctx.converter.typeOfNode(p.name);
            const param: MethodParameterDto = { name: paramName, type: paramType };
            if (p.questionToken !== undefined) param.isOptional = true;
            if (p.dotDotDotToken !== undefined) param.isRest = true;
            parameters.push(param);
            prologueParams.push({ name: paramName, type: paramType });
        }

        const returnType =
            decl.type !== undefined
                ? this.ctx.converter.convertTypeNode(decl.type)
                : this.inferredReturnType(decl);

        const method: MethodDto = {
            signature: { declaringClass, name, parameters, returnType },
            modifiers: modifiersOf(decl),
            decorators: [],
        };
        const typeParameters = this.ctx.converter.convertTypeParameters(decl.typeParameters);
        if (typeParameters !== undefined) {
            method.typeParameters = typeParameters;
        }

        if (decl.body !== undefined) {
            m.emitPrologue(prologueParams);
            const lowerer = new StmtLowerer(m);
            lowerer.lowerStatements(decl.body.statements);
            method.body = m.build();
        }
        return method;
    }

    private inferredReturnType(decl: ts.SignatureDeclaration): TypeDto {
        try {
            const signature = this.ctx.checker.getSignatureFromDeclaration(decl);
            if (signature !== undefined) {
                return this.ctx.converter.convertType(this.ctx.checker.getReturnTypeOfSignature(signature));
            }
        } catch {
            // fall through
        }
        return UNKNOWN_TYPE;
    }
}

/** Map ts modifiers to the EtsIR bitmask. */
export function modifiersOf(node: ts.HasModifiers): number {
    const flags = ts.getCombinedModifierFlags(node as ts.Declaration);
    let result = 0;
    if (flags & ts.ModifierFlags.Private) result |= Modifier.PRIVATE;
    if (flags & ts.ModifierFlags.Protected) result |= Modifier.PROTECTED;
    if (flags & ts.ModifierFlags.Public) result |= Modifier.PUBLIC;
    if (flags & ts.ModifierFlags.Export) result |= Modifier.EXPORT;
    if (flags & ts.ModifierFlags.Static) result |= Modifier.STATIC;
    if (flags & ts.ModifierFlags.Abstract) result |= Modifier.ABSTRACT;
    if (flags & ts.ModifierFlags.Async) result |= Modifier.ASYNC;
    if (flags & ts.ModifierFlags.Const) result |= Modifier.CONST;
    if (flags & ts.ModifierFlags.Accessor) result |= Modifier.ACCESSOR;
    if (flags & ts.ModifierFlags.Default) result |= Modifier.DEFAULT;
    if (flags & ts.ModifierFlags.In) result |= Modifier.IN;
    if (flags & ts.ModifierFlags.Readonly) result |= Modifier.READONLY;
    if (flags & ts.ModifierFlags.Out) result |= Modifier.OUT;
    if (flags & ts.ModifierFlags.Override) result |= Modifier.OVERRIDE;
    if (flags & ts.ModifierFlags.Ambient) result |= Modifier.DECLARE;
    return result;
}
