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
import {
    DEFAULT_ARK_CLASS_NAME,
    DEFAULT_ARK_METHOD_NAME,
    ExportType,
    ExportTypeValue,
    ImportType,
    Modifier,
} from "../dto/constants";
import { ClassDto, EtsFileDto, ExportInfoDto, FieldDto, ImportInfoDto, MethodDto, NamespaceDto } from "../dto/model";
import { ClassSignatureDto, FileSignatureDto, NamespaceSignatureDto } from "../dto/signatures";
import { VOID_TYPE } from "../dto/types";
import { TypeConverter } from "../types/convert";
import { modifiersOf } from "./astUtils";
import { ClassBuilder } from "./classBuilder";
import { Diagnostics } from "./diagnostics";
import { AnonymousRegistry, LoweringContext, MethodContext } from "./methodBuilder";
import { StmtLowerer } from "./stmtLowering";

/** Stable IR binding for anonymous `export default class/function` declarations. */
const DEFAULT_EXPORT_BINDING_NAME = "default";

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
    const anonymous: AnonymousRegistry = {
        defaultClassSignature: { name: DEFAULT_ARK_CLASS_NAME, declaringFile: fileSignature },
        methods: [],
        classes: [],
        nextMethodId: 0,
        nextClassId: 0,
    };
    const ctx: LoweringContext = {
        checker,
        converter,
        fileSignatureFor,
        diagnostics,
        anonymous,
        moduleFields: new Map(),
    };

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

        contents.classes.push(...this.ctx.anonymous.classes);
        // Anonymous closure methods retain the enclosing class so lexical
        // `this` has the same type as in the source method. Add anonymous
        // classes first because their methods may themselves contain closures.
        this.attachAnonymousMethods(contents);

        return {
            signature: this.fileSignature,
            namespaces: contents.namespaces,
            classes: contents.classes,
            importInfos: this.buildImportInfos(sourceFile),
            exportInfos: this.buildExportInfos(sourceFile),
        };
    }

    private attachAnonymousMethods(contents: ScopeContents): void {
        const classes: ClassDto[] = [...contents.classes];
        const collectNamespace = (namespace: NamespaceDto): void => {
            classes.push(...(namespace.classes ?? []));
            (namespace.namespaces ?? []).forEach(collectNamespace);
        };
        contents.namespaces.forEach(collectNamespace);

        // Index by key once: `find` with a per-candidate key recomputation is quadratic.
        const byKey = new Map<string, ClassDto>();
        for (const candidate of classes) {
            const key = classSignatureKey(candidate.signature);
            if (!byKey.has(key)) byKey.set(key, candidate);
        }

        for (const method of this.ctx.anonymous.methods) {
            const target = byKey.get(classSignatureKey(method.signature.declaringClass));
            if (target === undefined) {
                this.ctx.diagnostics.warn(
                    undefined,
                    `anonymous method target not found: ${method.signature.declaringClass.name}.${method.signature.name}`,
                );
                continue;
            }
            target.methods.push(method);
        }
    }

    // ------------------------------------------------------------------
    // Imports / exports
    // ------------------------------------------------------------------

    private buildImportInfos(sourceFile: ts.SourceFile): ImportInfoDto[] {
        const infos: ImportInfoDto[] = [];
        for (const statement of sourceFile.statements) {
            if (!ts.isImportDeclaration(statement)) {
                continue;
            }
            const importFrom = ts.isStringLiteral(statement.moduleSpecifier) ? statement.moduleSpecifier.text : "";
            const clause = statement.importClause;
            if (clause === undefined) {
                // Side-effect import: `import "module"`.
                infos.push(this.importInfo("", "", importFrom));
                continue;
            }
            if (clause.name !== undefined) {
                // Default import: `import d from "module"`.
                infos.push(this.importInfo(clause.name.text, "Identifier", importFrom));
            }
            const bindings = clause.namedBindings;
            if (bindings !== undefined) {
                if (ts.isNamespaceImport(bindings)) {
                    // `import * as ns from "module"`.
                    infos.push(this.importInfo(bindings.name.text, "NamespaceImport", importFrom));
                } else {
                    // `import { a, b as c } from "module"`.
                    for (const element of bindings.elements) {
                        const info = this.importInfo(element.name.text, "NamedImports", importFrom);
                        if (element.propertyName !== undefined) {
                            info.nameBeforeAs = element.propertyName.text;
                        }
                        infos.push(info);
                    }
                }
            }
        }
        return infos;
    }

    private importInfo(importName: string, importType: ImportType, importFrom: string): ImportInfoDto {
        return { importName, importType, importFrom, modifiers: 0 };
    }

    private buildExportInfos(sourceFile: ts.SourceFile): ExportInfoDto[] {
        const infos: ExportInfoDto[] = [];
        for (const statement of sourceFile.statements) {
            // Exported declarations: `export class C {}`, `export function f() {}`, ...
            const modifiers = modifiersOf(statement);
            if ((modifiers & Modifier.EXPORT) !== 0) {
                const name = declarationName(statement) ?? anonymousDefaultExportBinding(statement);
                if (name !== undefined) {
                    infos.push({
                        exportName: name,
                        exportType: exportTypeOfDeclaration(statement),
                        modifiers,
                    });
                } else if (ts.isVariableStatement(statement)) {
                    for (const decl of statement.declarationList.declarations) {
                        if (ts.isIdentifier(decl.name)) {
                            infos.push({ exportName: decl.name.text, exportType: ExportType.LOCAL, modifiers });
                        }
                    }
                }
                continue;
            }
            // Re-exports: `export { a, b as c } [from "module"]`, `export * from "module"`.
            if (ts.isExportDeclaration(statement)) {
                const exportFrom =
                    statement.moduleSpecifier !== undefined && ts.isStringLiteral(statement.moduleSpecifier)
                        ? statement.moduleSpecifier.text
                        : undefined;
                if (statement.exportClause === undefined) {
                    // `export * from "module"`.
                    const info: ExportInfoDto = { exportName: "*", exportType: ExportType.UNKNOWN, modifiers: 0 };
                    if (exportFrom !== undefined) info.exportFrom = exportFrom;
                    infos.push(info);
                } else if (ts.isNamedExports(statement.exportClause)) {
                    for (const element of statement.exportClause.elements) {
                        const info: ExportInfoDto = {
                            exportName: element.name.text,
                            exportType: this.exportTypeOfSymbol(element.name),
                            modifiers: 0,
                        };
                        if (element.propertyName !== undefined) info.nameBeforeAs = element.propertyName.text;
                        if (exportFrom !== undefined) info.exportFrom = exportFrom;
                        infos.push(info);
                    }
                } else if (ts.isNamespaceExport(statement.exportClause)) {
                    // `export * as ns from "module"` — star re-export with an alias
                    // (nameBeforeAs "*" drives the model's isStarReExport).
                    const info: ExportInfoDto = {
                        exportName: statement.exportClause.name.text,
                        exportType: ExportType.NAMESPACE,
                        nameBeforeAs: "*",
                        modifiers: 0,
                    };
                    if (exportFrom !== undefined) info.exportFrom = exportFrom;
                    infos.push(info);
                }
                continue;
            }
            // `export default <expr>;` / `export = <expr>;`
            if (ts.isExportAssignment(statement)) {
                const name = ts.isIdentifier(statement.expression) ? statement.expression.text : "default";
                infos.push({
                    exportName: name,
                    exportType: this.exportTypeOfSymbol(statement.expression),
                    modifiers: Modifier.DEFAULT,
                });
            }
        }
        return infos;
    }

    /** Export type of a re-exported name, resolved through the checker. */
    private exportTypeOfSymbol(node: ts.Node): ExportTypeValue {
        const decl = this.ctx.converter.symbolOf(node)?.declarations?.[0];
        return decl !== undefined ? exportTypeOfDeclaration(decl) : ExportType.UNKNOWN;
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

        const fields = this.registerModuleFields(defaultClassSignature, statements);
        const methods: MethodDto[] = [this.buildDefaultMethod(defaultClassSignature, statements)];

        for (const statement of statements) {
            if (ts.isFunctionDeclaration(statement)) {
                const name = declarationName(statement) ?? anonymousDefaultExportBinding(statement);
                if (name !== undefined) {
                    methods.push(this.classBuilder.buildMethodFromDecl(defaultClassSignature, statement, name));
                }
            } else if (ts.isClassDeclaration(statement)) {
                classes.push(this.classBuilder.buildClass(statement, anonymousDefaultExportBinding(statement)));
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
            fields,
            methods,
        });

        return { classes, namespaces };
    }

    /** Direct scope variables have one storage location shared by all scope methods. */
    private registerModuleFields(
        declaringClass: ClassSignatureDto,
        statements: readonly ts.Statement[],
    ): FieldDto[] {
        const fields: FieldDto[] = [];
        const registeredSymbols = new Set<ts.Symbol>();
        for (const declaration of scopeVariableDeclarations(statements)) {
            const declarationList = declaration.parent;
            const statement = declarationList.parent;
            const declarationModifiers = ts.isVariableStatement(statement) ? modifiersOf(statement) : 0;
            const isConst = (declarationList.flags & ts.NodeFlags.Const) !== 0;
            for (const identifier of bindingIdentifiers(declaration.name)) {
                const type = this.ctx.converter.typeOfNode(identifier);
                const signature = { declaringClass, name: identifier.text, type };
                const symbol = this.ctx.checker.getSymbolAtLocation(identifier);
                if (symbol !== undefined) {
                    if (registeredSymbols.has(symbol)) continue;
                    registeredSymbols.add(symbol);
                    this.ctx.moduleFields.set(symbol, signature);
                }
                fields.push({
                    signature,
                    modifiers: declarationModifiers | Modifier.STATIC | (isConst ? Modifier.CONST : 0),
                    decorators: [],
                    questionToken: false,
                    exclamationToken: false,
                });
            }
        }
        return fields;
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

    /** Loose scope statements -> `%dflt` method (impl continues below). */
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

function bindingIdentifiers(name: ts.BindingName): ts.Identifier[] {
    if (ts.isIdentifier(name)) return [name];
    return name.elements.flatMap((element) =>
        ts.isOmittedExpression(element) ? [] : bindingIdentifiers(element.name),
    );
}

/** Direct declarations plus function-scoped `var`s nested in top-level control flow. */
function scopeVariableDeclarations(statements: readonly ts.Statement[]): ts.VariableDeclaration[] {
    const declarations = new Set<ts.VariableDeclaration>();
    const visit = (node: ts.Node): void => {
        if (isNestedScopeBoundary(node)) return;
        if (ts.isVariableDeclarationList(node)) {
            const direct = ts.isVariableStatement(node.parent) && statements.includes(node.parent);
            const functionScoped = (node.flags & ts.NodeFlags.BlockScoped) === 0;
            if (direct || functionScoped) {
                node.declarations.forEach((declaration) => declarations.add(declaration));
            }
        }
        ts.forEachChild(node, visit);
    };
    statements.forEach(visit);
    return [...declarations];
}

function isNestedScopeBoundary(node: ts.Node): boolean {
    return ts.isFunctionDeclaration(node)
        || ts.isFunctionExpression(node)
        || ts.isArrowFunction(node)
        || ts.isMethodDeclaration(node)
        || ts.isConstructorDeclaration(node)
        || ts.isGetAccessorDeclaration(node)
        || ts.isSetAccessorDeclaration(node)
        || ts.isClassDeclaration(node)
        || ts.isModuleDeclaration(node)
        || ts.isClassStaticBlockDeclaration(node);
}

// ----------------------------------------------------------------------
// Export helpers
// ----------------------------------------------------------------------

function classSignatureKey(signature: ClassSignatureDto): string {
    const namespaceNames: string[] = [];
    for (
        let namespace = signature.declaringNamespace;
        namespace !== undefined;
        namespace = namespace.declaringNamespace
    ) {
        namespaceNames.unshift(namespace.name);
    }
    return [
        signature.declaringFile.projectName,
        signature.declaringFile.fileName,
        ...namespaceNames,
        signature.name,
    ].join("\u0000");
}

function declarationName(node: ts.Node): string | undefined {
    if (
        (ts.isClassDeclaration(node) ||
            ts.isInterfaceDeclaration(node) ||
            ts.isEnumDeclaration(node) ||
            ts.isFunctionDeclaration(node) ||
            ts.isTypeAliasDeclaration(node) ||
            ts.isModuleDeclaration(node)) &&
        node.name !== undefined &&
        ts.isIdentifier(node.name)
    ) {
        return node.name.text;
    }
    return undefined;
}

/** Anonymous default declarations need a stable IR binding because JavaScript has no source name to preserve. */
function anonymousDefaultExportBinding(node: ts.Node): string | undefined {
    const isAnonymousDeclaration =
        (ts.isClassDeclaration(node) || ts.isFunctionDeclaration(node)) && node.name === undefined;
    return isAnonymousDeclaration && (modifiersOf(node) & Modifier.DEFAULT) !== 0
        ? DEFAULT_EXPORT_BINDING_NAME
        : undefined;
}

function exportTypeOfDeclaration(node: ts.Node): ExportTypeValue {
    if (ts.isClassDeclaration(node) || ts.isEnumDeclaration(node)) {
        return ExportType.CLASS;
    }
    if (ts.isFunctionDeclaration(node) || ts.isMethodDeclaration(node)) {
        return ExportType.METHOD;
    }
    if (ts.isInterfaceDeclaration(node) || ts.isTypeAliasDeclaration(node)) {
        return ExportType.TYPE;
    }
    if (ts.isModuleDeclaration(node)) {
        return ExportType.NAMESPACE;
    }
    if (ts.isVariableStatement(node) || ts.isVariableDeclaration(node)) {
        return ExportType.LOCAL;
    }
    return ExportType.UNKNOWN;
}
