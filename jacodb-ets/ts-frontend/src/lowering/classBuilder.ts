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
 * Class-like declarations -> ClassDto.
 *
 * Conventions (verified against ArkAnalyzer ground truth):
 *  - classes: synthesized `%instInit` / `%statInit` initializer methods
 *    (prologue `this := ThisRef`, field-init assignments, return void),
 *    constructor always present (synthesized if absent) and shaped as
 *    `this := ThisRef; this.%instInit(); ...body...; return this`;
 *  - enums: category 3, members as STATIC fields typed EnumValueType,
 *    values assigned in `%statInit`;
 *  - interfaces: category 2, bodyless methods, no initializers.
 */

import * as ts from "typescript";
import { isStrictMode } from "./strictMode";
import {
    CONSTRUCTOR_NAME,
    ClassCategory,
    INSTANCE_INIT_METHOD_NAME,
    Modifier,
    PATTERN_PARAMETER_PREFIX,
    STATIC_INIT_METHOD_NAME,
} from "../dto/constants";
import { BodyDto, ClassDto, FieldDto, MethodDto } from "../dto/model";
import { ClassSignatureDto, UNKNOWN_FILE_SIGNATURE } from "../dto/signatures";
import { ClassTypeDto, TypeDto, NUMBER_TYPE, STRING_TYPE, UNKNOWN_TYPE, VOID_TYPE } from "../dto/types";
import { buildParameters, classMemberKey, ClassMemberKey, decoratorsOf, memberName, modifiersOf, parameterType, returnTypeOf, unsupportedClassDeclarationReason } from "./astUtils";
import { unsupportedStmt } from "./diagnostics";
import { LoweringError, constant } from "./exprLowering";
import { LoweringContext, MethodContext } from "./methodBuilder";
import { StmtLowerer, ensureSupportedBindingPattern } from "./stmtLowering";

type ClassMemberDecl = ts.MethodDeclaration | ts.GetAccessorDeclaration | ts.SetAccessorDeclaration;

export class ClassBuilder {
    constructor(private readonly ctx: LoweringContext) {}

    // ------------------------------------------------------------------
    // Classes
    // ------------------------------------------------------------------

    buildClass(decl: ts.ClassLikeDeclaration, nameOverride?: string): ClassDto {
        const convertedSignature = this.ctx.converter.classSignatureOf(decl);
        const signature = nameOverride === undefined
            ? convertedSignature
            : { ...convertedSignature, name: nameOverride };
        const definitionFailure = ts.isClassDeclaration(decl)
            ? unsupportedClassDeclarationReason(decl, this.ctx.checker)
            : undefined;
        const superClass = this.superClassInfo(decl);

        const instanceFields: ts.PropertyDeclaration[] = [];
        const staticInitializers: (ts.PropertyDeclaration | ts.ClassStaticBlockDeclaration)[] = [];
        const fields: FieldDto[] = [];
        const methods: MethodDto[] = [];
        const computedMethods = new Set<MethodDto>();
        let ctorDecl: ts.ConstructorDeclaration | undefined;

        for (const member of decl.members) {
            const key = member.name === undefined ? undefined : classMemberKey(member.name, this.ctx.checker);
            const hasUnsupportedComputedName = member.name !== undefined && ts.isComputedPropertyName(member.name)
                && (key === undefined || key.name === CONSTRUCTOR_NAME);
            if (hasUnsupportedComputedName) {
                // Keep independent bodies without inventing storage for an unevaluated key.
                continue;
            }

            if (ts.isPropertyDeclaration(member)) {
                const field = this.buildField(signature, member);
                const previous = fields.findIndex((candidate) => candidate.signature.name === field.signature.name
                    && candidate.signature.wellKnownSymbol === field.signature.wellKnownSymbol
                    && (candidate.modifiers & Modifier.STATIC) === (field.modifiers & Modifier.STATIC));
                if (previous >= 0) fields.splice(previous, 1);
                fields.push(field);
                if (member.initializer !== undefined) {
                    if (isStatic(member)) {
                        staticInitializers.push(member);
                    } else {
                        instanceFields.push(member);
                    }
                }
            } else if (ts.isConstructorDeclaration(member)) {
                if (member.body !== undefined) {
                    ctorDecl = member;
                }
                // Parameter properties (constructor(private x: number)) become fields.
                member.parameters.forEach((p, index) => {
                    if (hasParameterPropertyModifier(p) && ts.isIdentifier(p.name)) {
                        fields.push(this.buildParameterPropertyField(signature, p, index));
                    }
                });
            } else if (
                ts.isMethodDeclaration(member) ||
                ts.isGetAccessorDeclaration(member) ||
                ts.isSetAccessorDeclaration(member)
            ) {
                const method = this.buildMethodFromDecl(signature, member);
                const sameKey = (candidate: MethodDto): boolean => candidate.signature.name === method.signature.name
                    && candidate.signature.wellKnownSymbol === method.signature.wellKnownSymbol
                    && (candidate.modifiers & Modifier.STATIC) === (method.modifiers & Modifier.STATIC);
                const replacesComputedKey = ts.isComputedPropertyName(member.name)
                    || methods.some((candidate) => computedMethods.has(candidate) && sameKey(candidate));
                const replacesMethod = definitionFailure === undefined && replacesComputedKey
                    && ts.isMethodDeclaration(member) && member.body !== undefined;
                if (replacesMethod) {
                    // Runtime definitions replace earlier bodies; overload declarations are retained.
                    for (let index = methods.length - 1; index >= 0; index--) {
                        if (methods[index].body !== undefined && sameKey(methods[index])) methods.splice(index, 1);
                    }
                }
                if (ts.isComputedPropertyName(member.name)) computedMethods.add(method);
                methods.push(method);
            } else if (ts.isIndexSignatureDeclaration(member)) {
                fields.push(this.buildIndexSignatureField(signature, member));
            } else if (ts.isSemicolonClassElement(member)) {
                // ignore
            } else if (ts.isClassStaticBlockDeclaration(member)) {
                staticInitializers.push(member);
            } else {
                this.ctx.diagnostics.warn(member, `unsupported class member: ${ts.SyntaxKind[member.kind]}`);
            }
        }

        if (definitionFailure === undefined) {
            methods.push(this.buildInstInit(signature, instanceFields));
            methods.push(this.buildStatInit(signature, staticInitializers));
            methods.push(
                ctorDecl !== undefined
                    ? this.buildConstructor(signature, ctorDecl, superClass?.signature)
                    : this.synthesizeDefaultConstructor(signature, decl, superClass),
            );
        } else {
            this.ctx.diagnostics.warn(decl, `class bodies retained without definition semantics: ${definitionFailure}`);
            // Lower initializers to retain their lifted closure bodies, then discard
            // the executable initialization. No captured environment is constructed.
            const initializers = [
                this.buildInstInit(signature, instanceFields),
                this.buildStatInit(signature, staticInitializers),
            ];
            for (const initializer of initializers) {
                initializer.body = this.unsupportedClassLifecycle(signature, decl, initializer.signature.name).body;
                methods.push(initializer);
            }
            methods.push(this.unsupportedClassLifecycle(signature, decl, CONSTRUCTOR_NAME, ctorDecl));
        }

        const result: ClassDto = {
            signature,
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
            category: ClassCategory.CLASS,
            superClassName: superClassNameOf(decl) ?? "",
            implementedInterfaceNames: implementedInterfacesOf(decl),
            fields,
            methods,
        };
        const typeParameters = this.ctx.converter.convertTypeParameters(decl.typeParameters);
        if (typeParameters !== undefined) {
            result.typeParameters = typeParameters;
        }
        return result;
    }

    private unsupportedClassLifecycle(
        declaringClass: ClassSignatureDto,
        declaration: ts.ClassLikeDeclaration,
        name: string,
        constructor?: ts.ConstructorDeclaration,
    ): MethodDto {
        const m = new MethodContext(this.ctx, declaringClass, name);
        m.emitPrologue([]);
        m.withOrigin(declaration, () => m.cfg.emit(unsupportedStmt(declaration)));

        const parameters = constructor === undefined ? [] : buildParameters(this.ctx, constructor).parameters;
        const returnType: TypeDto = name === CONSTRUCTOR_NAME
            ? { _: "ClassType", signature: declaringClass }
            : VOID_TYPE;
        return {
            signature: { declaringClass, name, parameters, returnType },
            modifiers: name === STATIC_INIT_METHOD_NAME ? Modifier.STATIC : 0,
            decorators: [],
            isStrictMode: true,
            body: m.build(),
        };
    }

    // ------------------------------------------------------------------
    // Interfaces
    // ------------------------------------------------------------------

    buildInterface(decl: ts.InterfaceDeclaration): ClassDto {
        const signature = this.ctx.converter.classSignatureOf(decl);
        const fields: FieldDto[] = [];
        const methods: MethodDto[] = [];

        for (const member of decl.members) {
            if (ts.isPropertySignature(member)) {
                const field: FieldDto = {
                    signature: {
                        declaringClass: signature,
                        name: memberName(member.name),
                        type:
                            member.type !== undefined
                                ? this.ctx.converter.convertTypeNode(member.type)
                                : UNKNOWN_TYPE,
                    },
                    modifiers: modifiersOf(member),
                    decorators: [],
                    questionToken: member.questionToken !== undefined,
                    exclamationToken: false,
                };
                fields.push(field);
            } else if (ts.isIndexSignatureDeclaration(member)) {
                fields.push(this.buildIndexSignatureField(signature, member));
            } else if (ts.isMethodSignature(member)) {
                methods.push(this.buildBodylessMethod(signature, member));
            } else {
                this.ctx.diagnostics.warn(member, `unsupported interface member: ${ts.SyntaxKind[member.kind]}`);
            }
        }

        const result: ClassDto = {
            signature,
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
            category: ClassCategory.INTERFACE,
            superClassName: "",
            implementedInterfaceNames: extendedInterfacesOf(decl),
            fields,
            methods,
        };
        const typeParameters = this.ctx.converter.convertTypeParameters(decl.typeParameters);
        if (typeParameters !== undefined) {
            result.typeParameters = typeParameters;
        }
        return result;
    }

    private buildIndexSignatureField(
        declaringClass: ClassSignatureDto,
        member: ts.IndexSignatureDeclaration,
    ): FieldDto {
        const parameters = member.parameters.map((parameter) => {
            const name = parameter.name.getText();
            const type = parameter.type?.getText() ?? "unknown";
            return `${name}: ${type}`;
        });
        return {
            signature: {
                declaringClass,
                name: `[${parameters.join(", ")}]`,
                type: this.ctx.converter.convertTypeNode(member.type),
            },
            modifiers: modifiersOf(member),
            decorators: [],
            questionToken: false,
            exclamationToken: false,
        };
    }

    // ------------------------------------------------------------------
    // Enums
    // ------------------------------------------------------------------

    buildEnum(decl: ts.EnumDeclaration): ClassDto {
        const signature = this.ctx.converter.classSignatureOf(decl);

        const fields: FieldDto[] = decl.members.map((member) => ({
            signature: {
                declaringClass: signature,
                name: memberName(member.name),
                type: { _: "EnumValueType", signature, name: memberName(member.name) },
            },
            modifiers: Modifier.STATIC,
            decorators: [],
            questionToken: false,
            exclamationToken: false,
        }));

        // %statInit assigns member values.
        const m = new MethodContext(this.ctx, signature, STATIC_INIT_METHOD_NAME, /* isStaticMethod */ true);
        m.emitPrologue([]);
        const lowerer = new StmtLowerer(m);
        let autoValue = 0;
        for (const member of decl.members) {
            const name = memberName(member.name);
            let value;
            const constValue = this.constantValueOf(member);
            if (constValue !== undefined) {
                value =
                    typeof constValue === "number"
                        ? constant(String(constValue), NUMBER_TYPE)
                        : constant(constValue, STRING_TYPE);
                if (typeof constValue === "number") {
                    autoValue = constValue + 1;
                }
            } else if (member.initializer !== undefined) {
                value = lowerer.expr.lowerToImmediate(member.initializer);
            } else {
                value = constant(String(autoValue++), NUMBER_TYPE);
            }
            m.cfg.emit({
                _: "AssignStmt",
                left: {
                    _: "StaticFieldRef",
                    field: {
                        declaringClass: signature,
                        name,
                        type: { _: "EnumValueType", signature, name },
                    },
                },
                right: value,
            });
        }
        m.cfg.ret();

        return {
            signature,
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
            category: ClassCategory.ENUM,
            superClassName: "",
            implementedInterfaceNames: [],
            fields,
            methods: [
                {
                    signature: { declaringClass: signature, name: STATIC_INIT_METHOD_NAME, parameters: [], returnType: VOID_TYPE },
                    modifiers: Modifier.STATIC,
                    decorators: [],
                    isStrictMode: isStrictMode(decl),
                    body: m.build(),
                },
            ],
        };
    }

    private constantValueOf(member: ts.EnumMember): string | number | undefined {
        try {
            return this.ctx.checker.getConstantValue(member);
        } catch {
            return undefined;
        }
    }

    // ------------------------------------------------------------------
    // Methods
    // ------------------------------------------------------------------

    buildMethodFromDecl(
        declaringClass: ClassSignatureDto,
        decl: ClassMemberDecl | ts.FunctionDeclaration,
        nameOverride?: string,
    ): MethodDto {
        const key = decl.name === undefined ? { name: "" } : this.memberKey(decl.name);
        const name = nameOverride ?? key.name;
        const { parameters, prologueParams } = buildParameters(this.ctx, decl);
        const returnType = returnTypeOf(this.ctx, decl);

        const method: MethodDto = {
            isStrictMode: isStrictMode(decl),
            signature: { declaringClass, ...key, name, parameters, returnType },
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
        };
        const typeParameters = this.ctx.converter.convertTypeParameters(decl.typeParameters);
        if (typeParameters !== undefined) {
            method.typeParameters = typeParameters;
        }

        if (decl.body !== undefined) {
            const isStaticMethod = (modifiersOf(decl) & Modifier.STATIC) !== 0;
            const patternError = this.parameterPatternError(decl);
            if (patternError !== undefined) {
                method.body = this.unsupportedParameterBody(decl, declaringClass, name, patternError, isStaticMethod);
                return method;
            }

            const m = new MethodContext(this.ctx, declaringClass, name, isStaticMethod);
            const lowerer = new StmtLowerer(m);
            m.emitPrologue(prologueParams, (parameter, local) =>
                lowerer.lowerParameterBinding(parameter, local),
            );
            lowerer.lowerStatements(decl.body.statements);
            method.body = m.build();
        }
        return method;
    }

    buildBodylessMethod(declaringClass: ClassSignatureDto, decl: ts.MethodSignature): MethodDto {
        const { parameters } = buildParameters(this.ctx, decl);
        const method: MethodDto = {
            signature: {
                declaringClass,
                name: memberName(decl.name),
                parameters,
                returnType: returnTypeOf(this.ctx, decl),
            },
            modifiers: modifiersOf(decl),
            decorators: [],
        };
        const typeParameters = this.ctx.converter.convertTypeParameters(decl.typeParameters);
        if (typeParameters !== undefined) {
            method.typeParameters = typeParameters;
        }

        return method;
    }

    private buildConstructor(
        declaringClass: ClassSignatureDto,
        decl: ts.ConstructorDeclaration,
        superClass: ClassSignatureDto | undefined,
    ): MethodDto {
        const { parameters, prologueParams } = buildParameters(this.ctx, decl);
        const classType: ClassTypeDto = { _: "ClassType", signature: declaringClass };
        const patternError = this.parameterPatternError(decl);
        if (patternError !== undefined) {
            return {
                signature: { declaringClass, name: CONSTRUCTOR_NAME, parameters, returnType: classType },
                modifiers: modifiersOf(decl),
                decorators: decoratorsOf(decl),
                isStrictMode: true,
                body: this.unsupportedParameterBody(decl, declaringClass, CONSTRUCTOR_NAME, patternError),
            };
        }

        const m = new MethodContext(this.ctx, declaringClass, CONSTRUCTOR_NAME);
        const lowerer = new StmtLowerer(m);
        m.emitPrologue(prologueParams, (parameter, local) =>
            lowerer.lowerParameterBinding(parameter, local),
        );
        const thisLocal = m.getOrCreateLocal("this", classType);
        const emitInitializers = (): void => {
            // Parameter properties are initialized at constructor entry (after
            // super() in a derived class), before ordinary instance fields.
            this.emitParameterProperties(m, thisLocal, declaringClass, decl.parameters);
            this.emitInstInitCall(m, declaringClass);
        };
        if (decl.body !== undefined) {
            if (superClass === undefined) {
                emitInitializers();
                lowerer.lowerStatements(decl.body.statements);
            } else {
                // In a derived class, JavaScript initializes instance fields and
                // parameter properties only after the base constructor returns.
                new StmtLowerer(m, emitInitializers).lowerStatements(decl.body.statements);
            }
        }
        if (m.cfg.isUnterminated()) {
            m.cfg.ret(thisLocal);
        }
        return {
            signature: { declaringClass, name: CONSTRUCTOR_NAME, parameters, returnType: classType },
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
            isStrictMode: true,
            body: m.build(),
        };
    }

    private parameterPatternError(decl: ts.SignatureDeclarationBase): string | undefined {
        try {
            for (const parameter of decl.parameters) {
                if (!ts.isIdentifier(parameter.name)) {
                    ensureSupportedBindingPattern(parameter.name);
                }
            }
        } catch (error) {
            if (error instanceof LoweringError) {
                return error.message;
            }
            throw error;
        }
        return undefined;
    }

    private unsupportedParameterBody(
        decl: ts.SignatureDeclarationBase,
        declaringClass: ClassSignatureDto,
        name: string,
        reason: string,
        isStaticMethod?: boolean,
    ): BodyDto {
        this.ctx.diagnostics.warn(decl, `unsupported parameter binding: ${reason}`);
        const m = new MethodContext(this.ctx, declaringClass, name, isStaticMethod);
        m.withOrigin(decl, () => m.cfg.emit(unsupportedStmt(decl)));
        return m.build();
    }

    private synthesizeDefaultConstructor(
        declaringClass: ClassSignatureDto,
        decl: ts.ClassLikeDeclaration,
        superClass?: { signature: ClassSignatureDto; constructorDecl?: ts.ConstructorDeclaration },
    ): MethodDto {
        const classType: ClassTypeDto = { _: "ClassType", signature: declaringClass };
        const m = new MethodContext(this.ctx, declaringClass, CONSTRUCTOR_NAME);
        const inherited = superClass?.constructorDecl !== undefined
            ? buildParameters(this.ctx, superClass.constructorDecl)
            : { parameters: [], prologueParams: [] };
        const hasRestParameter = inherited.parameters.some((parameter) => parameter.isRest === true);
        if (hasRestParameter) {
            // ParameterRef for a rest parameter is already a packed array. Passing
            // it as one call argument would pack it again in the base constructor.
            this.ctx.diagnostics.warn(decl, "unsupported default constructor forwarding of rest arguments");
            m.withOrigin(decl, () => m.cfg.emit(unsupportedStmt(decl)));
            return {
                signature: {
                    declaringClass,
                    name: CONSTRUCTOR_NAME,
                    parameters: inherited.parameters,
                    returnType: classType,
                },
                modifiers: 0,
                decorators: [],
                isStrictMode: true,
                body: m.build(),
            };
        }

        m.emitPrologue(inherited.prologueParams);
        if (superClass !== undefined) {
            const thisLocal = m.getOrCreateLocal("this", classType);
            m.cfg.emit({
                _: "CallStmt",
                expr: {
                    _: "InstanceCallExpr",
                    instance: thisLocal,
                    method: {
                        declaringClass: superClass.signature,
                        name: CONSTRUCTOR_NAME,
                        parameters: inherited.parameters,
                        returnType: { _: "ClassType", signature: superClass.signature },
                    },
                    args: inherited.prologueParams.map((param) =>
                        param.identifier !== undefined
                            ? m.localForIdentifier(param.identifier, param.type)
                            : m.getOrCreateLocal(param.name, param.type),
                    ),
                },
            });
        }
        this.emitInstInitCall(m, declaringClass);
        m.cfg.ret(m.getOrCreateLocal("this", classType));
        return {
            signature: {
                declaringClass,
                name: CONSTRUCTOR_NAME,
                parameters: inherited.parameters,
                returnType: classType,
            },
            modifiers: 0,
            decorators: [],
            isStrictMode: true,
            body: m.build(),
        };
    }

    private emitParameterProperties(
        m: MethodContext,
        thisLocal: ReturnType<MethodContext["getOrCreateLocal"]>,
        declaringClass: ClassSignatureDto,
        parameters: readonly ts.ParameterDeclaration[],
    ): void {
        for (const p of parameters) {
            if (hasParameterPropertyModifier(p) && ts.isIdentifier(p.name)) {
                const paramType = parameterType(this.ctx, p);
                m.cfg.emit({
                    _: "AssignStmt",
                    left: {
                        _: "InstanceFieldRef",
                        instance: thisLocal,
                        field: { declaringClass, name: p.name.text, type: paramType },
                    },
                    right: m.localForIdentifier(p.name, paramType),
                });
            }
        }
    }

    private superClassInfo(
        decl: ts.ClassLikeDeclaration,
        visitedClasses: Set<ts.ClassDeclaration> = new Set(),
    ): { signature: ClassSignatureDto; constructorDecl?: ts.ConstructorDeclaration } | undefined {
        const extended = decl.heritageClauses
            ?.find((clause) => clause.token === ts.SyntaxKind.ExtendsKeyword)
            ?.types[0];
        if (extended === undefined) return undefined;

        // Unresolved superclasses keep the syntactic signature below.
        const classDeclaration = this.ctx.converter.symbolOf(extended.expression)
            ?.declarations?.find(ts.isClassDeclaration);

        const signature = classDeclaration !== undefined
            ? this.ctx.converter.classSignatureOf(classDeclaration)
            : {
                name: extended.expression.getText(),
                declaringFile: UNKNOWN_FILE_SIGNATURE,
            };
        const ownConstructor = classDeclaration?.members.find(
            (member): member is ts.ConstructorDeclaration => ts.isConstructorDeclaration(member) && member.body !== undefined,
        ) ?? classDeclaration?.members.find(ts.isConstructorDeclaration);
        // A default derived constructor forwards the arguments accepted by the
        // first explicit constructor, even across other default constructors.
        let constructorDecl = ownConstructor;
        const canFollowBase = constructorDecl === undefined
            && classDeclaration !== undefined
            && !visitedClasses.has(classDeclaration);
        if (canFollowBase && classDeclaration !== undefined) {
            visitedClasses.add(classDeclaration);
            constructorDecl = this.superClassInfo(classDeclaration, visitedClasses)?.constructorDecl;
        }

        return constructorDecl === undefined ? { signature } : { signature, constructorDecl };
    }

    private emitInstInitCall(m: MethodContext, declaringClass: ClassSignatureDto): void {
        m.cfg.emit({
            _: "CallStmt",
            expr: {
                _: "InstanceCallExpr",
                instance: m.getOrCreateLocal("this", m.thisType()),
                method: {
                    declaringClass,
                    name: INSTANCE_INIT_METHOD_NAME,
                    parameters: [],
                    returnType: VOID_TYPE,
                },
                args: [],
            },
        });
    }

    // ------------------------------------------------------------------
    // Initializers
    // ------------------------------------------------------------------

    private buildInstInit(declaringClass: ClassSignatureDto, fields: ts.PropertyDeclaration[]): MethodDto {
        const m = new MethodContext(this.ctx, declaringClass, INSTANCE_INIT_METHOD_NAME);
        m.emitPrologue([]);
        const thisLocal = m.getOrCreateLocal("this", m.thisType());
        const lowerer = new StmtLowerer(m);
        for (const field of fields) {
            m.withOrigin(field, () => {
                const fieldType = this.fieldType(field);
                m.cfg.emit({
                    _: "AssignStmt",
                    left: {
                        _: "InstanceFieldRef",
                        instance: thisLocal,
                        field: { declaringClass, ...this.memberKey(field.name), type: fieldType },
                    },
                    right: lowerer.expr.lowerToImmediate(field.initializer!),
                });
            });
        }
        m.cfg.ret();
        return {
            signature: { declaringClass, name: INSTANCE_INIT_METHOD_NAME, parameters: [], returnType: VOID_TYPE },
            modifiers: 0,
            decorators: [],
            isStrictMode: true,
            body: m.build(),
        };
    }

    private buildStatInit(
        declaringClass: ClassSignatureDto,
        initializers: readonly (ts.PropertyDeclaration | ts.ClassStaticBlockDeclaration)[],
    ): MethodDto {
        const m = new MethodContext(this.ctx, declaringClass, STATIC_INIT_METHOD_NAME, /* isStaticMethod */ true);
        m.emitPrologue([]);
        const lowerer = new StmtLowerer(m);
        for (const initializer of initializers) {
            if (ts.isPropertyDeclaration(initializer)) {
                m.withOrigin(initializer, () => {
                    m.cfg.emit({
                        _: "AssignStmt",
                        left: {
                            _: "StaticFieldRef",
                            field: {
                                declaringClass,
                                ...this.memberKey(initializer.name),
                                type: this.fieldType(initializer),
                            },
                        },
                        right: lowerer.expr.lowerToImmediate(initializer.initializer!),
                    });
                });
            } else {
                lowerer.lowerStatements(initializer.body.statements);
            }
        }
        m.cfg.ret();
        return {
            signature: { declaringClass, name: STATIC_INIT_METHOD_NAME, parameters: [], returnType: VOID_TYPE },
            modifiers: Modifier.STATIC,
            decorators: [],
            isStrictMode: true,
            body: m.build(),
        };
    }

    // ------------------------------------------------------------------
    // Fields / parameters / types
    // ------------------------------------------------------------------

    private buildField(declaringClass: ClassSignatureDto, decl: ts.PropertyDeclaration): FieldDto {
        return {
            signature: {
                declaringClass,
                ...this.memberKey(decl.name),
                type: this.fieldType(decl),
            },
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
            questionToken: decl.questionToken !== undefined,
            exclamationToken: decl.exclamationToken !== undefined,
        };
    }

    private memberKey(name: ts.PropertyName): ClassMemberKey {
        return classMemberKey(name, this.ctx.checker) ?? { name: memberName(name) };
    }

    private buildParameterPropertyField(
        declaringClass: ClassSignatureDto,
        p: ts.ParameterDeclaration,
        index: number,
    ): FieldDto {
        return {
            signature: {
                declaringClass,
                name: ts.isIdentifier(p.name) ? p.name.text : `${PATTERN_PARAMETER_PREFIX}${index}`,
                type: parameterType(this.ctx, p),
            },
            modifiers: modifiersOf(p),
            decorators: [],
            questionToken: p.questionToken !== undefined,
            exclamationToken: false,
        };
    }

    private fieldType(decl: ts.PropertyDeclaration): TypeDto {
        if (decl.type !== undefined) {
            return this.ctx.converter.convertTypeNode(decl.type);
        }
        if (decl.initializer !== undefined) {
            return this.ctx.converter.typeOfNode(decl.name);
        }
        return UNKNOWN_TYPE;
    }

}

// ----------------------------------------------------------------------
// Shared helpers
// ----------------------------------------------------------------------

function isStatic(member: ts.ClassElement): boolean {
    return (ts.getCombinedModifierFlags(member as ts.Declaration) & ts.ModifierFlags.Static) !== 0;
}

function hasParameterPropertyModifier(p: ts.ParameterDeclaration): boolean {
    const flags = ts.getCombinedModifierFlags(p);
    return (
        (flags & (ts.ModifierFlags.Private | ts.ModifierFlags.Protected | ts.ModifierFlags.Public | ts.ModifierFlags.Readonly)) !==
        0
    );
}

function superClassNameOf(decl: ts.ClassLikeDeclaration): string | undefined {
    for (const clause of decl.heritageClauses ?? []) {
        if (clause.token === ts.SyntaxKind.ExtendsKeyword && clause.types.length > 0) {
            return clause.types[0].expression.getText();
        }
    }
    return undefined;
}

function implementedInterfacesOf(decl: ts.ClassLikeDeclaration): string[] {
    for (const clause of decl.heritageClauses ?? []) {
        if (clause.token === ts.SyntaxKind.ImplementsKeyword) {
            return clause.types.map((t) => t.expression.getText());
        }
    }
    return [];
}

function extendedInterfacesOf(decl: ts.InterfaceDeclaration): string[] {
    for (const clause of decl.heritageClauses ?? []) {
        if (clause.token === ts.SyntaxKind.ExtendsKeyword) {
            return clause.types.map((t) => t.expression.getText());
        }
    }
    return [];
}
