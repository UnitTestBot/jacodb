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
import { CONSTRUCTOR_NAME, INSTANCE_INIT_METHOD_NAME, Modifier, STATIC_INIT_METHOD_NAME } from "../dto/constants";
import { ClassDto, DecoratorDto, FieldDto, MethodDto } from "../dto/model";
import { ClassSignatureDto, MethodParameterDto } from "../dto/signatures";
import { ClassTypeDto, TypeDto, BOOLEAN_TYPE, NUMBER_TYPE, STRING_TYPE, UNKNOWN_TYPE, VOID_TYPE } from "../dto/types";
import { buildParameters, decoratorsOf, memberName, modifiersOf, parameterType, returnTypeOf } from "./astUtils";
import { constant } from "./exprLowering";
import { LoweringContext, MethodContext } from "./methodBuilder";
import { StmtLowerer } from "./stmtLowering";

type ClassMemberDecl = ts.MethodDeclaration | ts.GetAccessorDeclaration | ts.SetAccessorDeclaration;

export class ClassBuilder {
    constructor(private readonly ctx: LoweringContext) {}

    // ------------------------------------------------------------------
    // Classes
    // ------------------------------------------------------------------

    buildClass(decl: ts.ClassDeclaration): ClassDto {
        const signature = this.ctx.converter.classSignatureOf(decl);

        const instanceFields: ts.PropertyDeclaration[] = [];
        const staticFields: ts.PropertyDeclaration[] = [];
        const fields: FieldDto[] = [];
        const methods: MethodDto[] = [];
        let ctorDecl: ts.ConstructorDeclaration | undefined;

        for (const member of decl.members) {
            if (ts.isPropertyDeclaration(member)) {
                fields.push(this.buildField(signature, member));
                if (member.initializer !== undefined) {
                    if (isStatic(member)) {
                        staticFields.push(member);
                    } else {
                        instanceFields.push(member);
                    }
                }
            } else if (ts.isConstructorDeclaration(member)) {
                if (member.body !== undefined) {
                    ctorDecl = member;
                }
                // Parameter properties (constructor(private x: number)) become fields.
                for (const p of member.parameters) {
                    if (hasParameterPropertyModifier(p) && ts.isIdentifier(p.name)) {
                        fields.push(this.buildParameterPropertyField(signature, p));
                    }
                }
            } else if (
                ts.isMethodDeclaration(member) ||
                ts.isGetAccessorDeclaration(member) ||
                ts.isSetAccessorDeclaration(member)
            ) {
                methods.push(this.buildMethodFromDecl(signature, member));
            } else if (ts.isSemicolonClassElement(member) || ts.isIndexSignatureDeclaration(member)) {
                // ignore
            } else if (ts.isClassStaticBlockDeclaration(member)) {
                this.ctx.diagnostics.warn(member, "static blocks are folded into %statInit");
                // handled below via buildStatInit extension: keep simple — lowered into %statInit
            } else {
                this.ctx.diagnostics.warn(member, `unsupported class member: ${ts.SyntaxKind[member.kind]}`);
            }
        }

        const staticBlocks = decl.members.filter(ts.isClassStaticBlockDeclaration);

        methods.push(this.buildInstInit(signature, instanceFields));
        methods.push(this.buildStatInit(signature, staticFields, staticBlocks));
        methods.push(
            ctorDecl !== undefined
                ? this.buildConstructor(signature, ctorDecl)
                : this.synthesizeDefaultConstructor(signature),
        );

        const result: ClassDto = {
            signature,
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
            category: 0,
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
            category: 2,
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
        const m = new MethodContext(this.ctx, signature, STATIC_INIT_METHOD_NAME);
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
            category: 3,
            superClassName: "",
            implementedInterfaceNames: [],
            fields,
            methods: [
                {
                    signature: { declaringClass: signature, name: STATIC_INIT_METHOD_NAME, parameters: [], returnType: VOID_TYPE },
                    modifiers: Modifier.STATIC,
                    decorators: [],
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

    buildMethodFromDecl(declaringClass: ClassSignatureDto, decl: ClassMemberDecl | ts.FunctionDeclaration): MethodDto {
        const name = decl.name !== undefined ? memberName(decl.name) : "";
        const { parameters, prologueParams } = buildParameters(this.ctx, decl);
        const returnType = returnTypeOf(this.ctx, decl);

        const method: MethodDto = {
            signature: { declaringClass, name, parameters, returnType },
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
        };
        const typeParameters = this.ctx.converter.convertTypeParameters(decl.typeParameters);
        if (typeParameters !== undefined) {
            method.typeParameters = typeParameters;
        }

        if (decl.body !== undefined) {
            const isStaticMethod = (modifiersOf(decl) & Modifier.STATIC) !== 0;
            const m = new MethodContext(this.ctx, declaringClass, name, isStaticMethod);
            m.emitPrologue(prologueParams);
            new StmtLowerer(m).lowerStatements(decl.body.statements);
            method.body = m.build();
        }
        return method;
    }

    buildBodylessMethod(declaringClass: ClassSignatureDto, decl: ts.MethodSignature): MethodDto {
        const { parameters } = buildParameters(this.ctx, decl);
        return {
            signature: {
                declaringClass,
                name: memberName(decl.name),
                parameters,
                returnType: returnTypeOf(this.ctx, decl),
            },
            modifiers: modifiersOf(decl),
            decorators: [],
        };
    }

    private buildConstructor(declaringClass: ClassSignatureDto, decl: ts.ConstructorDeclaration): MethodDto {
        const { parameters, prologueParams } = buildParameters(this.ctx, decl);
        const classType: ClassTypeDto = { _: "ClassType", signature: declaringClass };

        const m = new MethodContext(this.ctx, declaringClass, CONSTRUCTOR_NAME);
        m.emitPrologue(prologueParams);
        const thisLocal = m.getOrCreateLocal("this", classType);
        this.emitInstInitCall(m, declaringClass);
        // Parameter properties: this.x := x
        for (const p of decl.parameters) {
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
        if (decl.body !== undefined) {
            new StmtLowerer(m).lowerStatements(decl.body.statements);
        }
        if (m.cfg.isOpen()) {
            m.cfg.ret(thisLocal);
        }
        return {
            signature: { declaringClass, name: CONSTRUCTOR_NAME, parameters, returnType: classType },
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
            body: m.build(),
        };
    }

    private synthesizeDefaultConstructor(declaringClass: ClassSignatureDto): MethodDto {
        const classType: ClassTypeDto = { _: "ClassType", signature: declaringClass };
        const m = new MethodContext(this.ctx, declaringClass, CONSTRUCTOR_NAME);
        m.emitPrologue([]);
        this.emitInstInitCall(m, declaringClass);
        m.cfg.ret(m.getOrCreateLocal("this", classType));
        return {
            signature: { declaringClass, name: CONSTRUCTOR_NAME, parameters: [], returnType: classType },
            modifiers: 0,
            decorators: [],
            body: m.build(),
        };
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
            const fieldType = this.fieldType(field);
            m.cfg.emit({
                _: "AssignStmt",
                left: {
                    _: "InstanceFieldRef",
                    instance: thisLocal,
                    field: { declaringClass, name: memberName(field.name), type: fieldType },
                },
                right: lowerer.expr.lowerToImmediate(field.initializer!),
            });
        }
        m.cfg.ret();
        return {
            signature: { declaringClass, name: INSTANCE_INIT_METHOD_NAME, parameters: [], returnType: VOID_TYPE },
            modifiers: 0,
            decorators: [],
            body: m.build(),
        };
    }

    private buildStatInit(
        declaringClass: ClassSignatureDto,
        fields: ts.PropertyDeclaration[],
        staticBlocks: readonly ts.ClassStaticBlockDeclaration[] = [],
    ): MethodDto {
        const m = new MethodContext(this.ctx, declaringClass, STATIC_INIT_METHOD_NAME);
        m.emitPrologue([]);
        const lowerer = new StmtLowerer(m);
        for (const field of fields) {
            m.cfg.emit({
                _: "AssignStmt",
                left: {
                    _: "StaticFieldRef",
                    field: { declaringClass, name: memberName(field.name), type: this.fieldType(field) },
                },
                right: lowerer.expr.lowerToImmediate(field.initializer!),
            });
        }
        for (const block of staticBlocks) {
            lowerer.lowerStatements(block.body.statements);
        }
        m.cfg.ret();
        return {
            signature: { declaringClass, name: STATIC_INIT_METHOD_NAME, parameters: [], returnType: VOID_TYPE },
            modifiers: Modifier.STATIC,
            decorators: [],
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
                name: memberName(decl.name),
                type: this.fieldType(decl),
            },
            modifiers: modifiersOf(decl),
            decorators: decoratorsOf(decl),
            questionToken: decl.questionToken !== undefined,
            exclamationToken: decl.exclamationToken !== undefined,
        };
    }

    private buildParameterPropertyField(declaringClass: ClassSignatureDto, p: ts.ParameterDeclaration): FieldDto {
        return {
            signature: {
                declaringClass,
                name: ts.isIdentifier(p.name) ? p.name.text : "%pat",
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
            const inferred = this.ctx.converter.typeOfNode(decl.name);
            return widenLiteral(inferred);
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

function superClassNameOf(decl: ts.ClassDeclaration): string | undefined {
    for (const clause of decl.heritageClauses ?? []) {
        if (clause.token === ts.SyntaxKind.ExtendsKeyword && clause.types.length > 0) {
            return clause.types[0].expression.getText();
        }
    }
    return undefined;
}

function implementedInterfacesOf(decl: ts.ClassDeclaration): string[] {
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

/** Widen literal types inferred from initializers (fields are mutable). */
function widenLiteral(type: TypeDto): TypeDto {
    if (type._ === "LiteralType") {
        switch (typeof type.literal) {
            case "number":
                return NUMBER_TYPE;
            case "string":
                return STRING_TYPE;
            case "boolean":
                return BOOLEAN_TYPE;
        }
    }
    return type;
}
