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

import * as ts from "typescript";
import { DEFAULT_ARK_CLASS_NAME, FORBIDDEN_LOCAL_PREFIX, TEMP_LOCAL_PREFIX } from "../dto/constants";
import { BodyDto, ClassDto, LocalDeclDto, MethodDto, SourceSpanDto } from "../dto/model";
import { ClassSignatureDto, FieldSignatureDto, FileSignatureDto } from "../dto/signatures";
import { ClassTypeDto, LexicalEnvTypeDto, TypeDto, UNKNOWN_TYPE } from "../dto/types";
import { ClosureFieldRefDto, LocalDto, StaticFieldRefDto, ValueDto } from "../dto/values";
import { TypeConverter } from "../types/convert";
import { CfgBuilder } from "./cfg";
import { Diagnostics, syntaxKindName } from "./diagnostics";

/**
 * Registry for anonymous methods (`%AM<n>$<method>`, closures) and anonymous
 * classes (`%AC<n>$<method>`, object literals) created while lowering bodies.
 * Methods are attached to their recorded declaring class and anonymous classes
 * to the top-level class list when the file is assembled.
 */
export interface AnonymousRegistry {
    defaultClassSignature: ClassSignatureDto;
    methods: MethodDto[];
    classes: ClassDto[];
    nextMethodId: number;
    nextClassId: number;
}

/** Shared per-file lowering context. */
export interface LoweringContext {
    checker: ts.TypeChecker;
    converter: TypeConverter;
    fileSignatureFor(sf: ts.SourceFile): FileSignatureDto;
    diagnostics: Diagnostics;
    anonymous: AnonymousRegistry;
    /** File/namespace variables represented as static fields of the owning %dflt class. */
    moduleFields: Map<ts.Symbol, FieldSignatureDto>;
}

export interface ClosureCapture {
    identifier: ts.Identifier;
    outerLocal: LocalDto;
    type: TypeDto;
    forwardedFieldName?: string;
}

/**
 * Per-method lowering state: locals table, temp counter, CFG builder.
 *
 * Prologue convention (matches ArkAnalyzer):
 *   `param_i := ParameterRef(i)` for each parameter, then `this := ThisRef(classType)`.
 */
export class MethodContext {
    readonly cfg = new CfgBuilder();
    private readonly locals = new Map<string, LocalDto>();
    private readonly symbolLocals = new Map<ts.Symbol, LocalDto>();
    private readonly capturedRefs = new Map<ts.Symbol, ClosureFieldRefDto>();
    private readonly localNameCounters = new Map<string, number>();
    private tempCount = 0;
    private closureEnvCount = 0;

    constructor(
        readonly ctx: LoweringContext,
        readonly declaringClass: ClassSignatureDto,
        readonly methodName: string,
        /** In static methods `this` refers to the class itself (static field access). */
        readonly isStaticMethod: boolean = false,
    ) {}

    get checker(): ts.TypeChecker {
        return this.ctx.checker;
    }

    get converter(): TypeConverter {
        return this.ctx.converter;
    }

    get diagnostics(): Diagnostics {
        return this.ctx.diagnostics;
    }

    /** Execute lowering while attributing emitted EtsIR statements to [node]. */
    withOrigin<T>(node: ts.Node, action: () => T): T {
        return this.cfg.withOrigin(this.sourceSpan(node), action);
    }

    private sourceSpan(node: ts.Node): SourceSpanDto | undefined {
        if (node.pos < 0 || node.end < 0) return undefined;
        const sourceFile = node.getSourceFile();
        const startOffset = node.getStart(sourceFile, false);
        const endOffset = node.getEnd();
        const start = sourceFile.getLineAndCharacterOfPosition(startOffset);
        const end = sourceFile.getLineAndCharacterOfPosition(endOffset);
        // The enclosing file name is already present in the file signature; repeating it in
        // every origin entry inflates the JSON, so emit it only for foreign source files.
        const fileName = this.ctx.fileSignatureFor(sourceFile).fileName;
        const span: SourceSpanDto = {
            startOffset,
            endOffset,
            startLine: start.line,
            startColumn: start.character,
            endLine: end.line,
            endColumn: end.character,
            nodeKind: syntaxKindName(node.kind),
        };
        if (fileName !== this.declaringClass.declaringFile.fileName) {
            span.fileName = fileName;
        }
        return span;
    }

    thisType(): ClassTypeDto {
        return { _: "ClassType", signature: this.declaringClass };
    }

    /**
     * Local by name, declared on first use.
     * A known (non-Unknown) type upgrades a previously recorded UnknownType.
     */
    getOrCreateLocal(name: string, type: TypeDto = UNKNOWN_TYPE): LocalDto {
        const existing = this.locals.get(name);
        if (existing !== undefined) {
            this.upgradeLocalType(existing, type);
            return existing;
        }
        const local: LocalDto = { _: "Local", name, type };
        this.locals.set(name, local);
        return local;
    }

    /** Preserve a selected value before later evaluation; private temps are already stable. */
    snapshotToLocal(value: ValueDto, type: TypeDto): LocalDto {
        if (value._ === "Local" && value.name.startsWith(TEMP_LOCAL_PREFIX)) {
            return value;
        }
        const snapshot = this.newTemp(type);
        this.cfg.emit({ _: "AssignStmt", left: snapshot, right: value });
        return snapshot;
    }

    /**
     * Resolve a source identifier to a method local by TypeScript symbol.
     *
     * Text alone is insufficient because block-scoped declarations may shadow
     * one another. The first symbol keeps the source name; later same-named
     * symbols receive deterministic `$N` suffixes.
     */
    localForIdentifier(node: ts.Identifier, type: TypeDto = UNKNOWN_TYPE): LocalDto {
        const symbol = this.symbolForIdentifier(node);
        if (symbol === undefined) {
            return this.getOrCreateLocal(node.text, type);
        }

        const existing = this.symbolLocals.get(symbol);
        if (existing !== undefined) {
            this.upgradeLocalType(existing, type);
            return existing;
        }

        const name = this.freshSourceLocalName(node.text);
        const local: LocalDto = { _: "Local", name, type };
        this.locals.set(name, local);
        this.symbolLocals.set(symbol, local);
        return local;
    }

    /** Static storage shared by the scope default method and its free functions. */
    moduleFieldForIdentifier(node: ts.Identifier): StaticFieldRefDto | undefined {
        const symbol = this.symbolForIdentifier(node);
        if (symbol === undefined) return undefined;
        let field = this.ctx.moduleFields.get(symbol);
        if (field === undefined) {
            field = this.moduleFieldFromSymbol(symbol);
            if (field !== undefined) this.ctx.moduleFields.set(symbol, field);
        }
        return field === undefined ? undefined : { _: "StaticFieldRef", field };
    }

    /** Captured slot used directly for both reads and writes in a lifted function. */
    capturedRefForIdentifier(node: ts.Identifier): ClosureFieldRefDto | undefined {
        const symbol = this.symbolForIdentifier(node);
        return symbol === undefined ? undefined : this.capturedRefs.get(symbol);
    }

    /** Preserve the original lexical-environment reference for a transitive capture. */
    captureForIdentifier(node: ts.Identifier, type: TypeDto): ClosureCapture {
        const captured = this.capturedRefForIdentifier(node);
        if (captured === undefined) {
            return { identifier: node, outerLocal: this.localForIdentifier(node, type), type };
        }
        return {
            identifier: node,
            outerLocal: this.getOrCreateLocal(captured.base.name, captured.base.type),
            type,
            forwardedFieldName: captured.fieldName,
        };
    }

    private symbolForIdentifier(node: ts.Identifier): ts.Symbol | undefined {
        // Unresolved identifiers (for example ambient globals in malformed input)
        // yield undefined here and retain the name-based fallback. Shorthand-property
        // names are redirected to their value symbol inside `symbolOf`.
        return this.converter.symbolOf(node);
    }

    /** Derive storage for an imported scope variable from its declaration. */
    private moduleFieldFromSymbol(symbol: ts.Symbol): FieldSignatureDto | undefined {
        const identifier = symbol.declarations
            ?.map(declaredBindingIdentifier)
            .find((candidate): candidate is ts.Identifier => candidate !== undefined);
        if (identifier === undefined) return undefined;
        if (identifier.getSourceFile().isDeclarationFile) return undefined;

        let declaration: ts.Node | undefined = identifier.parent;
        while (declaration !== undefined && !ts.isVariableDeclaration(declaration)) {
            declaration = declaration.parent;
        }
        if (declaration === undefined) return undefined;
        const variableDeclaration = declaration;
        const declarationList = variableDeclaration.parent;
        if (!ts.isVariableDeclarationList(declarationList)) return undefined;
        const statement = declarationList.parent;
        const directScope = ts.isVariableStatement(statement)
            && (ts.isSourceFile(statement.parent) || ts.isModuleBlock(statement.parent));
        const functionScoped = (declarationList.flags & ts.NodeFlags.BlockScoped) === 0;
        if (!directScope && (!functionScoped || moduleScopeOf(variableDeclaration) === undefined)) return undefined;

        const declaringClass: ClassSignatureDto = {
            name: DEFAULT_ARK_CLASS_NAME,
            declaringFile: this.ctx.fileSignatureFor(identifier.getSourceFile()),
        };
        const moduleScope = directScope ? statement.parent : moduleScopeOf(variableDeclaration);
        if (moduleScope !== undefined && ts.isModuleBlock(moduleScope) && ts.isModuleDeclaration(moduleScope.parent)) {
            const namespace = this.converter.namespaceSignatureOf(moduleScope.parent);
            if (namespace !== undefined) declaringClass.declaringNamespace = namespace;
        }
        return { declaringClass, name: identifier.text, type: this.converter.typeOfNode(identifier) };
    }

    private freshSourceLocalName(base: string): string {
        const safeBase = base.startsWith(FORBIDDEN_LOCAL_PREFIX) ? `$source$${base}` : base;
        if (!this.locals.has(safeBase)) {
            this.localNameCounters.set(safeBase, 1);
            return safeBase;
        }
        let suffix = this.localNameCounters.get(safeBase) ?? 1;
        let candidate: string;
        do {
            candidate = `${safeBase}$${suffix++}`;
        } while (this.locals.has(candidate));
        this.localNameCounters.set(safeBase, suffix);
        return candidate;
    }

    private upgradeLocalType(local: LocalDto, type: TypeDto): void {
        if (local.type._ === "UnknownType" && type._ !== "UnknownType") {
            local.type = type;
        }
    }

    /** Fresh temp local `%N`. */
    newTemp(type: TypeDto = UNKNOWN_TYPE): LocalDto {
        const name = `${TEMP_LOCAL_PREFIX}${this.tempCount++}`;
        const local: LocalDto = { _: "Local", name, type };
        this.locals.set(name, local);
        return local;
    }

    /** Reserve the lexical-environment local used implicitly by a closure value. */
    newClosureEnvironment(type: LexicalEnvTypeDto): LocalDto {
        let name: string;
        do {
            name = `%closures${this.closureEnvCount++}`;
        } while (this.locals.has(name));
        return this.getOrCreateLocal(name, type);
    }

    /** Emit the standard prologue: parameter assignments, then `this := ThisRef`. */
    emitPrologue(parameters: { name: string; type: TypeDto; identifier?: ts.Identifier }[]): void {
        parameters.forEach((param, index) => {
            const local = param.identifier !== undefined
                ? this.localForIdentifier(param.identifier, param.type)
                : this.getOrCreateLocal(param.name, param.type);
            this.cfg.emit({
                _: "AssignStmt",
                left: local,
                right: { _: "ParameterRef", index, type: param.type },
            });
        });
        this.emitThisAssignment();
    }

    /**
     * ArkAnalyzer closure prologue: environment parameter, regular parameters,
     * captured-slot bindings, then `this := ThisRef`.
     */
    emitClosurePrologue(
        environmentName: string,
        environmentType: LexicalEnvTypeDto,
        captures: ClosureCapture[],
        parameters: { name: string; type: TypeDto; identifier?: ts.Identifier }[],
    ): void {
        const environment = this.getOrCreateLocal(environmentName, environmentType);
        this.cfg.emit({
            _: "AssignStmt",
            left: environment,
            right: { _: "ParameterRef", index: 0, type: environmentType },
        });
        parameters.forEach((param, index) => {
            const local = param.identifier !== undefined
                ? this.localForIdentifier(param.identifier, param.type)
                : this.getOrCreateLocal(param.name, param.type);
            this.cfg.emit({
                _: "AssignStmt",
                left: local,
                right: { _: "ParameterRef", index: index + 1, type: param.type },
            });
        });
        const forwardedEnvironments = new Map<string, LocalDto>();
        for (const capture of captures) {
            const symbol = this.symbolForIdentifier(capture.identifier);
            if (symbol !== undefined) {
                let base: LocalDto = environment;
                let fieldName = capture.outerLocal.name;
                if (capture.forwardedFieldName !== undefined) {
                    let forwardedEnvironment = forwardedEnvironments.get(capture.outerLocal.name);
                    if (forwardedEnvironment === undefined) {
                        forwardedEnvironment = this.getOrCreateLocal(
                            capture.outerLocal.name,
                            capture.outerLocal.type,
                        );
                        this.cfg.emit({
                            _: "AssignStmt",
                            left: forwardedEnvironment,
                            right: {
                                _: "ClosureFieldRef",
                                base: { name: environment.name, type: environment.type },
                                fieldName: capture.outerLocal.name,
                                type: capture.outerLocal.type,
                            },
                        });
                        forwardedEnvironments.set(capture.outerLocal.name, forwardedEnvironment);
                    }
                    base = forwardedEnvironment;
                    fieldName = capture.forwardedFieldName;
                }
                this.capturedRefs.set(symbol, {
                    _: "ClosureFieldRef",
                    base: { name: base.name, type: base.type },
                    fieldName,
                    type: capture.type,
                });
            }
        }
        this.emitThisAssignment();
    }

    private emitThisAssignment(): void {
        const thisType = this.thisType();
        const thisLocal = this.getOrCreateLocal("this", thisType);
        this.cfg.emit({
            _: "AssignStmt",
            left: thisLocal,
            right: { _: "ThisRef", type: thisType },
        });
    }

    build(): BodyDto {
        const { cfg, stmtOrigins } = this.cfg.finalize();
        const locals: LocalDeclDto[] = [...this.locals.values()].map((l) => ({
            name: l.name,
            type: l.type,
        }));
        return stmtOrigins.length === 0 ? { locals, cfg } : { locals, cfg, stmtOrigins };
    }
}

function declaredBindingIdentifier(declaration: ts.Declaration): ts.Identifier | undefined {
    if (ts.isVariableDeclaration(declaration) && ts.isIdentifier(declaration.name)) return declaration.name;
    if (ts.isBindingElement(declaration) && ts.isIdentifier(declaration.name)) return declaration.name;
    return undefined;
}

function moduleScopeOf(node: ts.Node): ts.SourceFile | ts.ModuleBlock | undefined {
    for (let current: ts.Node | undefined = node.parent; current !== undefined; current = current.parent) {
        if (ts.isSourceFile(current) || ts.isModuleBlock(current)) return current;
        if (
            ts.isFunctionDeclaration(current)
            || ts.isFunctionExpression(current)
            || ts.isArrowFunction(current)
            || ts.isMethodDeclaration(current)
            || ts.isConstructorDeclaration(current)
            || ts.isGetAccessorDeclaration(current)
            || ts.isSetAccessorDeclaration(current)
            || ts.isClassStaticBlockDeclaration(current)
        ) return undefined;
    }
    return undefined;
}
