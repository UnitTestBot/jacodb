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
import {
    ANONYMOUS_CLASS_PREFIX,
    ANONYMOUS_METHOD_PREFIX,
    CONSTRUCTOR_NAME,
    DEFAULT_ARK_CLASS_NAME,
} from "../dto/constants";
import { FieldDto, MethodDto } from "../dto/model";
import { buildParameters, memberName, modifiersOf, returnTypeOf } from "./astUtils";
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

/** Lowers the body of a nested function (closure / object-literal method) into a fresh MethodContext. */
export type FunctionBodyLowerer = (m: MethodContext, body: ts.ConciseBody) => void;

export class ExprLowerer {
    constructor(
        private readonly m: MethodContext,
        private readonly lowerFunctionBody?: FunctionBodyLowerer,
    ) {}

    // ------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------

    /** Lower to any value (full exprs allowed). Use only for AssignStmt.right / CallStmt. */
    lowerExpr(node: ts.Expression): ValueDto {
        return this.m.withOrigin(node, () => {
            try {
                return this.lowerExprImpl(node);
            } catch (e) {
                if (e instanceof LoweringError) {
                    this.m.diagnostics.warn(node, `unsupported expression: ${e.message}`);
                    // Raw fallback values are only legal as the RHS of a Local
                    // assignment (Kotlin's ensureOneAddress rejects EtsRawEntity in
                    // every other position), so hoist into a temp right away.
                    const type = this.safeTypeOf(node);
                    return this.materialize(unsupportedValue(node, type), type);
                }
                throw e;
            }
        });
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
        if (node.kind === ts.SyntaxKind.SuperKeyword) {
            // `super` is the same object as `this`.
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
        if (ts.isConditionalExpression(node)) {
            return this.lowerTernary(node);
        }
        if (ts.isArrowFunction(node) || ts.isFunctionExpression(node)) {
            return this.lowerClosure(node);
        }
        if (ts.isObjectLiteralExpression(node)) {
            return this.lowerObjectLiteral(node);
        }
        if (ts.isYieldExpression(node)) {
            return {
                _: "YieldExpr",
                arg: node.expression !== undefined
                    ? this.lowerToImmediate(node.expression)
                    : constant("undefined", UNDEFINED_TYPE),
            };
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
        return this.m.localForIdentifier(node, this.safeTypeOf(node));
    }

    // ------------------------------------------------------------------
    // Field / array access
    // ------------------------------------------------------------------

    private lowerPropertyAccess(node: ts.PropertyAccessExpression): ValueDto {
        const fieldName = node.name.text;
        const fieldType = this.safeTypeOf(node);

        if (node.questionDotToken !== undefined) {
            // a?.b
            return this.optionalDiamond(node.expression, fieldType, (obj) => ({
                _: "InstanceFieldRef",
                instance: obj,
                field: {
                    declaringClass: this.classSignatureFromType(obj.type),
                    name: fieldName,
                    type: fieldType,
                },
            }));
        }

        // `this.f` inside a STATIC method addresses a static field of the class.
        if (node.expression.kind === ts.SyntaxKind.ThisKeyword && this.m.isStaticMethod) {
            return {
                _: "StaticFieldRef",
                field: { declaringClass: this.m.declaringClass, name: fieldName, type: fieldType },
            };
        }

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
        if (node.questionDotToken !== undefined) {
            // a?.[i]
            return this.optionalDiamond(node.expression, this.safeTypeOf(node), (obj) => ({
                _: "ArrayRef",
                array: obj,
                index: this.lowerToImmediate(node.argumentExpression),
                type: this.safeTypeOf(node),
            }));
        }
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
            return this.m.localForIdentifier(node, this.safeTypeOf(node));
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

        if (
            opKind === ts.SyntaxKind.AmpersandAmpersandToken ||
            opKind === ts.SyntaxKind.BarBarToken ||
            opKind === ts.SyntaxKind.QuestionQuestionToken
        ) {
            return this.lowerLogicalBinary(node);
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

    // ------------------------------------------------------------------
    // Conditions / branching
    // ------------------------------------------------------------------

    /**
     * Lower a boolean context expression into a conditional branch.
     * Direct comparisons branch on the ConditionExpr itself; anything else is
     * normalized the ArkAnalyzer way: booleans as `v != false`, others as `v != 0`.
     * `!x` swaps the branch targets.
     */
    lowerCondition(node: ts.Expression, trueTarget: number, falseTarget: number): void {
        this.m.withOrigin(node, () => this.lowerConditionImpl(node, trueTarget, falseTarget));
    }

    private lowerConditionImpl(node: ts.Expression, trueTarget: number, falseTarget: number): void {
        if (ts.isParenthesizedExpression(node)) {
            this.lowerCondition(node.expression, trueTarget, falseTarget);
            return;
        }
        if (ts.isPrefixUnaryExpression(node) && node.operator === ts.SyntaxKind.ExclamationToken) {
            this.lowerCondition(node.operand, falseTarget, trueTarget);
            return;
        }
        if (ts.isBinaryExpression(node)) {
            const relationOp = RELATION_BY_SYNTAX[node.operatorToken.kind];
            if (relationOp !== undefined) {
                const condition = this.relation(
                    relationOp,
                    this.lowerToImmediate(node.left),
                    this.lowerToImmediate(node.right),
                );
                this.m.cfg.branch(condition, trueTarget, falseTarget);
                return;
            }
        }
        const value = this.lowerToImmediate(node);
        this.branchOnTruthiness(value, trueTarget, falseTarget);
    }

    /** A single-condition truthiness check for values whose type has a direct representation. */
    truthyCondition(value: ValueDto): ConditionExprDto {
        const valueType = value._ === "Local" || value._ === "Constant" ? value.type : UNKNOWN_TYPE;
        if (valueType._ === "BooleanType") {
            return this.relation("!=", value, constant("false", BOOLEAN_TYPE));
        }
        if (valueType._ === "StringType") {
            return this.relation("!==", value, constant("", STRING_TYPE));
        }
        return this.relation("!=", value, constant("0", NUMBER_TYPE));
    }

    /** Branch with JavaScript ToBoolean semantics for the representable type cases. */
    private branchOnTruthiness(value: ImmediateDto, trueTarget: number, falseTarget: number): void {
        const type = value.type;
        switch (type._) {
            case "BooleanType":
                this.m.cfg.branch(this.relation("!==", value, constant("false", BOOLEAN_TYPE)), trueTarget, falseTarget);
                return;
            case "NumberType":
            case "EnumValueType": {
                const nonZero = this.m.cfg.newLabel();
                this.m.cfg.branch(this.relation("!==", value, constant("0", NUMBER_TYPE)), nonZero, falseTarget);
                this.m.cfg.placeLabel(nonZero);
                // NaN is the only non-zero falsy number. It is also the only
                // JavaScript value that is not strictly equal to itself.
                this.m.cfg.branch(this.relation("===", value, value), trueTarget, falseTarget);
                return;
            }
            case "StringType":
                this.m.cfg.branch(this.relation("!==", value, constant("", STRING_TYPE)), trueTarget, falseTarget);
                return;
            case "NullType":
            case "UndefinedType":
            case "VoidType":
            case "NeverType":
                this.m.cfg.goto(falseTarget);
                return;
            case "ClassType":
            case "ArrayType":
            case "TupleType":
            case "FunctionType":
                this.m.cfg.goto(trueTarget);
                return;
            case "LiteralType": {
                const literal = type.literal;
                this.m.cfg.goto(literal === false || literal === 0 || literal === "" ? falseTarget : trueTarget);
                return;
            }
            default:
                this.branchOnUnknownTruthiness(value, trueTarget, falseTarget);
        }
    }

    /**
     * Unknown/union values need the full falsy set rather than the old `v != 0`
     * approximation. The final self-equality check rejects NaN.
     */
    private branchOnUnknownTruthiness(value: ImmediateDto, trueTarget: number, falseTarget: number): void {
        const notFalse = this.m.cfg.newLabel();
        const notZero = this.m.cfg.newLabel();
        const notEmpty = this.m.cfg.newLabel();
        this.m.cfg.branch(this.relation("===", value, constant("false", BOOLEAN_TYPE)), falseTarget, notFalse);
        this.m.cfg.placeLabel(notFalse);
        this.m.cfg.branch(this.relation("===", value, constant("0", NUMBER_TYPE)), falseTarget, notZero);
        this.m.cfg.placeLabel(notZero);
        this.m.cfg.branch(this.relation("===", value, constant("", STRING_TYPE)), falseTarget, notEmpty);
        this.m.cfg.placeLabel(notEmpty);
        const notNullish = this.m.cfg.newLabel();
        // Loose equality intentionally covers both null and undefined.
        this.m.cfg.branch(this.relation("==", value, constant("null", NULL_TYPE)), falseTarget, notNullish);
        this.m.cfg.placeLabel(notNullish);
        this.m.cfg.branch(this.relation("!==", value, value), falseTarget, trueTarget);
    }

    /** Value-preserving, side-effect-safe lowering for `&&`, `||`, and `??`. */
    private lowerLogicalBinary(node: ts.BinaryExpression): LocalDto {
        const cfg = this.m.cfg;
        const left = this.lowerToImmediate(node.left);
        const result = this.m.newTemp(this.safeTypeOf(node));
        const rightLabel = cfg.newLabel();
        const leftLabel = cfg.newLabel();
        const joinLabel = cfg.newLabel();

        switch (node.operatorToken.kind) {
            case ts.SyntaxKind.AmpersandAmpersandToken:
                this.branchOnTruthiness(left, rightLabel, leftLabel);
                break;
            case ts.SyntaxKind.BarBarToken:
                this.branchOnTruthiness(left, leftLabel, rightLabel);
                break;
            case ts.SyntaxKind.QuestionQuestionToken:
                cfg.branch(this.relation("!=", left, constant("null", NULL_TYPE)), leftLabel, rightLabel);
                break;
        }

        cfg.placeLabel(leftLabel);
        cfg.emit({ _: "AssignStmt", left: result, right: left });
        cfg.goto(joinLabel);
        cfg.placeLabel(rightLabel);
        cfg.emit({ _: "AssignStmt", left: result, right: this.lowerExpr(node.right) });
        cfg.goto(joinLabel);
        cfg.placeLabel(joinLabel);
        return result;
    }

    /** `c ? a : b` -> branch diamond writing a shared temp. */
    private lowerTernary(node: ts.ConditionalExpression): ValueDto {
        const cfg = this.m.cfg;
        const result = this.m.newTemp(this.safeTypeOf(node));
        const trueLabel = cfg.newLabel();
        const falseLabel = cfg.newLabel();
        const joinLabel = cfg.newLabel();

        this.lowerCondition(node.condition, trueLabel, falseLabel);
        cfg.placeLabel(trueLabel);
        cfg.emit({ _: "AssignStmt", left: result, right: this.lowerExpr(node.whenTrue) });
        cfg.goto(joinLabel);
        cfg.placeLabel(falseLabel);
        cfg.emit({ _: "AssignStmt", left: result, right: this.lowerExpr(node.whenFalse) });
        cfg.goto(joinLabel);
        cfg.placeLabel(joinLabel);
        return result;
    }

    /** `x = e`, `x += e`, obj.f = e, arr[i] = e; returns the assigned value. */
    lowerAssignment(node: ts.BinaryExpression): ValueDto {
        const opKind = node.operatorToken.kind;
        const target = this.lowerLValue(node.left);

        if (
            opKind === ts.SyntaxKind.AmpersandAmpersandEqualsToken ||
            opKind === ts.SyntaxKind.BarBarEqualsToken ||
            opKind === ts.SyntaxKind.QuestionQuestionEqualsToken
        ) {
            return this.lowerLogicalAssignment(node, target);
        }

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

    private lowerLogicalAssignment(node: ts.BinaryExpression, target: LValueDto): LocalDto {
        const cfg = this.m.cfg;
        const oldValue = target._ === "Local" ? target : this.materialize(target, lvalueType(target));
        const result = this.m.newTemp(this.safeTypeOf(node));
        const assignLabel = cfg.newLabel();
        const keepLabel = cfg.newLabel();
        const joinLabel = cfg.newLabel();

        switch (node.operatorToken.kind) {
            case ts.SyntaxKind.AmpersandAmpersandEqualsToken:
                this.branchOnTruthiness(oldValue, assignLabel, keepLabel);
                break;
            case ts.SyntaxKind.BarBarEqualsToken:
                this.branchOnTruthiness(oldValue, keepLabel, assignLabel);
                break;
            case ts.SyntaxKind.QuestionQuestionEqualsToken:
                cfg.branch(this.relation("!=", oldValue, constant("null", NULL_TYPE)), keepLabel, assignLabel);
                break;
        }

        cfg.placeLabel(keepLabel);
        cfg.emit({ _: "AssignStmt", left: result, right: oldValue });
        cfg.goto(joinLabel);
        cfg.placeLabel(assignLabel);
        let rhs = this.lowerExpr(node.right);
        if (target._ !== "Local" && rhs._ !== "Local" && rhs._ !== "Constant") {
            rhs = this.materialize(rhs, lvalueType(target));
        }
        cfg.emit({ _: "AssignStmt", left: target, right: rhs });
        const assigned = rhs._ === "Local" || rhs._ === "Constant" ? rhs : target;
        cfg.emit({ _: "AssignStmt", left: result, right: assigned });
        cfg.goto(joinLabel);
        cfg.placeLabel(joinLabel);
        return result;
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

        if (target._ === "Local") {
            // Postfix needs a copy of the old value BEFORE the update.
            const saved = returnOld ? this.materialize(target, target.type) : undefined;
            this.m.cfg.emit({ _: "AssignStmt", left: target, right: { _: "UnopExpr", op, arg: target } });
            return saved ?? target;
        }

        // Field/array target: load old, compute updated, store back.
        //   %old := ref; %new := %old ++; ref := %new
        const oldValue = this.materialize(target, lvalueType(target));
        const updated = this.materialize({ _: "UnopExpr", op, arg: oldValue }, lvalueType(target));
        this.m.cfg.emit({ _: "AssignStmt", left: target, right: updated });
        return returnOld ? oldValue : updated;
    }

    // ------------------------------------------------------------------
    // Calls / new / literals
    // ------------------------------------------------------------------

    lowerCall(node: ts.CallExpression): ValueDto {
        const callee = node.expression;

        // a?.b(...) / f?.(...) — wrap the whole call in a null-check diamond.
        if (ts.isPropertyAccessExpression(callee) && callee.questionDotToken !== undefined) {
            return this.optionalDiamond(callee.expression, this.safeTypeOf(node), (obj) => ({
                _: "InstanceCallExpr",
                instance: obj,
                method: this.methodSignatureForCall(node, callee.name.text, this.classSignatureFromType(obj.type)),
                args: node.arguments.map((a) =>
                    ts.isSpreadElement(a) ? this.spreadFallback(a) : this.lowerToImmediate(a),
                ),
            }));
        }
        if (node.questionDotToken !== undefined) {
            return this.optionalDiamond(callee, this.safeTypeOf(node), (obj) => ({
                _: "PtrCallExpr",
                ptr: obj,
                method: this.methodSignatureForCall(node, "%call", UNKNOWN_CLASS_SIGNATURE),
                args: node.arguments.map((a) =>
                    ts.isSpreadElement(a) ? this.spreadFallback(a) : this.lowerToImmediate(a),
                ),
            }));
        }

        const args = node.arguments.map((a) =>
            ts.isSpreadElement(a) ? this.spreadFallback(a) : this.lowerToImmediate(a),
        );

        // `super(...)` — call the superclass constructor on `this`.
        if (callee.kind === ts.SyntaxKind.SuperKeyword) {
            const thisLocal = this.m.getOrCreateLocal("this", this.m.thisType());
            const superSignature = this.classSignatureFromType(this.safeTypeOf(callee));
            return {
                _: "InstanceCallExpr",
                instance: thisLocal,
                method: {
                    declaringClass: superSignature,
                    name: CONSTRUCTOR_NAME,
                    parameters: [],
                    returnType: { _: "ClassType", signature: superSignature },
                },
                args,
            };
        }

        // `super.m(...)` — instance call on `this` with the superclass as declaring class.
        if (ts.isPropertyAccessExpression(callee) && callee.expression.kind === ts.SyntaxKind.SuperKeyword) {
            const thisLocal = this.m.getOrCreateLocal("this", this.m.thisType());
            const superSignature = this.classSignatureFromType(this.safeTypeOf(callee.expression));
            return {
                _: "InstanceCallExpr",
                instance: thisLocal,
                method: this.methodSignatureForCall(node, callee.name.text, superSignature),
                args,
            };
        }

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
            const localCallee = this.m.localForIdentifier(callee, this.safeTypeOf(callee));
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
        const inferredType = this.safeTypeOf(node);
        const args = node.arguments ?? ts.factory.createNodeArray();
        const lengthType = args.length === 1 ? this.safeTypeOf(args[0]) : undefined;
        const numericLength = lengthType?._ === "NumberType"
            || (lengthType?._ === "LiteralType" && typeof lengthType.literal === "number");
        if (inferredType._ === "ArrayType" && (args.length === 0 || numericLength)) {
            const size = args.length === 0 ? constant("0", NUMBER_TYPE) : this.lowerToImmediate(args[0]);
            const temp = this.m.newTemp(inferredType);
            this.m.cfg.emit({
                _: "AssignStmt",
                left: temp,
                right: { _: "NewArrayExpr", elementType: arrayElementType(inferredType), size },
            });
            return temp;
        }

        const classType = this.newTargetClassType(node.expression);
        const temp = this.m.newTemp(classType);
        this.m.cfg.emit({ _: "AssignStmt", left: temp, right: { _: "NewExpr", classType } });

        const loweredArgs = args.map((a) =>
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
            right: { _: "InstanceCallExpr", instance: temp, method: ctorSig, args: loweredArgs },
        });
        return temp;
    }

    private lowerArrayLiteral(node: ts.ArrayLiteralExpression): ValueDto {
        const inferredType = this.safeTypeOf(node);
        const contextualType = this.m.converter.contextualTypeOfNode(node);
        // An empty literal is inferred as never[] in isolation. Prefer the
        // contextual annotation (`const xs: number[] = []`) when available.
        const arrayType = node.elements.length === 0 && contextualType._ === "ArrayType"
            ? contextualType
            : inferredType;
        const elementType: TypeDto = arrayType._ === "ArrayType" ? arrayElementType(arrayType) : UNKNOWN_TYPE;
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
    // Closures / object literals
    // ------------------------------------------------------------------

    /**
     * Arrow function / function expression -> anonymous `%AM<n>$<method>` method
     * on the file's %dflt class; the use-site value is a Local of that name typed
     * FunctionType (the ArkAnalyzer shape). Captured outer variables degrade to
     * same-named locals inside the anonymous method (no LexicalEnvType yet).
     */
    private lowerClosure(node: ts.ArrowFunction | ts.FunctionExpression): ValueDto {
        if (this.lowerFunctionBody === undefined) {
            throw new LoweringError("closure in a context without a body lowerer");
        }
        const registry = this.m.ctx.anonymous;
        const name = `${ANONYMOUS_METHOD_PREFIX}${registry.nextMethodId++}$${this.m.methodName}`;
        const declaringClass = registry.defaultClassSignature;

        const { parameters, prologueParams } = buildParameters(this.m.ctx, node);
        const returnType = returnTypeOf(this.m.ctx, node);
        const signature: MethodSignatureDto = { declaringClass, name, parameters, returnType };

        const closureContext = new MethodContext(this.m.ctx, declaringClass, name);
        closureContext.emitPrologue(prologueParams);
        this.lowerFunctionBody(closureContext, node.body);
        registry.methods.push({
            signature,
            modifiers: modifiersOf(node),
            decorators: [],
            body: closureContext.build(),
        });

        return this.m.getOrCreateLocal(name, { _: "FunctionType", signature });
    }

    /**
     * Object literal -> anonymous `%AC<n>$<method>` class (category OBJECT) plus
     * `new` + per-property stores at the use site. Literal methods become methods
     * of the anonymous class.
     */
    private lowerObjectLiteral(node: ts.ObjectLiteralExpression): ValueDto {
        if (this.lowerFunctionBody === undefined) {
            throw new LoweringError("object literal in a context without a body lowerer");
        }
        const registry = this.m.ctx.anonymous;
        const name = `${ANONYMOUS_CLASS_PREFIX}${registry.nextClassId++}$${this.m.methodName}`;
        const signature: ClassSignatureDto = {
            name,
            declaringFile: registry.defaultClassSignature.declaringFile,
        };
        const classType: ClassTypeDto = { _: "ClassType", signature };

        // Evaluate property values BEFORE instantiation (source evaluation order).
        const stores: { name: string; type: TypeDto; value: ValueDto }[] = [];
        const fields: FieldDto[] = [];
        const methods: MethodDto[] = [];
        for (const property of node.properties) {
            if (ts.isPropertyAssignment(property)) {
                const propName = memberName(property.name);
                const propType = this.safeTypeOf(property.initializer);
                fields.push(objectField(signature, propName, propType));
                stores.push({ name: propName, type: propType, value: this.lowerToImmediate(property.initializer) });
            } else if (ts.isShorthandPropertyAssignment(property)) {
                const propName = property.name.text;
                const local = this.m.localForIdentifier(property.name, this.safeTypeOf(property.name));
                fields.push(objectField(signature, propName, local.type));
                stores.push({ name: propName, type: local.type, value: local });
            } else if (ts.isMethodDeclaration(property)) {
                const methodName = memberName(property.name);
                const { parameters, prologueParams } = buildParameters(this.m.ctx, property);
                const methodSignature: MethodSignatureDto = {
                    declaringClass: signature,
                    name: methodName,
                    parameters,
                    returnType: returnTypeOf(this.m.ctx, property),
                };
                const methodContext = new MethodContext(this.m.ctx, signature, methodName);
                methodContext.emitPrologue(prologueParams);
                if (property.body !== undefined) {
                    this.lowerFunctionBody(methodContext, property.body);
                }
                methods.push({
                    signature: methodSignature,
                    modifiers: modifiersOf(property),
                    decorators: [],
                    body: methodContext.build(),
                });
            } else {
                // spread / accessors / computed names degrade the whole literal
                throw new LoweringError(`object literal member: ${ts.SyntaxKind[property.kind]}`);
            }
        }

        registry.classes.push({
            signature,
            modifiers: 0,
            decorators: [],
            category: 5, // OBJECT
            superClassName: "",
            implementedInterfaceNames: [],
            fields,
            methods,
        });

        const temp = this.m.newTemp(classType);
        this.m.cfg.emit({ _: "AssignStmt", left: temp, right: { _: "NewExpr", classType } });
        for (const store of stores) {
            this.m.cfg.emit({
                _: "AssignStmt",
                left: {
                    _: "InstanceFieldRef",
                    instance: temp,
                    field: { declaringClass: signature, name: store.name, type: store.type },
                },
                right: store.value,
            });
        }
        return temp;
    }

    // ------------------------------------------------------------------
    // Optional chaining
    // ------------------------------------------------------------------

    /**
     * `obj?.access` -> null-check diamond:
     *   if (obj != null) %t := <access>; else %t := undefined
     * (loose `!= null` also covers undefined).
     */
    private optionalDiamond(
        objectNode: ts.Expression,
        resultType: TypeDto,
        access: (obj: LocalDto) => ValueDto,
    ): LocalDto {
        const cfg = this.m.cfg;
        const obj = this.lowerToLocal(objectNode);
        const result = this.m.newTemp(resultType);
        const accessLabel = cfg.newLabel();
        const elseLabel = cfg.newLabel();
        const joinLabel = cfg.newLabel();

        cfg.branch(this.relation("!=", obj, constant("null", NULL_TYPE)), accessLabel, elseLabel);
        cfg.placeLabel(accessLabel);
        cfg.emit({ _: "AssignStmt", left: result, right: access(obj) });
        cfg.goto(joinLabel);
        cfg.placeLabel(elseLabel);
        cfg.emit({ _: "AssignStmt", left: result, right: constant("undefined", UNDEFINED_TYPE) });
        cfg.goto(joinLabel);
        cfg.placeLabel(joinLabel);
        return result;
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
        // Hoisted for the same reason as in lowerExpr: raw values are only
        // legal as the RHS of a Local assignment.
        const type = this.safeTypeOf(node.expression);
        return this.materialize(unsupportedValue(node, type), type);
    }
}

// ----------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------

export function constant(value: string, type: TypeDto): ConstantDto {
    return { _: "Constant", value, type };
}

function objectField(declaringClass: ClassSignatureDto, name: string, type: TypeDto): FieldDto {
    return {
        signature: { declaringClass, name, type },
        modifiers: 0,
        decorators: [],
        questionToken: false,
        exclamationToken: false,
    };
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

function arrayElementType(array: Extract<TypeDto, { _: "ArrayType" }>): TypeDto {
    if (array.dimensions <= 1) {
        return array.elementType;
    }
    return { _: "ArrayType", elementType: array.elementType, dimensions: array.dimensions - 1 };
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
