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
import { TEMP_LOCAL_PREFIX } from "../dto/constants";
import { BodyDto, ClassDto, LocalDeclDto, MethodDto, SourceSpanDto } from "../dto/model";
import { ClassSignatureDto, FileSignatureDto } from "../dto/signatures";
import { ClassTypeDto, LexicalEnvTypeDto, TypeDto, UNKNOWN_TYPE } from "../dto/types";
import { LocalDto } from "../dto/values";
import { TypeConverter } from "../types/convert";
import { CfgBuilder } from "./cfg";
import { Diagnostics } from "./diagnostics";

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
}

export interface ClosureCapture {
    identifier: ts.Identifier;
    outerLocal: LocalDto;
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
        return {
            fileName: this.ctx.fileSignatureFor(sourceFile).fileName,
            startOffset,
            endOffset,
            startLine: start.line,
            startColumn: start.character,
            endLine: end.line,
            endColumn: end.character,
            nodeKind: ts.SyntaxKind[node.kind],
        };
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

    /**
     * Resolve a source identifier to a method local by TypeScript symbol.
     *
     * Text alone is insufficient because block-scoped declarations may shadow
     * one another. The first symbol keeps the source name; later same-named
     * symbols receive deterministic `$N` suffixes.
     */
    localForIdentifier(node: ts.Identifier, type: TypeDto = UNKNOWN_TYPE): LocalDto {
        let symbol: ts.Symbol | undefined;
        try {
            symbol = this.checker.getSymbolAtLocation(node);
        } catch {
            // Unresolved identifiers (for example ambient globals in malformed
            // input) retain the old name-based fallback.
        }
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

    private freshSourceLocalName(base: string): string {
        if (!this.locals.has(base)) {
            this.localNameCounters.set(base, 1);
            return base;
        }
        let suffix = this.localNameCounters.get(base) ?? 1;
        let candidate: string;
        do {
            candidate = `${base}$${suffix++}`;
        } while (this.locals.has(candidate));
        this.localNameCounters.set(base, suffix);
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
     * captured-local loads, then `this := ThisRef`.
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
        for (const capture of captures) {
            const local = this.localForIdentifier(capture.identifier, capture.outerLocal.type);
            this.cfg.emit({
                _: "AssignStmt",
                left: local,
                right: {
                    _: "ClosureFieldRef",
                    base: { name: environment.name, type: environment.type },
                    fieldName: capture.outerLocal.name,
                    type: capture.outerLocal.type,
                },
            });
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
