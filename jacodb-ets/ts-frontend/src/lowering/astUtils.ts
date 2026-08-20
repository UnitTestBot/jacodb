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
