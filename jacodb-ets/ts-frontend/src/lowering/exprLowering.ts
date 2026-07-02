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
 * Expression lowering: ts.Expression -> ValueDto, emitting three-address
 * statements into the current basic block as needed.
 *
 * Value discipline (verified against ArkAnalyzer ground truth):
 *  - operands of exprs, call arguments and array indices are IMMEDIATES
 *    (Local | Constant), hoisted into `%N` temps;
 *  - call instances are strictly Locals;
 *  - full exprs appear only as AssignStmt.right / CallStmt.expr;
 *  - `new C(args)` becomes `%t := NewExpr(C); %t := %t.constructor(args)`;
 *  - unresolved identifiers become Locals with UnknownType (e.g. `console`);
 *  - unresolved callees get a method signature with the UNKNOWN class.
 */

import * as ts from "typescript";
import { CONSTRUCTOR_NAME, DEFAULT_ARK_CLASS_NAME } from "../dto/constants";
import { BinaryOp, RelationOp, UnaryOp } from "../dto/ops";
import {
    ClassSignatureDto,
    MethodParameterDto,
    MethodSignatureDto,
    UNKNOWN_CLASS_SIGNATURE,
    UNKNOWN_FILE_SIGNATURE,
} from "../dto/signatures";
import {
    BOOLEAN_TYPE,
    ClassTypeDto,
    NUMBER_TYPE,
    NULL_TYPE,
    STRING_TYPE,
    TypeDto,
    UNDEFINED_TYPE,
    UNKNOWN_TYPE,
} from "../dto/types";
import {
    CallExprDto,
    ConditionExprDto,
    ConstantDto,
    ImmediateDto,
    LValueDto,
    LocalDto,
    ValueDto,
} from "../dto/values";
import { unsupportedValue } from "./diagnostics";
import { MethodContext } from "./methodBuilder";

const RELATION_BY_SYNTAX: Partial<Record<ts.SyntaxKind, RelationOp>> = {
    [ts.SyntaxKind.EqualsEqualsToken]: "==",
    [ts.SyntaxKind.ExclamationEqualsToken]: "!=",
    [ts.SyntaxKind.EqualsEqualsEqualsToken]: "===",
    [ts.SyntaxKind.ExclamationEqualsEqualsToken]: "!==",
    [ts.SyntaxKind.LessThanToken]: "<",
    [ts.SyntaxKind.LessThanEqualsToken]: "<=",
    [ts.SyntaxKind.GreaterThanToken]: ">",
    [ts.SyntaxKind.GreaterThanEqualsToken]: ">=",
    [ts.SyntaxKind.InKeyword]: "in",
};

const BINARY_BY_SYNTAX: Partial<Record<ts.SyntaxKind, BinaryOp>> = {
    [ts.SyntaxKind.PlusToken]: "+",
    [ts.SyntaxKind.MinusToken]: "-",
    [ts.SyntaxKind.AsteriskToken]: "*",
    [ts.SyntaxKind.SlashToken]: "/",
    [ts.SyntaxKind.PercentToken]: "%",
    [ts.SyntaxKind.AsteriskAsteriskToken]: "**",
    [ts.SyntaxKind.LessThanLessThanToken]: "<<",
    [ts.SyntaxKind.GreaterThanGreaterThanToken]: ">>",
    [ts.SyntaxKind.GreaterThanGreaterThanGreaterThanToken]: ">>>",
    [ts.SyntaxKind.AmpersandToken]: "&",
    [ts.SyntaxKind.BarToken]: "|",
    [ts.SyntaxKind.CaretToken]: "^",
    [ts.SyntaxKind.AmpersandAmpersandToken]: "&&",
    [ts.SyntaxKind.BarBarToken]: "||",
    [ts.SyntaxKind.QuestionQuestionToken]: "??",
};

/** Compound assignment operator -> the underlying binary op. */
const COMPOUND_ASSIGN_BY_SYNTAX: Partial<Record<ts.SyntaxKind, BinaryOp>> = {
    [ts.SyntaxKind.PlusEqualsToken]: "+",
    [ts.SyntaxKind.MinusEqualsToken]: "-",
    [ts.SyntaxKind.AsteriskEqualsToken]: "*",
    [ts.SyntaxKind.SlashEqualsToken]: "/",
    [ts.SyntaxKind.PercentEqualsToken]: "%",
    [ts.SyntaxKind.AsteriskAsteriskEqualsToken]: "**",
    [ts.SyntaxKind.LessThanLessThanEqualsToken]: "<<",
    [ts.SyntaxKind.GreaterThanGreaterThanEqualsToken]: ">>",
    [ts.SyntaxKind.GreaterThanGreaterThanGreaterThanEqualsToken]: ">>>",
    [ts.SyntaxKind.AmpersandEqualsToken]: "&",
    [ts.SyntaxKind.BarEqualsToken]: "|",
    [ts.SyntaxKind.CaretEqualsToken]: "^",
    [ts.SyntaxKind.AmpersandAmpersandEqualsToken]: "&&",
    [ts.SyntaxKind.BarBarEqualsToken]: "||",
    [ts.SyntaxKind.QuestionQuestionEqualsToken]: "??",
};

/** Thrown internally for constructs the current milestone cannot lower; callers degrade to Raw*. */
export class LoweringError extends Error {}

export class ExprLowerer {
    constructor(private readonly m: MethodContext) {}

    // ------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------

    /** Lower to any value (full exprs allowed). Use only for AssignStmt.right / CallStmt. */
    lowerExpr(node: ts.Expression): ValueDto {
        try {
            return this.lowerExprImpl(node);
        } catch (e) {
            if (e instanceof LoweringError) {
                this.m.diagnostics.warn(node, `unsupported expression: ${e.message}`);
                return unsupportedValue(node, this.safeTypeOf(node));
            }
            throw e;
        }
    }

    /** Lower to an immediate (Local | Constant), hoisting into a temp if needed. */
    lowerToImmediate(node: ts.Expression): ImmediateDto {
        const value = this.lowerExpr(node);
        if (value._ === "Local" || value._ === "Constant") {
            return value;
        }
        return this.materialize(value, this.safeTypeOf(node));
    }

    /** Lower to a Local (call instances must be Locals). */
    lowerToLocal(node: ts.Expression): LocalDto {
        const value = this.lowerExpr(node);
        if (value._ === "Local") {
            return value;
        }
        return this.materialize(value, this.safeTypeOf(node));
    }

    /** Hoist a value into a fresh temp: `%t := value`. */
    materialize(value: ValueDto, type: TypeDto = UNKNOWN_TYPE): LocalDto {
        const temp = this.m.newTemp(type);
        this.m.cfg.emit({ _: "AssignStmt", left: temp, right: value });
        return temp;
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    private lowerExprImpl(node: ts.Expression): ValueDto {
        if (ts.isNumericLiteral(node)) {
            return constant(node.text, NUMBER_TYPE);
        }
        if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
            return constant(node.text, STRING_TYPE);
        }
        if (node.kind === ts.SyntaxKind.TrueKeyword) {
            return constant("true", BOOLEAN_TYPE);
        }
        if (node.kind === ts.SyntaxKind.FalseKeyword) {
            return constant("false", BOOLEAN_TYPE);
        }
        if (node.kind === ts.SyntaxKind.NullKeyword) {
            return constant("null", NULL_TYPE);
        }
        if (node.kind === ts.SyntaxKind.ThisKeyword) {
            return this.m.getOrCreateLocal("this", this.m.thisType());
        }
        if (ts.isIdentifier(node)) {
            return this.lowerIdentifier(node);
        }
        if (ts.isParenthesizedExpression(node)) {
            return this.lowerExprImpl(node.expression);
        }
        if (ts.isAsExpression(node)) {
            return { _: "CastExpr", arg: this.lowerToImmediate(node.expression), type: this.m.converter.convertTypeNode(node.type) };
        }
        if (ts.isTypeAssertionExpression(node)) {
            return { _: "CastExpr", arg: this.lowerToImmediate(node.expression), type: this.m.converter.convertTypeNode(node.type) };
        }
        if (ts.isNonNullExpression(node) || ts.isSatisfiesExpression(node)) {
            return this.lowerExprImpl(node.expression);
        }
        if (ts.isPropertyAccessExpression(node)) {
            return this.lowerPropertyAccess(node);
        }
        if (ts.isElementAccessExpression(node)) {
            return this.lowerElementAccess(node);
        }
        if (ts.isBinaryExpression(node)) {
            return this.lowerBinary(node);
        }
        if (ts.isPrefixUnaryExpression(node)) {
            return this.lowerPrefixUnary(node);
        }
        if (ts.isPostfixUnaryExpression(node)) {
            return this.lowerPostfixUnary(node);
        }
        if (ts.isCallExpression(node)) {
            return this.lowerCall(node);
        }
        if (ts.isNewExpression(node)) {
            return this.lowerNew(node);
        }
        if (ts.isArrayLiteralExpression(node)) {
            return this.lowerArrayLiteral(node);
        }
        if (ts.isTemplateExpression(node)) {
            return this.lowerTemplate(node);
        }
        if (ts.isTypeOfExpression(node)) {
            return { _: "TypeOfExpr", arg: this.lowerToImmediate(node.expression) };
        }
        if (ts.isAwaitExpression(node)) {
            return { _: "AwaitExpr", arg: this.lowerToImmediate(node.expression) };
        }
        if (ts.isDeleteExpression(node)) {
            return { _: "DeleteExpr", arg: this.lowerExpr(node.expression) };
        }
        if (ts.isVoidExpression(node)) {
            this.lowerDiscarded(node.expression);
            return constant("undefined", UNDEFINED_TYPE);
        }
        throw new LoweringError(ts.SyntaxKind[node.kind]);
    }

    // ------------------------------------------------------------------
    // Leaves
    // ------------------------------------------------------------------

    private lowerIdentifier(node: ts.Identifier): ValueDto {
        if (node.text === "undefined") {
            return constant("undefined", UNDEFINED_TYPE);
        }
        // Every named reference in a method body is a Local; unresolved globals
        // (e.g. `console`) become Locals with UnknownType, same as ArkAnalyzer.
        return this.m.getOrCreateLocal(node.text, this.safeTypeOf(node));
    }

    // ------------------------------------------------------------------
    // Field / array access
    // ------------------------------------------------------------------

    private lowerPropertyAccess(node: ts.PropertyAccessExpression): ValueDto {
        const fieldName = node.name.text;
        const fieldType = this.safeTypeOf(node);

        const staticTarget = this.classLikeSignatureOf(node.expression);
        if (staticTarget !== undefined) {
            return {
                _: "StaticFieldRef",
                field: { declaringClass: staticTarget, name: fieldName, type: fieldType },
            };
        }

        const instance = this.lowerToLocal(node.expression);
        return {
            _: "InstanceFieldRef",
            instance,
            field: {
                declaringClass: this.classSignatureFromType(instance.type),
                name: fieldName,
                type: fieldType,
            },
        };
    }

    private lowerElementAccess(node: ts.ElementAccessExpression): ValueDto {
        return {
            _: "ArrayRef",
            array: this.lowerToImmediate(node.expression),
            index: this.lowerToImmediate(node.argumentExpression),
            type: this.safeTypeOf(node),
        };
    }

    /** Assignment target. */
    lowerLValue(node: ts.Expression): LValueDto {
        if (ts.isParenthesizedExpression(node)) {
            return this.lowerLValue(node.expression);
        }
        if (ts.isIdentifier(node)) {
            return this.m.getOrCreateLocal(node.text, this.safeTypeOf(node));
        }
        if (ts.isPropertyAccessExpression(node)) {
            const ref = this.lowerPropertyAccess(node);
            if (ref._ === "InstanceFieldRef" || ref._ === "StaticFieldRef") {
                return ref;
            }
            throw new LoweringError("property access did not produce a field ref");
        }
        if (ts.isElementAccessExpression(node)) {
            const ref = this.lowerElementAccess(node);
            if (ref._ === "ArrayRef") {
                return ref;
            }
            throw new LoweringError("element access did not produce an array ref");
        }
        throw new LoweringError(`unsupported assignment target: ${ts.SyntaxKind[node.kind]}`);
    }

    // ------------------------------------------------------------------
    // Binary / unary
    // ------------------------------------------------------------------

    private lowerBinary(node: ts.BinaryExpression): ValueDto {
        const opKind = node.operatorToken.kind;

        if (opKind === ts.SyntaxKind.EqualsToken || COMPOUND_ASSIGN_BY_SYNTAX[opKind] !== undefined) {
            return this.lowerAssignment(node);
        }
        if (opKind === ts.SyntaxKind.CommaToken) {
            this.lowerDiscarded(node.left);
            return this.lowerExprImpl(node.right);
        }
        if (opKind === ts.SyntaxKind.InstanceOfKeyword) {
            return {
                _: "InstanceOfExpr",
                arg: this.lowerToImmediate(node.left),
                checkType: this.checkTypeOf(node.right),
            };
        }

        const relationOp = RELATION_BY_SYNTAX[opKind];
        if (relationOp !== undefined) {
            return this.relation(relationOp, this.lowerToImmediate(node.left), this.lowerToImmediate(node.right));
        }

        const binaryOp = BINARY_BY_SYNTAX[opKind];
        if (binaryOp !== undefined) {
            return {
                _: "BinopExpr",
                op: binaryOp,
                left: this.lowerToImmediate(node.left),
                right: this.lowerToImmediate(node.right),
                type: this.safeTypeOf(node),
            };
        }

        throw new LoweringError(`binary operator ${ts.SyntaxKind[opKind]}`);
    }

    relation(op: RelationOp, left: ValueDto, right: ValueDto): ConditionExprDto {
        return { _: "ConditionExpr", op, left, right, type: BOOLEAN_TYPE };
    }

    /** `x = e`, `x += e`, obj.f = e, arr[i] = e; returns the assigned value. */
    lowerAssignment(node: ts.BinaryExpression): ValueDto {
        const opKind = node.operatorToken.kind;
        const target = this.lowerLValue(node.left);

        let rhs: ValueDto;
        const compoundOp = COMPOUND_ASSIGN_BY_SYNTAX[opKind];
        if (compoundOp !== undefined) {
            // load-op-store (note: no short-circuit for &&= / ||= / ??= — approximation)
            const oldValue = target._ === "Local" ? target : this.materialize(target, lvalueType(target));
            rhs = {
                _: "BinopExpr",
                op: compoundOp,
                left: oldValue,
                right: this.lowerToImmediate(node.right),
                type: this.safeTypeOf(node),
            };
        } else {
            rhs = this.lowerExpr(node.right);
        }

        // Only `local := <expr>` may carry a full expr on the right;
        // stores into refs take immediates (ArkAnalyzer discipline).
        if (target._ !== "Local" && rhs._ !== "Local" && rhs._ !== "Constant") {
            rhs = this.materialize(rhs, lvalueType(target));
        }
        this.m.cfg.emit({ _: "AssignStmt", left: target, right: rhs });
        return target._ === "Local" ? target : (rhs as ImmediateDto);
    }

    private lowerPrefixUnary(node: ts.PrefixUnaryExpression): ValueDto {
        switch (node.operator) {
            case ts.SyntaxKind.PlusToken:
                return { _: "UnopExpr", op: "+", arg: this.lowerToImmediate(node.operand) };
            case ts.SyntaxKind.MinusToken: {
                // Constant-fold negative literals: -5 => Constant("-5").
                if (ts.isNumericLiteral(node.operand)) {
                    return constant(`-${node.operand.text}`, NUMBER_TYPE);
                }
                return { _: "UnopExpr", op: "-", arg: this.lowerToImmediate(node.operand) };
            }
            case ts.SyntaxKind.ExclamationToken:
                return { _: "UnopExpr", op: "!", arg: this.lowerToImmediate(node.operand) };
            case ts.SyntaxKind.TildeToken:
                return { _: "UnopExpr", op: "~", arg: this.lowerToImmediate(node.operand) };
            case ts.SyntaxKind.PlusPlusToken:
            case ts.SyntaxKind.MinusMinusToken:
                return this.lowerIncDec(node.operand, node.operator, /* returnOld */ false);
            default:
                throw new LoweringError(`prefix operator ${ts.SyntaxKind[node.operator]}`);
        }
    }

    private lowerPostfixUnary(node: ts.PostfixUnaryExpression): ValueDto {
        return this.lowerIncDec(node.operand, node.operator, /* returnOld */ true);
    }

    private lowerIncDec(
        operand: ts.Expression,
        operator: ts.SyntaxKind.PlusPlusToken | ts.SyntaxKind.MinusMinusToken,
        returnOld: boolean,
    ): ValueDto {
        const op: UnaryOp = operator === ts.SyntaxKind.PlusPlusToken ? "++" : "--";
        const target = this.lowerLValue(operand);
        const oldValue = target._ === "Local" ? target : this.materialize(target, lvalueType(target));
        const saved = returnOld ? this.materialize(oldValue, lvalueType(target)) : undefined;

        const updated: ValueDto = { _: "UnopExpr", op, arg: oldValue };
        if (target._ === "Local") {
            this.m.cfg.emit({ _: "AssignStmt", left: target, right: updated });
        } else {
            const temp = this.materialize(updated, lvalueType(target));
            this.m.cfg.emit({ _: "AssignStmt", left: target, right: temp });
        }
        return saved ?? (target._ === "Local" ? target : oldValue);
    }

    // ------------------------------------------------------------------
    // Calls / new / literals
    // ------------------------------------------------------------------

    lowerCall(node: ts.CallExpression): CallExprDto {
        const args = node.arguments.map((a) =>
            ts.isSpreadElement(a) ? this.spreadFallback(a) : this.lowerToImmediate(a),
        );
        const callee = node.expression;

        if (ts.isPropertyAccessExpression(callee)) {
            const methodName = callee.name.text;
            const staticTarget = this.classLikeSignatureOf(callee.expression);
            if (staticTarget !== undefined) {
                return {
                    _: "StaticCallExpr",
                    method: this.methodSignatureForCall(node, methodName, staticTarget),
                    args,
                };
            }
            const instance = this.lowerToLocal(callee.expression);
            return {
                _: "InstanceCallExpr",
                instance,
                method: this.methodSignatureForCall(node, methodName, this.classSignatureFromType(instance.type)),
                args,
            };
        }

        if (ts.isIdentifier(callee)) {
            const resolved = this.resolveCalleeDeclaration(node);
            if (resolved !== undefined && ts.isFunctionDeclaration(resolved) && isProjectFile(resolved)) {
                // Free function declared in a project file: method of that file's %dflt class.
                const declaringClass: ClassSignatureDto = {
                    name: DEFAULT_ARK_CLASS_NAME,
                    declaringFile: this.m.ctx.fileSignatureFor(resolved.getSourceFile()),
                };
                return { _: "StaticCallExpr", method: this.methodSignatureForCall(node, callee.text, declaringClass), args };
            }
            if (resolved === undefined && !isDeclaredLocalValue(this.m, callee)) {
                // Fully unresolved global callee — static call with the UNKNOWN class.
                return {
                    _: "StaticCallExpr",
                    method: this.methodSignatureForCall(node, callee.text, UNKNOWN_CLASS_SIGNATURE),
                    args,
                };
            }
            // Function value in a variable -> pointer call.
            const localCallee = this.m.getOrCreateLocal(callee.text, this.safeTypeOf(callee));
            return {
                _: "PtrCallExpr",
                ptr: localCallee,
                method: this.methodSignatureForCall(node, callee.text, UNKNOWN_CLASS_SIGNATURE),
                args,
            };
        }

        // Computed callee: evaluate to a local, pointer call.
        const ptr = this.lowerToLocal(callee);
        return {
            _: "PtrCallExpr",
            ptr,
            method: this.methodSignatureForCall(node, "%call", UNKNOWN_CLASS_SIGNATURE),
            args,
        };
    }

    private lowerNew(node: ts.NewExpression): ValueDto {
        const classType = this.newTargetClassType(node.expression);
        const temp = this.m.newTemp(classType);
        this.m.cfg.emit({ _: "AssignStmt", left: temp, right: { _: "NewExpr", classType } });

        const args = (node.arguments ?? []).map((a) =>
            ts.isSpreadElement(a) ? this.spreadFallback(a) : this.lowerToImmediate(a),
        );
        const ctorSig: MethodSignatureDto = {
            declaringClass: classType._ === "ClassType" ? classType.signature : UNKNOWN_CLASS_SIGNATURE,
            name: CONSTRUCTOR_NAME,
            parameters: this.constructorParameters(node),
            returnType: classType,
        };
        this.m.cfg.emit({
            _: "AssignStmt",
            left: temp,
            right: { _: "InstanceCallExpr", instance: temp, method: ctorSig, args },
        });
        return temp;
    }

    private lowerArrayLiteral(node: ts.ArrayLiteralExpression): ValueDto {
        const arrayType = this.safeTypeOf(node);
        const elementType: TypeDto = arrayType._ === "ArrayType" ? arrayType.elementType : UNKNOWN_TYPE;
        const temp = this.m.newTemp(
            arrayType._ === "ArrayType" ? arrayType : { _: "ArrayType", elementType, dimensions: 1 },
        );
        this.m.cfg.emit({
            _: "AssignStmt",
            left: temp,
            right: {
                _: "NewArrayExpr",
                elementType,
                size: constant(String(node.elements.length), NUMBER_TYPE),
            },
        });
        node.elements.forEach((element, index) => {
            const value = ts.isSpreadElement(element) ? this.spreadFallback(element) : this.lowerToImmediate(element);
            this.m.cfg.emit({
                _: "AssignStmt",
                left: {
                    _: "ArrayRef",
                    array: temp,
                    index: constant(String(index), NUMBER_TYPE),
                    type: elementType,
                },
                right: value,
            });
        });
        return temp;
    }

    /** `a${x}b` -> chain of string `+` binops. */
    private lowerTemplate(node: ts.TemplateExpression): ValueDto {
        let acc: ImmediateDto = constant(node.head.text, STRING_TYPE);
        for (const span of node.templateSpans) {
            const exprValue = this.lowerToImmediate(span.expression);
            acc = this.materialize(
                { _: "BinopExpr", op: "+", left: acc, right: exprValue, type: STRING_TYPE },
                STRING_TYPE,
            );
            if (span.literal.text.length > 0) {
                acc = this.materialize(
                    { _: "BinopExpr", op: "+", left: acc, right: constant(span.literal.text, STRING_TYPE), type: STRING_TYPE },
                    STRING_TYPE,
                );
            }
        }
        return acc;
    }

    /** Evaluate an expression for side effects; discard the value. */
    lowerDiscarded(node: ts.Expression): void {
        const value = this.lowerExpr(node);
        if (value._ === "InstanceCallExpr" || value._ === "StaticCallExpr" || value._ === "PtrCallExpr") {
            this.m.cfg.emit({ _: "CallStmt", expr: value });
        } else if (value._ !== "Local" && value._ !== "Constant") {
            this.materialize(value);
        }
    }

    // ------------------------------------------------------------------
    // Resolution helpers
    // ------------------------------------------------------------------

    private safeTypeOf(node: ts.Node): TypeDto {
        return this.m.converter.typeOfNode(node);
    }

    /**
     * If the expression statically refers to a class-like declaration
     * (class / enum — e.g. `Math`, `E` in `E.A`, `Foo` in `Foo.bar()`),
     * return its class signature; project classes get real signatures,
     * ambient ones get the %unk file.
     */
    private classLikeSignatureOf(node: ts.Expression): ClassSignatureDto | undefined {
        if (!ts.isIdentifier(node) && !ts.isPropertyAccessExpression(node)) {
            return undefined;
        }
        try {
            let symbol = this.m.checker.getSymbolAtLocation(ts.isIdentifier(node) ? node : node.name);
            if (symbol === undefined) return undefined;
            if ((symbol.flags & ts.SymbolFlags.Alias) !== 0) {
                symbol = this.m.checker.getAliasedSymbol(symbol);
            }
            const decl = symbol.declarations?.find(
                (d): d is ts.ClassDeclaration | ts.EnumDeclaration =>
                    ts.isClassDeclaration(d) || ts.isEnumDeclaration(d),
            );
            if (decl === undefined) return undefined;
            if (isProjectFile(decl)) {
                return this.m.converter.classSignatureOf(decl);
            }
            const name = decl.name !== undefined && ts.isIdentifier(decl.name) ? decl.name.text : "";
            return { name, declaringFile: UNKNOWN_FILE_SIGNATURE };
        } catch {
            return undefined;
        }
    }

    private classSignatureFromType(type: TypeDto): ClassSignatureDto {
        if (type._ === "ClassType") {
            return type.signature;
        }
        return UNKNOWN_CLASS_SIGNATURE;
    }

    /** ClassType for `new X(...)`; ambient classes get the %unk file (ArkAnalyzer convention). */
    private newTargetClassType(callee: ts.Expression): ClassTypeDto {
        const signature = this.classLikeSignatureOf(callee);
        if (signature !== undefined) {
            return { _: "ClassType", signature };
        }
        const name = ts.isIdentifier(callee)
            ? callee.text
            : ts.isPropertyAccessExpression(callee)
              ? callee.name.text
              : "";
        return { _: "ClassType", signature: { name, declaringFile: UNKNOWN_FILE_SIGNATURE } };
    }

    private resolveCalleeDeclaration(node: ts.CallExpression): ts.Declaration | undefined {
        try {
            const signature = this.m.checker.getResolvedSignature(node);
            return signature?.getDeclaration();
        } catch {
            return undefined;
        }
    }

    /** Method signature for a call site, resolved through the checker when possible. */
    private methodSignatureForCall(
        node: ts.CallExpression,
        name: string,
        declaringClass: ClassSignatureDto,
    ): MethodSignatureDto {
        let parameters: MethodParameterDto[] = [];
        let returnType: TypeDto = UNKNOWN_TYPE;
        try {
            const signature = this.m.checker.getResolvedSignature(node);
            if (signature !== undefined) {
                const decl = signature.getDeclaration();
                if (decl !== undefined && isProjectFile(decl)) {
                    parameters = this.parametersOfDeclaration(decl);
                }
                returnType = this.m.converter.convertType(this.m.checker.getReturnTypeOfSignature(signature));
            }
        } catch {
            // keep unknowns
        }
        return { declaringClass, name, parameters, returnType };
    }

    parametersOfDeclaration(decl: ts.SignatureDeclaration): MethodParameterDto[] {
        return decl.parameters
            .filter((p) => p.name.kind !== ts.SyntaxKind.Identifier || (p.name as ts.Identifier).text !== "this")
            .map((p) => {
                const param: MethodParameterDto = {
                    name: ts.isIdentifier(p.name) ? p.name.text : "%pat",
                    type:
                        p.type !== undefined
                            ? this.m.converter.convertTypeNode(p.type)
                            : this.m.converter.typeOfNode(p),
                };
                if (p.questionToken !== undefined) param.isOptional = true;
                if (p.dotDotDotToken !== undefined) param.isRest = true;
                return param;
            });
    }

    private constructorParameters(node: ts.NewExpression): MethodParameterDto[] {
        try {
            const signature = this.m.checker.getResolvedSignature(node);
            const decl = signature?.getDeclaration();
            if (decl !== undefined && ts.isConstructorDeclaration(decl) && isProjectFile(decl)) {
                return this.parametersOfDeclaration(decl);
            }
        } catch {
            // fall through
        }
        return [];
    }

    private checkTypeOf(node: ts.Expression): TypeDto {
        const signature = this.classLikeSignatureOf(node);
        if (signature !== undefined) {
            return { _: "ClassType", signature };
        }
        if (ts.isIdentifier(node)) {
            return { _: "UnclearReferenceType", name: node.text };
        }
        return UNKNOWN_TYPE;
    }

    private spreadFallback(node: ts.SpreadElement): ValueDto {
        this.m.diagnostics.warn(node, "spread arguments are not supported yet");
        return unsupportedValue(node, this.safeTypeOf(node.expression));
    }
}

// ----------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------

export function constant(value: string, type: TypeDto): ConstantDto {
    return { _: "Constant", value, type };
}

function lvalueType(target: LValueDto): TypeDto {
    switch (target._) {
        case "Local":
        case "ArrayRef":
            return target.type;
        case "InstanceFieldRef":
        case "StaticFieldRef":
            return target.field.type;
    }
}

function isProjectFile(decl: ts.Node): boolean {
    return !decl.getSourceFile().isDeclarationFile;
}

/** Whether the identifier refers to a value declared somewhere in project code. */
function isDeclaredLocalValue(m: MethodContext, node: ts.Identifier): boolean {
    try {
        const symbol = m.checker.getSymbolAtLocation(node);
        const decl = symbol?.declarations?.[0];
        return decl !== undefined && !decl.getSourceFile().isDeclarationFile;
    } catch {
        return false;
    }
}
