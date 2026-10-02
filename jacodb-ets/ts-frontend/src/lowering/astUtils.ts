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

/** Shared AST helpers used across the lowering modules. */

import * as ts from "typescript";
import { COMPUTED_MEMBER_NAME, Modifier, PATTERN_PARAMETER_PREFIX } from "../dto/constants";
import { DecoratorDto } from "../dto/model";
import { MethodParameterDto } from "../dto/signatures";
import { TypeDto, UNKNOWN_TYPE } from "../dto/types";
import type { LoweringContext } from "./methodBuilder";

export function memberName(name: ts.PropertyName | ts.BindingName | ts.EntityName): string {
    if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNumericLiteral(name) || ts.isPrivateIdentifier(name)) {
        return name.text;
    }
    return COMPUTED_MEMBER_NAME;
}

export function modifiersOf(node: ts.Node): number {
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

/** The simple binding whose current value an expression denotes, if any. */
export function bindingIdentifier(node: ts.Expression): ts.Identifier | undefined {
    while (
        ts.isParenthesizedExpression(node)
        || ts.isAsExpression(node)
        || ts.isTypeAssertionExpression(node)
        || ts.isNonNullExpression(node)
        || ts.isSatisfiesExpression(node)
    ) {
        node = node.expression;
    }
    return ts.isIdentifier(node) ? node : undefined;
}

function declarationSymbol(identifier: ts.Identifier, checker: ts.TypeChecker): ts.Symbol | undefined {
    let symbol = ts.isShorthandPropertyAssignment(identifier.parent)
        ? checker.getShorthandAssignmentValueSymbol(identifier.parent)
        : checker.getSymbolAtLocation(identifier);
    if (symbol === undefined) return undefined;
    if ((symbol.flags & ts.SymbolFlags.Alias) !== 0) symbol = checker.getAliasedSymbol(symbol);

    return symbol;
}

/** The exact class/enum receiver syntax recognized by ExprLowerer for static access. */
export function classLikeDeclarationOf(
    node: ts.Expression,
    checker: ts.TypeChecker,
): ts.ClassDeclaration | ts.EnumDeclaration | undefined {
    if (!ts.isIdentifier(node) && !ts.isPropertyAccessExpression(node)) return undefined;

    const identifier = ts.isIdentifier(node) ? node : node.name;
    if (!ts.isIdentifier(identifier)) return undefined;

    return declarationSymbol(identifier, checker)?.declarations?.find(
        (declaration): declaration is ts.ClassDeclaration | ts.EnumDeclaration =>
            ts.isClassDeclaration(declaration) || ts.isEnumDeclaration(declaration),
    );
}

/** Storage that ClassBuilder actually emits for a direct static class/enum member. */
function staticMemberStorage(
    node: ts.PropertyAccessExpression,
    checker: ts.TypeChecker,
): "field" | "method" | "unsupported" | undefined {
    const classDecl = classLikeDeclarationOf(node.expression, checker);
    if (classDecl === undefined) return undefined;
    if (!ts.isIdentifier(node.name)) return "unsupported";

    const declarations = declarationSymbol(node.name, checker)?.declarations ?? [];
    const member = declarations.find((declaration) =>
        declaration.parent === classDecl
        && (ts.isPropertyDeclaration(declaration)
            || ts.isMethodDeclaration(declaration)
            || ts.isEnumMember(declaration))
        && memberName(declaration.name) === node.name.text,
    );
    if (member === undefined) return "unsupported";

    if (ts.isEnumMember(member) && ts.isEnumDeclaration(classDecl)) return "field";
    if (ts.isPropertyDeclaration(member) && (modifiersOf(member) & Modifier.STATIC) !== 0) return "field";
    if (ts.isMethodDeclaration(member) && (modifiersOf(member) & Modifier.STATIC) !== 0) return "method";

    return "unsupported";
}

/** Scope functions, classes, enums, and namespaces have no standalone value reference in EtsIR yet. */
function isUnmaterializedValue(identifier: ts.Identifier, checker: ts.TypeChecker): boolean {
    const symbol = declarationSymbol(identifier, checker);

    return symbol?.declarations?.some((declaration) =>
        ts.isClassDeclaration(declaration)
        || (ts.isFunctionDeclaration(declaration)
            && (ts.isSourceFile(declaration.parent) || ts.isModuleBlock(declaration.parent)))
        || ts.isEnumDeclaration(declaration)
        || ts.isModuleDeclaration(declaration),
    ) ?? false;
}

/** Direct calls and constructors use method/class signatures; other reads need a materialized value. */
export function usesUnmaterializedDeclarationValue(expression: ts.Expression, checker: ts.TypeChecker): boolean {
    const visit = (node: ts.Node): boolean => {
        if (ts.isTypeNode(node)) return false;
        if (ts.isIdentifier(node)) return isUnmaterializedValue(node, checker);

        if (ts.isNewExpression(node) && classLikeDeclarationOf(node.expression, checker) !== undefined) {
            return node.arguments?.some(visit) ?? false;
        }
        if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression)) {
            const storage = staticMemberStorage(node.expression, checker);
            if (storage !== undefined) {
                return ts.isOptionalChain(node) || storage !== "method" || (node.arguments?.some(visit) ?? false);
            }
        }
        if (ts.isCallExpression(node) || ts.isNewExpression(node)) {
            const directCall = ts.isCallExpression(node) && node.questionDotToken === undefined;
            const calleeNeedsValue = !(ts.isIdentifier(node.expression) && (directCall || ts.isNewExpression(node)))
                && visit(node.expression);
            return calleeNeedsValue || (node.arguments?.some(visit) ?? false);
        }
        if (ts.isPropertyAccessExpression(node)) {
            const storage = staticMemberStorage(node, checker);
            if (storage !== undefined) return ts.isOptionalChain(node) || storage !== "field";

            return visit(node.expression);
        }
        if (ts.isPropertyAssignment(node)) {
            return (ts.isComputedPropertyName(node.name) && visit(node.name.expression))
                || visit(node.initializer);
        }
        if (ts.isShorthandPropertyAssignment(node)) return visit(node.name);

        let found = false;
        ts.forEachChild(node, (child) => { found = visit(child) || found; });
        return found;
    };

    return visit(expression);
}

/** `export default` is a module statement; its lexical `this` is not `%dflt`'s synthetic receiver. */
export function usesModuleLexicalThis(expression: ts.Expression): boolean {
    const visit = (node: ts.Node): boolean => {
        if (node.kind === ts.SyntaxKind.ThisKeyword) return true;
        if (ts.isFunctionExpression(node)
            || ts.isMethodDeclaration(node)
            || ts.isConstructorDeclaration(node)
            || ts.isGetAccessorDeclaration(node)
            || ts.isSetAccessorDeclaration(node)
            || ts.isClassExpression(node)) return false;

        let found = false;
        ts.forEachChild(node, (child) => { found = visit(child) || found; });
        return found;
    };

    return visit(expression);
}

function decoratorName(expr: ts.Expression): string {
    if (ts.isCallExpression(expr)) return decoratorName(expr.expression);
    if (ts.isIdentifier(expr)) return expr.text;
    if (ts.isPropertyAccessExpression(expr)) {
        return `${decoratorName(expr.expression)}.${expr.name.text}`;
    }
    return expr.getText();
}

export function decoratorsOf(node: ts.Node): DecoratorDto[] {
    const decorators = ts.canHaveDecorators(node) ? ts.getDecorators(node) : undefined;
    if (decorators === undefined) {
        return [];
    }
    return decorators.map((d) => {
        return { kind: decoratorName(d.expression) };
    });
}

export function parameterType(ctx: LoweringContext, p: ts.ParameterDeclaration): TypeDto {
    return p.type !== undefined ? ctx.converter.convertTypeNode(p.type) : ctx.converter.typeOfNode(p.name);
}

export interface BuiltParameters {
    parameters: MethodParameterDto[];
    prologueParams: { name: string; type: TypeDto; identifier?: ts.Identifier; pattern?: ts.BindingPattern }[];
}

export function buildParameters(ctx: LoweringContext, decl: ts.SignatureDeclarationBase): BuiltParameters {
    const parameters: MethodParameterDto[] = [];
    const prologueParams: BuiltParameters["prologueParams"] = [];
    for (const p of decl.parameters) {
        // A TS fake `this` parameter is a type annotation, not a real parameter:
        // it must not shift ParameterRef indices or shadow the `this` local.
        if (ts.isIdentifier(p.name) && p.name.text === "this") {
            continue;
        }
        // Pattern parameters need distinct names: a shared `%pat` would make the second
        // ParameterRef overwrite the first one and duplicate the name in the signature.
        const name = ts.isIdentifier(p.name)
            ? p.name.text
            : `${PATTERN_PARAMETER_PREFIX}${parameters.length}`;
        const type = parameterType(ctx, p);
        const param: MethodParameterDto = { name, type };
        if (p.questionToken !== undefined) param.isOptional = true;
        if (p.dotDotDotToken !== undefined) param.isRest = true;
        parameters.push(param);
        prologueParams.push({
            name,
            type,
            identifier: ts.isIdentifier(p.name) ? p.name : undefined,
            pattern: ts.isObjectBindingPattern(p.name) || ts.isArrayBindingPattern(p.name) ? p.name : undefined,
        });
    }
    return { parameters, prologueParams };
}

export function returnTypeOf(ctx: LoweringContext, decl: ts.SignatureDeclarationBase): TypeDto {
    if (decl.type !== undefined) {
        return ctx.converter.convertTypeNode(decl.type);
    }
    try {
        const signature = ctx.checker.getSignatureFromDeclaration(decl as ts.SignatureDeclaration);
        if (signature !== undefined) {
            return ctx.converter.convertType(ctx.checker.getReturnTypeOfSignature(signature));
        }
    } catch {
        // fall through
    }
    return UNKNOWN_TYPE;
}
