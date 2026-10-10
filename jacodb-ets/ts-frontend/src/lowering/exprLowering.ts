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
 *  - `new C(args)` evaluates C and args before `%t := NewExpr(C); %t := %t.constructor(args)`;
 *  - unresolved identifiers become Locals with UnknownType (e.g. `console`);
 *  - unresolved callees get a method signature with the UNKNOWN class.
 */

import * as ts from "typescript";
import {
    ANONYMOUS_CLASS_PREFIX,
    ANONYMOUS_METHOD_PREFIX,
    ClassCategory,
    CONSTRUCTOR_NAME,
    PATTERN_PARAMETER_PREFIX,
} from "../dto/constants";
import { FieldDto, MethodDto } from "../dto/model";
import { bindingIdentifier, buildParameters, BuiltParameters, classLikeDeclarationOf, memberName, modifiersOf, returnTypeOf } from "./astUtils";
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
    BIGINT_TYPE,
    ClassTypeDto,
    NUMBER_TYPE,
    NULL_TYPE,
    STRING_TYPE,
    TypeDto,
    UNDEFINED_TYPE,
    UNKNOWN_TYPE,
} from "../dto/types";
import {
    ConditionExprDto,
    ConstantDto,
    ImmediateDto,
    LValueDto,
    LocalDto,
    StaticFieldRefDto,
    ValueDto,
} from "../dto/values";
import { syntaxKindName, unsupportedValue } from "./diagnostics";
import { MethodContext } from "./methodBuilder";
import { IteratorLowerer, throwRuntimeError } from "./iteratorLowering";

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
export type FunctionBodyLowerer = (
    m: MethodContext,
    body: ts.ConciseBody,
    emitPrologue: (afterParameter: (
        parameter: BuiltParameters["prologueParams"][number],
        local: LocalDto,
    ) => void) => void,
) => void;

type OptionalChainSegment = ts.PropertyAccessExpression | ts.ElementAccessExpression | ts.CallExpression;

interface OptionalChain {
    segments: OptionalChainSegment[];
}

interface OptionalChainValue {
    value: ValueDto;
    methodReceiver?: LocalDto;
    staticTarget?: ClassSignatureDto;
}

export class ExprLowerer {
    private unsupportedExpressionCount = 0;
    private computedConstructorCalleeDepth = 0;

    constructor(
        private readonly m: MethodContext,
        private readonly lowerFunctionBody?: FunctionBodyLowerer,
        private readonly afterDirectSuperCall?: () => void,
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
                    this.unsupportedExpressionCount++;
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

    /** Lower to an immediate, hoisting non-immediate expressions into a temp. */
    lowerToImmediate(node: ts.Expression): ImmediateDto {
        const value = this.lowerExpr(node);
        if (value._ === "Local" || value._ === "Constant" || value._ === "ClassValueRef") {
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

    /** Convert at the source key's evaluation point, before value/default side effects. */
    lowerPropertyKey(node: ts.Expression): ImmediateDto {
        return this.materialize({ _: "ToPropertyKeyExpr", arg: this.lowerToImmediate(node) }, UNKNOWN_TYPE);
    }

    /** Get/Put rejects a nullish base before converting the already evaluated key. */
    private propertyAccessKey(key: ImmediateDto, instance: LocalDto): ImmediateDto {
        this.materialize({ _: "RequireObjectCoercibleExpr", arg: instance, type: instance.type }, instance.type);
        return this.materialize({ _: "ToPropertyKeyExpr", arg: key }, UNKNOWN_TYPE);
    }

    private propertyReferenceForOperation(target: LValueDto): LValueDto {
        return target._ === "PropertyRef"
            ? { ...target, key: this.propertyAccessKey(target.key, target.instance) }
            : target;
    }

    /** Save components before Get/coercion, retaining raw key/index conversion at each operation. */
    private snapshotReference(target: LValueDto): LValueDto {
        if (target._ === "InstanceFieldRef" || target._ === "PropertyRef") {
            return { ...target, instance: this.m.snapshotToLocal(target.instance, target.instance.type) };
        }

        if (target._ === "ArrayRef") {
            const array = target.array;
            const index = target.index;
            return {
                ...target,
                array: array._ === "Local" ? this.m.snapshotToLocal(array, array.type) : array,
                index: index._ === "Local" ? this.m.snapshotToLocal(index, index.type) : index,
            };
        }

        return target;
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
            return constant(numericConstantText(node), NUMBER_TYPE);
        }
        if (ts.isBigIntLiteral(node)) {
            return constant(bigIntConstantText(node), BIGINT_TYPE);
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
        if (ts.isRegularExpressionLiteral(node)) {
            return this.lowerRegularExpressionLiteral(node);
        }
        if (ts.isTemplateExpression(node)) {
            return this.lowerTemplate(node);
        }
        if (ts.isTaggedTemplateExpression(node)) {
            return this.lowerTaggedTemplate(node);
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
        if (ts.isClassExpression(node)) {
            return this.lowerClassExpression(node);
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
                isDelegating: node.asteriskToken !== undefined,
            };
        }
        throw new LoweringError(ts.SyntaxKind[node.kind]);
    }

    // ------------------------------------------------------------------
    // Leaves
    // ------------------------------------------------------------------

    private lowerIdentifier(node: ts.Identifier): ValueDto {
        if (this.m.isUninitializedParameter(node)) {
            throwRuntimeError(this.m, "ReferenceError", `Cannot access '${node.text}' before initialization`);
            return constant("undefined", UNDEFINED_TYPE);
        }
        const nonFiniteNumber = this.builtInNonFiniteNumber(node);
        if (nonFiniteNumber !== undefined) {
            return constant(nonFiniteNumber, NUMBER_TYPE);
        }
        if (node.text === "undefined") {
            return constant("undefined", UNDEFINED_TYPE);
        }
        const declaration = this.m.converter.symbolOf(node)?.declarations?.find((candidate) =>
            ts.isClassDeclaration(candidate)
            || (ts.isModuleDeclaration(candidate) && isProjectFile(candidate))
            || (ts.isSourceFile(candidate) && !candidate.isDeclarationFile) ||
            (ts.isFunctionDeclaration(candidate) &&
                (ts.isSourceFile(candidate.parent) || ts.isModuleBlock(candidate.parent))),
        );
        if (declaration !== undefined && ts.isClassDeclaration(declaration) &&
            isProjectFile(declaration) &&
            (ts.isSourceFile(declaration.parent) || ts.isModuleBlock(declaration.parent))) {
            const signature = this.m.converter.classSignatureOf(declaration);
            return { _: "ClassValueRef", signature, type: { _: "ClassValueType", signature } };
        }
        const captured = this.m.capturedRefForIdentifier(node);
        if (captured !== undefined) return captured;
        const moduleField = this.m.moduleFieldForIdentifier(node);
        if (moduleField !== undefined) return moduleField;
        if (declaration !== undefined) {
            throw new LoweringError(`runtime value of declaration '${node.text}' is not represented in EtsIR`);
        }
        // Other named references are locals; unresolved globals (e.g. console)
        // become locals with UnknownType, same as ArkAnalyzer.
        return this.m.localForIdentifier(node, this.safeTypeOf(node));
    }

    /** Built-in numeric globals are constants, but a project declaration must still shadow them. */
    private builtInNonFiniteNumber(node: ts.Identifier): "Infinity" | "NaN" | undefined {
        if (node.text !== "Infinity" && node.text !== "NaN") return undefined;
        const symbol = this.m.converter.symbolOf(node);
        const declarations = symbol?.declarations;
        if (declarations === undefined || declarations.length === 0 || !declarations.every((declaration) =>
            this.m.ctx.isDefaultLibrarySourceFile(declaration.getSourceFile()),
        )) {
            return undefined;
        }
        return node.text;
    }

    // ------------------------------------------------------------------
    // Field / array access
    // ------------------------------------------------------------------

    private lowerPropertyAccess(node: ts.PropertyAccessExpression): ValueDto {
        const fieldName = node.name.text;
        const fieldType = this.safeTypeOf(node);

        if (this.isProjectClassProperty(node.expression)) {
            this.evaluateProjectClassPropertyReceiver(node.expression);
            throw new LoweringError("member read through a mutable class property is not represented in EtsIR");
        }

        const chain = optionalChain(node);
        if (chain !== undefined) {
            return this.lowerOptionalChain(node, chain);
        }

        const importedFunction = this.importedScopeFunctionField(node);
        if (importedFunction !== undefined) return importedFunction;

        // `this.f` inside a STATIC method addresses a static field of the class.
        if (node.expression.kind === ts.SyntaxKind.ThisKeyword && this.m.isStaticMethod) {
            return {
                _: "StaticFieldRef",
                field: { declaringClass: this.m.declaringClass, name: fieldName, type: fieldType },
            };
        }

        // Namespace class properties can be reassigned at runtime (`N.A = B`).
        // A declaration signature would freeze the original constructor.
        const receiverName = ts.isIdentifier(node.expression)
            ? node.expression
            : ts.isPropertyAccessExpression(node.expression)
              ? node.expression.name
              : undefined;
        const receiverIsNamespace = receiverName !== undefined &&
            this.m.converter.symbolOf(receiverName)?.declarations?.some(
                (declaration) => ts.isModuleDeclaration(declaration) || ts.isSourceFile(declaration),
            );
        if (receiverIsNamespace) {
            const declaration = this.m.converter.symbolOf(node.name)?.declarations?.find(ts.isClassDeclaration);
            if (declaration !== undefined && isProjectFile(declaration) &&
                (ts.isSourceFile(declaration.parent) || ts.isModuleBlock(declaration.parent))) {
                throw new LoweringError("mutable namespace class property is not represented in EtsIR");
            }
        }

        const staticTarget = this.classLikeSignatureOf(node.expression);
        if (staticTarget !== undefined) {
            return {
                _: "StaticFieldRef",
                field: { declaringClass: staticTarget, name: fieldName, type: fieldType },
            };
        }

        const instance = this.lowerToLocal(node.expression);
        if (this.m.converter.symbolOf(node.name)?.declarations?.some((declaration) =>
            ts.isGetAccessorDeclaration(declaration) && ts.isObjectLiteralExpression(declaration.parent),
        )) {
            return { _: "PropertyRef", instance, key: constant(fieldName, STRING_TYPE), type: fieldType };
        }
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

    private isNamespaceObject(node: ts.Expression): boolean {
        const expression = unwrapTransparentExpression(node);
        const symbol = ts.isIdentifier(expression) ? this.m.converter.symbolOf(expression) : undefined;
        const typeSymbol = this.m.checker.getTypeAtLocation(node).getSymbol();
        return [symbol, typeSymbol].some((candidate) => candidate?.declarations?.some(ts.isModuleDeclaration));
    }

    private lowerElementAccess(node: ts.ElementAccessExpression): ValueDto {
        const chain = optionalChain(node);
        if (chain !== undefined) {
            return this.lowerOptionalChain(node, chain);
        }
        const receiverType = this.m.checker.getTypeAtLocation(node.expression);
        if (!this.m.checker.isArrayType(receiverType)
            && !this.m.checker.isTupleType(receiverType)) {
            if (this.computedConstructorCalleeDepth > 0 && this.isNamespaceObject(node.expression)) {
                this.lowerToImmediate(node.expression);
                this.lowerToImmediate(node.argumentExpression);
                throw new LoweringError("computed namespace constructor access has no runtime namespace object in EtsIR");
            }
            const instance = this.snapshotToLocal(node.expression);
            return {
                _: "PropertyRef",
                instance,
                key: this.propertyAccessKey(this.lowerToImmediate(node.argumentExpression), instance),
                type: this.safeTypeOf(node),
            };
        }

        const array = this.snapshotValueIfReassigned(
            this.lowerToImmediate(node.expression),
            node.expression,
            node.argumentExpression,
            this.safeTypeOf(node.expression),
        );
        return {
            _: "ArrayRef",
            array,
            index: this.lowerToImmediate(node.argumentExpression),
            type: this.safeTypeOf(node),
        };
    }

    /** Lower every segment of one continuous optional chain under its preceding guards. */
    private lowerOptionalChain(
        node: OptionalChainSegment,
        chain: OptionalChain,
        complete: (current: OptionalChainValue) => ValueDto = (current) => current.value,
    ): ValueDto {
        const root = chain.segments[0];
        const initial = ts.isCallExpression(root)
            ? this.lowerOptionalChainCallee(root)
            : { value: this.snapshotToLocal(root.expression) };
        return this.lowerOptionalChainSegments(node, chain.segments, 0, initial, complete);
    }

    private lowerOptionalChainCallee(root: ts.CallExpression): OptionalChainValue {
        const callee = unwrapTransparentExpression(root.expression);
        if (ts.isPropertyAccessExpression(callee) || ts.isElementAccessExpression(callee)) {
            if (ts.isPropertyAccessExpression(callee)
                && this.importedScopeFunctionField(callee) !== undefined) {
                throw new LoweringError("module namespace call receiver is not represented in EtsIR");
            }

            const chain = optionalChain(callee);
            if (chain !== undefined) {
                // A grouped chain completes before the outer call; carry its receiver through the joins.
                const methodReceiver = this.materialize(constant("undefined", UNDEFINED_TYPE), this.safeTypeOf(callee.expression));
                const value = this.lowerOptionalChain(callee, chain, (current) => {
                    if (current.methodReceiver !== undefined) {
                        this.m.cfg.emit({ _: "AssignStmt", left: methodReceiver, right: current.methodReceiver });
                    }
                    return current.value;
                });
                return { value, methodReceiver };
            }

            const staticTarget = ts.isPropertyAccessExpression(callee)
                ? this.classLikeSignatureOf(callee.expression)
                : undefined;
            if (staticTarget !== undefined && ts.isPropertyAccessExpression(callee)) {
                return {
                    value: {
                        _: "StaticFieldRef",
                        field: { declaringClass: staticTarget, name: callee.name.text, type: this.safeTypeOf(callee) },
                    },
                    staticTarget,
                };
            }
            const methodReceiver = this.snapshotToLocal(callee.expression);
            return { value: this.accessFromInstance(callee, methodReceiver), methodReceiver };
        }
        return { value: this.snapshotToLocal(callee, root.arguments) };
    }

    private lowerOptionalChainSegments(
        node: OptionalChainSegment,
        segments: readonly OptionalChainSegment[],
        index: number,
        current: OptionalChainValue,
        complete: (current: OptionalChainValue) => ValueDto,
    ): ValueDto {
        if (index >= segments.length) return complete(current);

        const segment = segments[index];
        if (ts.isCallExpression(segment)) {
            const continueAfterCall = (callee: OptionalChainValue): ValueDto =>
                this.lowerOptionalChainSegments(
                    node,
                    segments,
                    index + 1,
                    { value: this.callOptionalChainSegment(segment, callee) },
                    complete,
                );
            if (segment.questionDotToken === undefined) {
                return continueAfterCall(current);
            }
            const methodValue = this.optionalChainLocal(current.value, this.safeTypeOf(segment.expression));
            return this.optionalEvaluatedDiamond(methodValue, this.safeTypeOf(node), () =>
                continueAfterCall({ ...current, value: methodValue }));
        }

        const receiver = this.optionalChainLocal(current.value, this.safeTypeOf(segment.expression));
        const continueAfterAccess = (): ValueDto => this.lowerOptionalChainSegments(
            node,
            segments,
            index + 1,
            { value: this.accessFromInstance(segment, receiver), methodReceiver: receiver },
            complete,
        );
        return segment.questionDotToken === undefined
            ? continueAfterAccess()
            : this.optionalEvaluatedDiamond(receiver, this.safeTypeOf(node), continueAfterAccess);
    }

    private callOptionalChainSegment(node: ts.CallExpression, callee: OptionalChainValue): ValueDto {
        const expression = unwrapTransparentExpression(node.expression);
        if (ts.isPropertyAccessExpression(expression)) {
            if (callee.staticTarget !== undefined) {
                return {
                    _: "StaticCallExpr",
                    method: this.methodSignatureForCall(node, expression.name.text, callee.staticTarget),
                    args: this.lowerCallArguments(node),
                };
            }
        }
        const ptr = this.optionalChainLocal(callee.value, this.safeTypeOf(node.expression));
        return {
            _: "PtrCallExpr",
            ptr,
            ...(callee.methodReceiver === undefined ? {} : { receiver: callee.methodReceiver }),
            method: this.methodSignatureForCall(node, "%call", UNKNOWN_CLASS_SIGNATURE),
            args: this.lowerCallArguments(node),
        };
    }

    private optionalChainLocal(value: ValueDto, type: TypeDto): LocalDto {
        return value._ === "Local" ? value : this.materialize(value, type);
    }

    private accessFromInstance(
        node: ts.PropertyAccessExpression | ts.ElementAccessExpression,
        instance: LocalDto,
    ): ValueDto {
        if (ts.isPropertyAccessExpression(node)) {
            const fieldType = this.safeTypeOf(node);
            if (this.m.converter.symbolOf(node.name)?.declarations?.some((declaration) =>
                ts.isGetAccessorDeclaration(declaration) && ts.isObjectLiteralExpression(declaration.parent),
            )) {
                return { _: "PropertyRef", instance, key: constant(node.name.text, STRING_TYPE), type: fieldType };
            }
            return {
                _: "InstanceFieldRef",
                instance,
                field: {
                    declaringClass: this.classSignatureFromType(instance.type),
                    name: node.name.text,
                    type: fieldType,
                },
            };
        }
        const receiverType = this.m.checker.getTypeAtLocation(node.expression);
        if (!this.m.checker.isArrayType(receiverType) && !this.m.checker.isTupleType(receiverType)) {
            if (this.computedConstructorCalleeDepth > 0 && this.isNamespaceObject(node.expression)) {
                this.lowerToImmediate(node.argumentExpression);
                throw new LoweringError("computed namespace constructor access has no runtime namespace object in EtsIR");
            }
            return {
                _: "PropertyRef",
                instance,
                key: this.propertyAccessKey(this.lowerToImmediate(node.argumentExpression), instance),
                type: this.safeTypeOf(node),
            };
        }
        return {
            _: "ArrayRef",
            array: instance,
            index: this.lowerToImmediate(node.argumentExpression),
            type: this.safeTypeOf(node),
        };
    }

    /** Assignment target. */
    lowerLValue(node: ts.Expression, laterExpression?: ts.Expression): LValueDto {
        if (ts.isIdentifier(node) && this.m.isUninitializedParameter(node)) {
            throwRuntimeError(this.m, "ReferenceError", `Cannot access '${node.text}' before initialization`);
        }
        if (ts.isParenthesizedExpression(node)) {
            return this.lowerLValue(node.expression, laterExpression);
        }
        if (ts.isIdentifier(node)) {
            const captured = this.m.capturedRefForIdentifier(node);
            if (captured !== undefined) return captured;
            const moduleField = this.m.moduleFieldForIdentifier(node);
            if (moduleField !== undefined) return moduleField;
            return this.m.localForIdentifier(node, this.safeTypeOf(node));
        }
        if (ts.isPropertyAccessExpression(node)) {
            if (this.importedScopeFunctionField(node) !== undefined) {
                throw new LoweringError("module namespace function properties are read-only");
            }
            const ref = this.lowerPropertyAccess(node);
            if (ref._ === "PropertyRef") {
                return this.mayReassign(node.expression, laterExpression)
                    ? { ...ref, instance: this.m.snapshotToLocal(ref.instance, ref.instance.type) }
                    : ref;
            }
            if (ref._ === "InstanceFieldRef" || ref._ === "StaticFieldRef") {
                return ref._ === "StaticFieldRef" || !this.mayReassign(node.expression, laterExpression)
                    ? ref
                    : { ...ref, instance: this.m.snapshotToLocal(ref.instance, ref.instance.type) };
            }
            throw new LoweringError("property access did not produce a field ref");
        }
        if (ts.isElementAccessExpression(node)) {
            const receiverType = this.m.checker.getTypeAtLocation(node.expression);
            if (!this.m.checker.isArrayType(receiverType) && !this.m.checker.isTupleType(receiverType)) {
                const instance = this.snapshotToLocal(node.expression);
                const key = this.snapshotToLocal(node.argumentExpression);

                // Reference evaluation retains the raw key; Get and Put each convert it.
                return { _: "PropertyRef", instance, key, type: this.safeTypeOf(node) };
            }
            const ref = this.lowerElementAccess(node);
            if (ref._ === "ArrayRef") {
                return {
                    ...ref,
                    array: this.snapshotValueIfReassigned(
                        ref.array,
                        node.expression,
                        laterExpression,
                        this.safeTypeOf(node.expression),
                    ),
                    index: this.snapshotValueIfReassigned(
                        ref.index,
                        node.argumentExpression,
                        laterExpression,
                        this.safeTypeOf(node.argumentExpression),
                    ),
                };
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
            const arg = this.lowerImmediateBefore(node.left, node.right);
            if (this.isProjectClassProperty(node.right)) {
                this.evaluateProjectClassPropertyReceiver(node.right);
                throw new LoweringError("instanceof through a mutable class property is not represented in EtsIR");
            }

            const unsupportedBeforeRight = this.unsupportedExpressionCount;
            const checkValue = this.lowerToImmediate(node.right);

            if (this.unsupportedExpressionCount !== unsupportedBeforeRight) {
                this.m.diagnostics.warn(node, "instanceof constructor value cannot be represented in EtsIR");
                return this.materialize(unsupportedValue(node, BOOLEAN_TYPE), BOOLEAN_TYPE);
            }

            return {
                _: "InstanceOfExpr",
                arg,
                checkValue,
                checkType: this.checkTypeOf(checkValue),
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
            return this.relation(
                relationOp,
                this.lowerImmediateBefore(node.left, node.right),
                this.lowerToImmediate(node.right),
            );
        }

        const binaryOp = BINARY_BY_SYNTAX[opKind];
        if (binaryOp !== undefined) {
            return {
                _: "BinopExpr",
                op: binaryOp,
                left: this.lowerImmediateBefore(node.left, node.right),
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

    /** Lower a boolean context expression once and preserve its source value shape. */
    lowerCondition(node: ts.Expression, trueTarget: number, falseTarget: number): void {
        this.m.withOrigin(node, () => this.lowerConditionImpl(node, trueTarget, falseTarget));
    }

    private lowerConditionImpl(node: ts.Expression, trueTarget: number, falseTarget: number): void {
        const condition = this.lowerExpr(node);
        this.m.cfg.branch(condition, trueTarget, falseTarget);
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
                cfg.branch(left, rightLabel, leftLabel);
                break;
            case ts.SyntaxKind.BarBarToken:
                cfg.branch(left, leftLabel, rightLabel);
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
        const targetNode = unwrapTransparentExpression(node.left);
        if (opKind === ts.SyntaxKind.EqualsToken
            && ts.isIdentifier(targetNode) && this.m.isUninitializedParameter(targetNode)) {
            // Resolving the binding is allowed; PutValue checks the TDZ after the RHS.
            const rhs = this.lowerToImmediate(node.right);
            throwRuntimeError(this.m, "ReferenceError", `Cannot access '${targetNode.text}' before initialization`);
            return rhs;
        }

        let target = this.lowerLValue(node.left, node.right);

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
            target = this.snapshotReference(target);
            const oldValue = target._ === "Local"
                ? target
                : this.materialize(this.propertyReferenceForOperation(target), lvalueType(target));
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
        if (target._ === "PropertyRef" && rhs._ === "Local") {
            rhs = this.materialize(rhs, rhs.type);
        }

        this.m.cfg.emit({ _: "AssignStmt", left: this.propertyReferenceForOperation(target), right: rhs });
        return target._ === "Local" ? target : (rhs as ImmediateDto);
    }

    private lowerLogicalAssignment(node: ts.BinaryExpression, target: LValueDto): LocalDto {
        target = this.snapshotReference(target);
        const cfg = this.m.cfg;
        const oldValue = target._ === "Local"
            ? target
            : this.materialize(this.propertyReferenceForOperation(target), lvalueType(target));
        const result = this.m.newTemp(this.safeTypeOf(node));
        const assignLabel = cfg.newLabel();
        const keepLabel = cfg.newLabel();
        const joinLabel = cfg.newLabel();

        switch (node.operatorToken.kind) {
            case ts.SyntaxKind.AmpersandAmpersandEqualsToken:
                cfg.branch(oldValue, assignLabel, keepLabel);
                break;
            case ts.SyntaxKind.BarBarEqualsToken:
                cfg.branch(oldValue, keepLabel, assignLabel);
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
        if (target._ === "PropertyRef" && rhs._ === "Local") {
            rhs = this.materialize(rhs, rhs.type);
        }

        cfg.emit({ _: "AssignStmt", left: this.propertyReferenceForOperation(target), right: rhs });
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
                    return constant(`-${numericConstantText(node.operand)}`, NUMBER_TYPE);
                }
                if (ts.isBigIntLiteral(node.operand)) {
                    return constant(`-${bigIntConstantText(node.operand)}`, BIGINT_TYPE);
                }
                const operand = this.lowerToImmediate(node.operand);
                if (operand._ === "Constant" && operand.value === "Infinity" && operand.type._ === "NumberType") {
                    return constant("-Infinity", NUMBER_TYPE);
                }
                return { _: "UnopExpr", op: "-", arg: operand };
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
        const target = this.snapshotReference(this.lowerLValue(operand));
        const rawValue = target._ === "Local"
            ? target
            : this.materialize(this.propertyReferenceForOperation(target), lvalueType(target));
        const numericType = toNumericType(lvalueType(target));
        const oldValue = this.materialize({ _: "ToNumericExpr", arg: rawValue, type: numericType }, numericType);

        if (target._ === "Local") {
            this.m.cfg.emit({ _: "AssignStmt", left: target, right: { _: "UnopExpr", op, arg: oldValue } });
            return returnOld ? oldValue : target;
        }

        const updated = this.materialize({ _: "UnopExpr", op, arg: oldValue }, numericType);
        this.m.cfg.emit({ _: "AssignStmt", left: this.propertyReferenceForOperation(target), right: updated });
        return returnOld ? oldValue : updated;
    }

    // ------------------------------------------------------------------
    // Calls / new / literals
    // ------------------------------------------------------------------

    lowerCall(node: ts.CallExpression): ValueDto {
        const callee = unwrapTransparentExpression(node.expression);

        // Namespace calls require a module-object receiver that is not represented in EtsIR.
        if (ts.isPropertyAccessExpression(callee)
            && this.importedScopeFunctionField(callee) !== undefined) {
            throw new LoweringError("module namespace call receiver is not represented in EtsIR");
        }

        if (ts.isPropertyAccessExpression(callee) && this.isProjectClassProperty(callee.expression)) {
            this.evaluateProjectClassPropertyReceiver(callee.expression);
            throw new LoweringError("call through a mutable class property is not represented in EtsIR");
        }

        const chain = optionalChain(node);
        if (chain !== undefined) {
            return this.lowerOptionalChain(node, chain);
        }

        if ((ts.isPropertyAccessExpression(callee) || ts.isElementAccessExpression(callee))
            && optionalChain(callee) !== undefined) {
            return this.callOptionalChainSegment(node, this.lowerOptionalChainCallee(node));
        }

        // `super(...)` — call the superclass constructor on `this`.
        if (callee.kind === ts.SyntaxKind.SuperKeyword) {
            const thisLocal = this.m.getOrCreateLocal("this", this.m.thisType());
            const superSignature = this.classSignatureFromType(this.safeTypeOf(callee));
            const superCall: ValueDto = {
                _: "InstanceCallExpr",
                instance: thisLocal,
                method: this.methodSignatureForCall(node, CONSTRUCTOR_NAME, superSignature),
                args: this.lowerCallArguments(node),
            };
            if (this.afterDirectSuperCall === undefined) {
                return superCall;
            }
            // A direct super call may be the value of `return`, assignment, or another
            // expression. Materialize its result here, after its arguments were lowered,
            // so the constructor callback follows the actual call on every such path.
            const result = this.materialize(superCall, this.safeTypeOf(node));
            this.afterDirectSuperCall();
            return result;
        }

        // `super.m(...)` — instance call on `this` with the superclass as declaring class.
        if (ts.isPropertyAccessExpression(callee) && callee.expression.kind === ts.SyntaxKind.SuperKeyword) {
            const thisLocal = this.m.getOrCreateLocal("this", this.m.thisType());
            const superSignature = this.classSignatureFromType(this.safeTypeOf(callee.expression));
            return {
                _: "InstanceCallExpr",
                instance: thisLocal,
                method: this.methodSignatureForCall(node, callee.name.text, superSignature),
                args: this.lowerCallArguments(node),
            };
        }

        if (ts.isPropertyAccessExpression(callee)) {
            const methodName = callee.name.text;

            // `this.m()` inside a static method targets a static member of the
            // current class, just like `C.m()`.
            if (callee.expression.kind === ts.SyntaxKind.ThisKeyword && this.m.isStaticMethod) {
                return {
                    _: "StaticCallExpr",
                    method: this.methodSignatureForCall(node, methodName, this.m.declaringClass),
                    args: this.lowerCallArguments(node),
                };
            }
            const staticTarget = this.classLikeSignatureOf(callee.expression);
            if (staticTarget !== undefined) {
                return {
                    _: "StaticCallExpr",
                    method: this.methodSignatureForCall(node, methodName, staticTarget),
                    args: this.lowerCallArguments(node),
                };
            }
            // A property getter runs before arguments and may replace the receiver binding.
            const instance = this.snapshotToLocal(callee.expression);
            const ptr = this.materialize(this.accessFromInstance(callee, instance), this.safeTypeOf(callee));
            return {
                _: "PtrCallExpr",
                ptr,
                receiver: instance,
                method: this.methodSignatureForCall(node, methodName, this.classSignatureFromType(instance.type)),
                args: this.lowerCallArguments(node),
            };
        }

        if (ts.isIdentifier(callee)) {
            const resolved = this.resolveCalleeDeclaration(node);
            if (resolved === undefined && !isDeclaredLocalValue(this.m, callee)) {
                // Fully unresolved global callee — static call with the UNKNOWN class.
                return {
                    _: "StaticCallExpr",
                    method: this.methodSignatureForCall(node, callee.text, UNKNOWN_CLASS_SIGNATURE),
                    args: this.lowerCallArguments(node),
                };
            }
            // Snapshot a function value before arguments; an argument may mutate
            // the binding but must not change this call's selected callee.
            const localCallee = this.snapshotToLocal(callee, node.arguments);
            const moduleField = this.m.moduleFieldForIdentifier(callee);
            return {
                _: "PtrCallExpr",
                ptr: localCallee,
                method: this.methodSignatureForCall(node, callee.text, moduleField?.field.declaringClass ?? UNKNOWN_CLASS_SIGNATURE),
                args: this.lowerCallArguments(node),
            };
        }

        if (ts.isElementAccessExpression(callee)) {
            const receiver = this.snapshotToLocal(callee.expression);
            const ptr = this.materialize({
                _: "PropertyRef",
                instance: receiver,
                key: this.propertyAccessKey(this.lowerToImmediate(callee.argumentExpression), receiver),
                type: this.safeTypeOf(callee),
            }, this.safeTypeOf(callee));
            return {
                _: "PtrCallExpr",
                ptr,
                receiver,
                method: this.methodSignatureForCall(node, "%call", UNKNOWN_CLASS_SIGNATURE),
                args: this.lowerCallArguments(node),
            };
        }

        // Computed callee is evaluated before arguments.
        const ptr = this.snapshotToLocal(callee, node.arguments);
        return {
            _: "PtrCallExpr",
            ptr,
            method: this.methodSignatureForCall(node, "%call", UNKNOWN_CLASS_SIGNATURE),
            args: this.lowerCallArguments(node),
        };
    }

    private lowerCallArguments(node: ts.CallExpression): ValueDto[] {
        return this.lowerArguments(node.arguments);
    }

    private lowerArguments(argumentsList: readonly ts.Expression[]): ImmediateDto[] {
        const result: ImmediateDto[] = [];

        argumentsList.forEach((argument, index) => {
            if (!ts.isSpreadElement(argument)) {
                result.push(this.lowerImmediateBefore(argument, argumentsList.slice(index + 1)));
                return;
            }

            const elementTypes = this.fixedSpreadElementTypes(argument.expression);
            const iterable = this.lowerToImmediate(argument.expression);
            if (elementTypes === undefined) {
                throw new LoweringError("spread argument has no statically known length");
            }

            const expandedType: TypeDto = { _: "ArrayType", elementType: UNKNOWN_TYPE, dimensions: 1 };
            const expanded = this.materialize({
                _: "SpreadExpansionExpr",
                iterable,
                expectedCount: elementTypes.length,
            }, expandedType);

            elementTypes.forEach((elementType, elementIndex) => {
                const element = this.materialize({
                    _: "ArrayRef",
                    array: expanded,
                    index: constant(String(elementIndex), NUMBER_TYPE),
                    type: elementType,
                }, elementType);
                result.push(element);
            });
        });

        return result;
    }

    private fixedSpreadElementTypes(expression: ts.Expression): TypeDto[] | undefined {
        const value = unwrapTransparentExpression(expression);
        if (ts.isArrayLiteralExpression(value) && value.elements.every((element) =>
            !ts.isSpreadElement(element) && !ts.isOmittedExpression(element))) {
            return value.elements.map((element) => this.safeTypeOf(element));
        }

        const type = this.m.checker.getTypeAtLocation(expression);
        if (!this.m.checker.isTupleType(type)) return undefined;

        const tuple = (type as ts.TupleTypeReference).target;
        return tuple.elementFlags.every((flag) => flag === ts.ElementFlags.Required)
            ? this.m.checker.getTypeArguments(type as ts.TupleTypeReference)
                .map((elementType) => this.m.converter.convertType(elementType))
            : undefined;
    }

    /** Preserve an already selected receiver/callee even if an argument mutates its source binding. */
    private snapshotToLocal(node: ts.Expression, laterExpressions?: readonly ts.Expression[]): LocalDto {
        const value = this.lowerToLocal(node);
        return laterExpressions !== undefined && !this.mayReassign(node, laterExpressions)
            ? value
            : this.m.snapshotToLocal(value, value.type);
    }

    /** Preserve a source local while leaving constants and private temporaries immediate. */
    private snapshotImmediate(value: ImmediateDto): ImmediateDto {
        return value._ === "Constant" ? value : this.m.snapshotToLocal(value, value.type);
    }

    lowerImmediateBefore(node: ts.Expression, later: ts.Node | readonly ts.Node[]): ImmediateDto {
        return this.snapshotImmediateIfReassigned(this.lowerToImmediate(node), node, later);
    }

    private snapshotImmediateIfReassigned(
        value: ImmediateDto,
        source: ts.Expression,
        later: ts.Node | readonly ts.Node[] | undefined,
    ): ImmediateDto {
        return this.mayReassign(source, later) ? this.snapshotImmediate(value) : value;
    }

    private snapshotValueIfReassigned(
        value: ValueDto,
        source: ts.Expression,
        later: ts.Node | readonly ts.Node[] | undefined,
        type: TypeDto,
    ): ValueDto {
        if (!this.mayReassign(source, later)) return value;
        return value._ === "Local" || value._ === "Constant"
            ? this.snapshotImmediate(value)
            : this.m.snapshotToLocal(value, type);
    }

    /** Whether later evaluation can replace the binding denoted by [source]. */
    private mayReassign(source: ts.Expression, later: ts.Node | readonly ts.Node[] | undefined): boolean {
        const identifier = bindingIdentifier(source);
        if (identifier === undefined || later === undefined) return false;
        const sourceSymbol = this.m.converter.symbolOf(identifier);
        const nodes = Array.isArray(later) ? later : [later];
        return nodes.some((node) =>
            this.containsReassignment(node, identifier, sourceSymbol) || containsPossibleSideEffect(node));
    }

    private containsReassignment(node: ts.Node, source: ts.Identifier, sourceSymbol: ts.Symbol | undefined): boolean {
        let found = false;
        const visit = (current: ts.Node): void => {
            if (found) return;
            const target = assignmentTarget(current);
            if (target !== undefined && sameBinding(this.m.converter.symbolOf(target), target, sourceSymbol, source)) {
                found = true;
                return;
            }
            ts.forEachChild(current, visit);
        };
        visit(node);
        return found;
    }

    private lowerNew(node: ts.NewExpression): ValueDto {
        const args = node.arguments ?? ts.factory.createNodeArray();

        if (this.isProjectClassProperty(node.expression)) {
            this.evaluateProjectClassPropertyReceiver(node.expression);
            throw new LoweringError("constructor read from a mutable class property is not represented in EtsIR");
        }

        const target = unwrapTransparentExpression(node.expression);
        const declarations = ts.isIdentifier(target)
            ? this.m.converter.symbolOf(target)?.declarations
            : undefined;
        // Keep the existing static lowering for built-in globals such as Date and Array.
        // Their symbols may also contain declarations from library augmentations.
        const standardLibraryIdentifier = declarations?.some(
            (declaration) => this.m.ctx.isDefaultLibrarySourceFile(declaration.getSourceFile()),
        ) === true;
        const staticConstructor = this.classLikeSignatureOf(node.expression) !== undefined || standardLibraryIdentifier;

        const inferredType = this.safeTypeOf(node);
        const lengthType = args.length === 1 ? this.safeTypeOf(args[0]) : undefined;
        const numericLength = lengthType?._ === "NumberType"
            || (lengthType?._ === "LiteralType" && typeof lengthType.literal === "number");
        if (standardLibraryIdentifier && inferredType._ === "ArrayType" && (args.length === 0 || numericLength)) {
            const size = args.length === 0 ? constant("0", NUMBER_TYPE) : this.lowerToImmediate(args[0]);
            const temp = this.m.newTemp(inferredType);
            this.m.cfg.emit({
                _: "AssignStmt",
                left: temp,
                right: { _: "NewArrayExpr", elementType: arrayElementType(inferredType), size },
            });
            return temp;
        }

        const unsupportedBeforeConstructor = this.unsupportedExpressionCount;
        const constructorValue = staticConstructor
            ? undefined
            : this.lowerDynamicConstructorValue(node.expression, args);
        if (this.unsupportedExpressionCount !== unsupportedBeforeConstructor) {
            throw new LoweringError("new constructor value cannot be represented in EtsIR");
        }

        const loweredArgs = this.lowerArguments(args);
        const classType = staticConstructor ? this.newTargetClassType(node.expression) : inferredType;
        const temp = this.m.newTemp(classType);
        const allocation = constructorValue === undefined
            ? { _: "NewExpr" as const, classType }
            : { _: "NewExpr" as const, classType, constructorValue };
        this.m.cfg.emit({ _: "AssignStmt", left: temp, right: allocation });
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

    private lowerDynamicConstructorValue(node: ts.Expression, args: readonly ts.Expression[]): ImmediateDto {
        this.computedConstructorCalleeDepth++;
        try {
            const value = this.lowerImmediateBefore(node, args);
            return value._ === "ClassValueRef" ? this.materialize(value, value.type) : value;
        } finally {
            this.computedConstructorCalleeDepth--;
        }
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
        if (node.elements.some(ts.isSpreadElement)) {
            return this.lowerArrayLiteralWithSpread(node, arrayType, elementType);
        }

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
            if (ts.isOmittedExpression(element)) return;

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

    private lowerRegularExpressionLiteral(node: ts.RegularExpressionLiteral): LocalDto {
        const text = node.text;
        const closingSlash = text.lastIndexOf("/");
        if (!text.startsWith("/") || closingSlash <= 0) {
            throw new LoweringError("invalid regular-expression literal");
        }

        const pattern = text.slice(1, closingSlash);
        const flags = text.slice(closingSlash + 1);
        // A literal uses the intrinsic RegExp constructor, even when a source binding shadows it.
        const classType: ClassTypeDto = {
            _: "ClassType", signature: { name: "RegExp", declaringFile: UNKNOWN_FILE_SIGNATURE },
        };
        const value = this.m.newTemp(classType);
        this.m.cfg.emit({ _: "AssignStmt", left: value, right: { _: "NewExpr", classType } });
        this.m.cfg.emit({
            _: "AssignStmt",
            left: value,
            right: {
                _: "InstanceCallExpr",
                instance: value,
                method: {
                    declaringClass: classType._ === "ClassType" ? classType.signature : UNKNOWN_CLASS_SIGNATURE,
                    name: CONSTRUCTOR_NAME,
                    parameters: [
                        { name: "pattern", type: STRING_TYPE },
                        { name: "flags", type: STRING_TYPE },
                    ],
                    returnType: classType,
                },
                args: [constant(pattern, STRING_TYPE), constant(flags, STRING_TYPE)],
            },
        });
        return value;
    }

    private lowerTaggedTemplate(node: ts.TaggedTemplateExpression): ValueDto {
        const tag = unwrapTransparentExpression(node.tag);
        const substitutions = ts.isTemplateExpression(node.template)
            ? node.template.templateSpans.map((span) => span.expression)
            : [];
        const unsupportedBeforeTag = this.unsupportedExpressionCount;
        let ptr: LocalDto;
        let receiver: LocalDto | undefined;
        let declaringClass = UNKNOWN_CLASS_SIGNATURE;
        let name = "%tag";

        if (ts.isPropertyAccessExpression(tag) || ts.isElementAccessExpression(tag)) {
            if (ts.isPropertyAccessExpression(tag)
                && this.importedScopeFunctionField(tag) !== undefined) {
                throw new LoweringError("module namespace call receiver is not represented in EtsIR");
            }

            // Property Get and key coercion can themselves mutate the source binding.
            receiver = this.snapshotToLocal(tag.expression);
            const key = ts.isPropertyAccessExpression(tag)
                ? constant(tag.name.text, STRING_TYPE)
                : this.propertyAccessKey(this.lowerToImmediate(tag.argumentExpression), receiver);
            ptr = this.materialize({
                _: "PropertyRef",
                instance: receiver,
                key,
                type: this.safeTypeOf(tag),
            }, this.safeTypeOf(tag));
            name = ts.isPropertyAccessExpression(tag) ? tag.name.text : "%tag";
        } else {
            ptr = this.snapshotToLocal(node.tag, substitutions);
            if (ts.isIdentifier(tag)) {
                name = tag.text;
                declaringClass = this.m.moduleFieldForIdentifier(tag)?.field.declaringClass ?? UNKNOWN_CLASS_SIGNATURE;
            }
        }

        if (this.unsupportedExpressionCount !== unsupportedBeforeTag) {
            throw new LoweringError("template tag cannot be represented in EtsIR");
        }

        const literals = ts.isTemplateExpression(node.template)
            ? [node.template.head, ...node.template.templateSpans.map((span) => span.literal)]
            : [node.template];
        const raw = literals.map(templateRawText);
        const cooked = literals.map((literal, index) => {
            // The public compiler API reports invalid escapes in an untagged token.
            // Such escapes are legal in tagged templates, whose cooked entry is undefined.
            const probe = ts.transpileModule(`const value = \`${raw[index]}\`;`, { reportDiagnostics: true });
            return probe.diagnostics?.some((diagnostic) => diagnostic.category === ts.DiagnosticCategory.Error)
                ? null
                : literal.text;
        });
        const templateType: TypeDto = {
            _: "ArrayType",
            elementType: cooked.includes(null)
                ? { _: "UnionType", types: [STRING_TYPE, UNDEFINED_TYPE] }
                : STRING_TYPE,
            dimensions: 1,
        };
        const file = this.m.ctx.fileSignatureFor(node.getSourceFile());
        const parts = this.materialize({
            _: "TemplateObjectExpr",
            siteId: JSON.stringify([file.projectName, file.fileName, node.template.getStart()]),
            cooked,
            raw,
            type: templateType,
        }, templateType);
        const args = [parts, ...this.lowerArguments(substitutions)];
        const method = this.methodSignatureForCall(node, name, declaringClass);

        return receiver === undefined
            ? { _: "PtrCallExpr", ptr, method, args }
            : { _: "PtrCallExpr", ptr, receiver, method, args };
    }

    private lowerArrayLiteralWithSpread(
        node: ts.ArrayLiteralExpression,
        arrayType: TypeDto,
        elementType: TypeDto,
    ): LocalDto {
        const result = this.m.newTemp(
            arrayType._ === "ArrayType" ? arrayType : { _: "ArrayType", elementType, dimensions: 1 },
        );
        this.m.cfg.emit({
            _: "AssignStmt", left: result,
            right: { _: "NewArrayExpr", elementType, size: constant("0", NUMBER_TYPE) },
        });
        const offset = this.m.newTemp(NUMBER_TYPE);
        this.m.cfg.emit({ _: "AssignStmt", left: offset, right: constant("0", NUMBER_TYPE) });

        for (const element of node.elements) {
            if (ts.isSpreadElement(element)) {
                const source = this.m.snapshotToLocal(this.lowerToLocal(element.expression), this.safeTypeOf(element.expression));
                new IteratorLowerer(this.m, source).appendTo(result, offset, elementType);
                continue;
            }
            if (!ts.isOmittedExpression(element)) {
                const value = this.lowerToImmediate(element);
                this.m.cfg.emit({
                    _: "AssignStmt", left: { _: "ArrayRef", array: result, index: offset, type: elementType }, right: value,
                });
            }
            this.m.cfg.emit({
                _: "AssignStmt", left: offset,
                right: { _: "BinopExpr", op: "+", left: offset, right: constant("1", NUMBER_TYPE), type: NUMBER_TYPE },
            });
            // Advancing a hole still grows the fresh array's length.
            this.m.cfg.emit({
                _: "AssignStmt",
                left: { _: "InstanceFieldRef", instance: result, field: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "length", type: NUMBER_TYPE } },
                right: offset,
            });
        }
        return result;
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

    private lowerClassExpression(node: ts.ClassExpression): ValueDto {
        if (node.heritageClauses?.length !== undefined && node.heritageClauses.length > 0) {
            throw new LoweringError("class expression heritage is not represented in EtsIR");
        }

        if (node.name !== undefined || node.members.some((member) =>
            ts.isClassStaticBlockDeclaration(member)
            || (ts.getCombinedModifierFlags(member) & ts.ModifierFlags.Static) !== 0
            || (member.name !== undefined && ts.isComputedPropertyName(member.name)))) {
            throw new LoweringError("named, static, or computed class expression members are not represented in EtsIR");
        }

        let capturesLexicalValue = false;
        const inspectCapture = (current: ts.Node): void => {
            if (ts.isIdentifier(current)) {
                const declarations = this.m.converter.symbolOf(current)?.declarations ?? [];
                for (const declaration of declarations) {
                    if (!ts.isVariableDeclaration(declaration) && !ts.isParameter(declaration)
                        && !ts.isBindingElement(declaration) && !ts.isFunctionDeclaration(declaration)
                        && !ts.isClassDeclaration(declaration)) continue;
                    let ancestor: ts.Node | undefined = declaration;
                    let insideClass = false;
                    let methodLocal = false;
                    while (ancestor !== undefined) {
                        if (ancestor === node) insideClass = true;
                        if (ts.isFunctionLike(ancestor)) methodLocal = true;
                        ancestor = ancestor.parent;
                    }
                    if (!insideClass && methodLocal) capturesLexicalValue = true;
                }
            }
            ts.forEachChild(current, inspectCapture);
        };
        inspectCapture(node);
        if (capturesLexicalValue) {
            throw new LoweringError("class expression lexical environment is not represented in EtsIR");
        }

        const buildClass = this.m.ctx.buildClassExpression;
        if (buildClass === undefined) {
            throw new LoweringError("class expression in a context without a class builder");
        }

        const registry = this.m.ctx.anonymous;
        const signature = this.m.converter.classSignatureOf(node);
        if (!registry.classes.some((clazz) => clazz.signature.name === signature.name)) {
            registry.classes.push(buildClass(node, signature.name));
        }
        return { _: "NewClassExpr", signature };
    }

    /**
     * Arrow function / function expression -> anonymous `%AM<n>$<method>` method
     * on the enclosing class; the use-site value is a Local of that name typed
     * FunctionType. Captured locals are carried by an implicit LexicalEnvType
     * parameter and loaded through ClosureFieldRef in the lifted method.
     */
    lowerFunctionDeclaration(node: ts.FunctionDeclaration): LocalDto {
        return this.lowerClosure(node);
    }

    private lowerClosure(node: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration | ts.GetAccessorDeclaration): LocalDto {
        if (this.lowerFunctionBody === undefined) {
            throw new LoweringError("closure in a context without a body lowerer");
        }
        if (node.body === undefined) {
            throw new LoweringError("function declaration without a body");
        }
        const registry = this.m.ctx.anonymous;
        const name = `${ANONYMOUS_METHOD_PREFIX}${registry.nextMethodId++}$${this.m.methodName}`;
        const declaringClass = this.m.declaringClass;

        const { parameters, prologueParams } = buildParameters(this.m.ctx, node);
        const returnType = returnTypeOf(this.m.ctx, node);
        const baseSignature: MethodSignatureDto = { declaringClass, name, parameters, returnType };
        const capturedIdentifiers = collectCapturedIdentifiers(node, this.m.checker);
        if (capturedIdentifiers.some((identifier) => this.m.isUninitializedParameter(identifier))) {
            throw new LoweringError("closure capturing an uninitialized parameter requires a runtime temporal-dead-zone cell");
        }
        const captures = capturedIdentifiers
            .filter((identifier) => this.m.moduleFieldForIdentifier(identifier) === undefined)
            .map((identifier) => this.m.captureForIdentifier(identifier, this.safeTypeOf(identifier)));

        const isArrow = ts.isArrowFunction(node);
        const lexicalThis = isArrow && usesLexicalThis(node)
            ? this.m.getOrCreateLocal("this", this.m.thisType())
            : undefined;

        let signature = baseSignature;
        let environment:
            | { name: string; type: Extract<TypeDto, { _: "LexicalEnvType" }> }
            | undefined;
        if (captures.length > 0 || lexicalThis !== undefined) {
            const capturedLocals = captures.map((capture) => ({
                name: capture.outerLocal.name,
                type: capture.outerLocal.type,
            }));
            if (lexicalThis !== undefined) {
                capturedLocals.push({ name: lexicalThis.name, type: lexicalThis.type });
            }

            const environmentType: Extract<TypeDto, { _: "LexicalEnvType" }> = {
                _: "LexicalEnvType",
                method: baseSignature,
                closures: [...new Map(capturedLocals.map((local) => [local.name, local])).values()],
            };
            const environmentLocal = this.m.newClosureEnvironment(environmentType);
            environment = { name: environmentLocal.name, type: environmentType };
            signature = {
                ...baseSignature,
                parameters: [{ name: environment.name, type: environment.type }, ...parameters],
            };
        }

        // Arrow functions retain lexical `this`, so a lifted arrow must inherit
        // `isStaticMethod` — otherwise `this.f` in a static context would produce an
        // InstanceFieldRef while the enclosing method produces a StaticFieldRef.
        // Ordinary function expressions have their own dynamic `this` and must not.
        const closureContext = new MethodContext(
            this.m.ctx,
            declaringClass,
            name,
            ts.isArrowFunction(node) && this.m.isStaticMethod,
        );
        this.lowerFunctionBody(closureContext, node.body, (afterParameter) => {
            if (environment === undefined) {
                closureContext.emitPrologue(prologueParams, afterParameter);
            } else {
                closureContext.emitClosurePrologue(
                    environment.name, environment.type, captures, prologueParams, afterParameter, lexicalThis,
                );
            }
        });
        registry.methods.push({
            signature,
            modifiers: modifiersOf(node),
            decorators: [],
            body: closureContext.build(),
        });

        return this.m.getOrCreateLocal(name, { _: "FunctionType", signature, ...(isArrow ? { isArrow } : {}) });
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

        const fields: FieldDto[] = [];
        const methods: MethodDto[] = [];
        const temp = this.m.newTemp(classType);
        this.m.cfg.emit({ _: "AssignStmt", left: temp, right: { _: "NewExpr", classType } });

        for (const property of node.properties) {
            if (ts.isPropertyAssignment(property)) {
                const propType = this.safeTypeOf(property.initializer);
                if (ts.isComputedPropertyName(property.name)) {
                    const key = this.lowerPropertyKey(property.name.expression);
                    const value = this.lowerToImmediate(property.initializer);
                    this.m.cfg.emit({
                        _: "DefineDataPropertyStmt",
                        target: temp,
                        key,
                        value,
                    });
                } else {
                    const propName = memberName(property.name);
                    if (propName === "__proto__") {
                        throw new LoweringError("object literal prototype setter is not represented in EtsIR");
                    }
                    fields.push(objectField(signature, propName, propType));
                    const value = this.lowerToImmediate(property.initializer);
                    this.emitObjectPropertyStore(temp, propName, value);
                }
            } else if (ts.isShorthandPropertyAssignment(property)) {
                const propName = property.name.text;
                const value = this.lowerToImmediate(property.name);
                fields.push(objectField(signature, propName, value.type));
                this.emitObjectPropertyStore(temp, propName, value);
            } else if (ts.isSpreadAssignment(property)) {
                const source = this.lowerToImmediate(property.expression);
                this.m.cfg.emit({
                    _: "CopyDataPropertiesStmt",
                    target: temp,
                    source,
                    excludedKeys: [],
                    throwOnNullishSource: false,
                });
            } else if (ts.isGetAccessorDeclaration(property)) {
                const key = ts.isComputedPropertyName(property.name)
                    ? this.lowerPropertyKey(property.name.expression)
                    : constant(memberName(property.name), STRING_TYPE);
                const getter = this.lowerClosure(property);
                this.m.cfg.emit({ _: "DefineAccessorStmt", target: temp, key, getter });
            } else if (ts.isMethodDeclaration(property)) {
                if (ts.isComputedPropertyName(property.name)) {
                    throw new LoweringError("computed object method name is not represented in EtsIR");
                }
                const methodName = memberName(property.name);
                const { parameters, prologueParams } = buildParameters(this.m.ctx, property);
                const methodSignature: MethodSignatureDto = {
                    declaringClass: signature,
                    name: methodName,
                    parameters,
                    returnType: returnTypeOf(this.m.ctx, property),
                };
                const methodContext = new MethodContext(this.m.ctx, signature, methodName);
                if (property.body !== undefined) {
                    this.lowerFunctionBody(methodContext, property.body, (afterParameter) =>
                        methodContext.emitPrologue(prologueParams, afterParameter),
                    );
                }
                methods.push({
                    signature: methodSignature,
                    modifiers: modifiersOf(property),
                    decorators: [],
                    body: methodContext.build(),
                });
            } else {
                // Setters remain separately unsupported.
                throw new LoweringError(`object literal member: ${syntaxKindName(property.kind)}`);
            }
        }

        registry.classes.push({
            signature,
            modifiers: 0,
            decorators: [],
            category: ClassCategory.OBJECT,
            superClassName: "",
            implementedInterfaceNames: [],
            fields,
            methods,
        });

        return temp;
    }

    /** Allocate the dynamic object container used by object-rest bindings. */
    newEmptyObject(): LocalDto {
        const registry = this.m.ctx.anonymous;
        const signature: ClassSignatureDto = {
            name: `${ANONYMOUS_CLASS_PREFIX}${registry.nextClassId++}$${this.m.methodName}`,
            declaringFile: registry.defaultClassSignature.declaringFile,
        };
        const classType: ClassTypeDto = { _: "ClassType", signature };
        registry.classes.push({
            signature,
            modifiers: 0,
            decorators: [],
            category: ClassCategory.OBJECT,
            superClassName: "",
            implementedInterfaceNames: [],
            fields: [],
            methods: [],
        });

        const temp = this.m.newTemp(classType);
        this.m.cfg.emit({ _: "AssignStmt", left: temp, right: { _: "NewExpr", classType } });
        return temp;
    }

    private emitObjectPropertyStore(
        instance: LocalDto,
        name: string,
        value: ImmediateDto,
    ): void {
        this.m.cfg.emit({
            _: "DefineDataPropertyStmt",
            target: instance,
            key: constant(name, STRING_TYPE),
            value,
        });
    }

    // ------------------------------------------------------------------
    // Optional chaining
    // ------------------------------------------------------------------

    /** Null-check diamond for an evaluated chain value; loose `!= null` also covers undefined. */
    private optionalEvaluatedDiamond(
        tested: LocalDto,
        resultType: TypeDto,
        access: () => ValueDto,
    ): LocalDto {
        const cfg = this.m.cfg;
        const result = this.m.newTemp(resultType);
        const accessLabel = cfg.newLabel();
        const elseLabel = cfg.newLabel();
        const joinLabel = cfg.newLabel();

        cfg.branch(this.relation("!=", tested, constant("null", NULL_TYPE)), accessLabel, elseLabel);
        cfg.placeLabel(accessLabel);
        cfg.emit({ _: "AssignStmt", left: result, right: access() });
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

    private importedScopeFunctionField(node: ts.PropertyAccessExpression): StaticFieldRefDto | undefined {
        const receiver = unwrapTransparentExpression(node.expression);
        if (!ts.isIdentifier(node.name) || !ts.isIdentifier(receiver)
            || !this.m.converter.symbolOf(receiver)?.declarations?.some(ts.isSourceFile)) return undefined;
        const field = this.m.moduleFieldForIdentifier(node.name);
        return field?.field.type._ === "FunctionType" ? field : undefined;
    }

    /**
     * If the expression statically refers to a class-like declaration
     * (class / enum — e.g. `Math`, `E` in `E.A`, `Foo` in `Foo.bar()`),
     * return its class signature; project classes get real signatures,
     * ambient ones get the %unk file.
     */
    private classLikeSignatureOf(node: ts.Expression): ClassSignatureDto | undefined {
        const value = unwrapTransparentExpression(node);
        if (this.isProjectClassProperty(value)) {
            return undefined;
        }

        const decl = classLikeDeclarationOf(value, this.m.checker);
        if (decl === undefined) return undefined;
        if (isProjectFile(decl)) {
            return this.m.converter.classSignatureOf(decl);
        }
        const name = decl.name !== undefined && ts.isIdentifier(decl.name) ? decl.name.text : "";
        return { name, declaringFile: UNKNOWN_FILE_SIGNATURE };
    }

    private isProjectClassProperty(node: ts.Expression): boolean {
        const value = unwrapTransparentExpression(node);
        return ts.isPropertyAccessExpression(value) &&
            this.m.converter.symbolOf(value.name)?.declarations?.some(
                (declaration) => ts.isClassDeclaration(declaration) && isProjectFile(declaration),
            ) === true;
    }

    private evaluateProjectClassPropertyReceiver(node: ts.Expression): void {
        const property = unwrapTransparentExpression(node);
        if (ts.isPropertyAccessExpression(property)) {
            // Preserve receiver effects without materializing the mutable class property.
            this.lowerToImmediate(property.expression);
        }
    }

    private classSignatureFromType(type: TypeDto): ClassSignatureDto {
        if (type._ === "ClassType" || type._ === "ClassValueType") {
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

    private resolveCalleeDeclaration(node: ts.CallExpression | ts.TaggedTemplateExpression): ts.Declaration | undefined {
        try {
            const signature = this.m.checker.getResolvedSignature(node);
            return signature?.getDeclaration();
        } catch {
            return undefined;
        }
    }

    /** Method signature for a call site, resolved through the checker when possible. */
    private methodSignatureForCall(
        node: ts.CallExpression | ts.TaggedTemplateExpression,
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
            .map((p, index) => {
                const param: MethodParameterDto = {
                    name: ts.isIdentifier(p.name) ? p.name.text : `${PATTERN_PARAMETER_PREFIX}${index}`,
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

    private checkTypeOf(checkValue: ImmediateDto): TypeDto | null {
        if (checkValue._ === "ClassValueRef") {
            return { _: "ClassType", signature: checkValue.signature };
        }
        return null;
    }

    private spreadFallback(node: ts.SpreadElement): ValueDto {
        this.unsupportedExpressionCount++;
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

function templateRawText(literal: ts.TemplateLiteralLikeNode): string {
    const text = literal.getText();
    const suffixLength = literal.kind === ts.SyntaxKind.TemplateHead || literal.kind === ts.SyntaxKind.TemplateMiddle
        ? 2
        : 1;
    return text.slice(1, -suffixLength).replace(/\r\n?/g, "\n");
}

/** Preserve the source spelling only when TypeScript normalizes a non-finite literal. */
function numericConstantText(node: ts.NumericLiteral): string {
    return Number.isFinite(Number(node.text)) ? node.text : node.getText();
}

/** Nested arrows share the receiver; ordinary functions and class members introduce their own. */
function usesLexicalThis(arrow: ts.ArrowFunction): boolean {
    let found = false;
    const visit = (node: ts.Node): void => {
        if (found) return;
        if (node.kind === ts.SyntaxKind.ThisKeyword) {
            found = true;
            return;
        }
        if (!ts.isArrowFunction(node) && (ts.isFunctionLike(node) || ts.isClassLike(node))) return;
        ts.forEachChild(node, visit);
    };

    for (const parameter of arrow.parameters) visit(parameter);
    visit(arrow.body);
    return found;
}

function bigIntConstantText(node: ts.BigIntLiteral): string {
    return BigInt(node.text.replace(/_/g, "").replace(/n$/, "")).toString();
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
        case "ClosureFieldRef":
        case "ArrayRef":
        case "PropertyRef":
            return target.type;
        case "InstanceFieldRef":
        case "StaticFieldRef":
            return target.field.type;
    }
}

/** Type hint for the converted primitive; opaque objects can produce either numeric kind. */
function toNumericType(type: TypeDto): TypeDto {
    switch (type._) {
        case "BigIntType":
            return BIGINT_TYPE;
        case "NumberType":
        case "StringType":
        case "BooleanType":
        case "NullType":
        case "UndefinedType":
        case "LiteralType":
            return NUMBER_TYPE;
        case "AliasType":
            return toNumericType(type.originalType);
        case "UnionType": {
            const numericTypes = type.types.map(toNumericType);
            if (numericTypes.every((numeric) => numeric._ === "NumberType")) return NUMBER_TYPE;
            if (numericTypes.every((numeric) => numeric._ === "BigIntType")) return BIGINT_TYPE;
            break;
        }
    }
    return { _: "UnionType", types: [NUMBER_TYPE, BIGINT_TYPE] };
}

export function arrayElementType(array: Extract<TypeDto, { _: "ArrayType" }>): TypeDto {
    if (array.dimensions <= 1) {
        return array.elementType;
    }
    return { _: "ArrayType", elementType: array.elementType, dimensions: array.dimensions - 1 };
}

function isProjectFile(decl: ts.Node): boolean {
    return !decl.getSourceFile().isDeclarationFile;
}

function optionalChain(node: OptionalChainSegment): OptionalChain | undefined {
    const reversed: OptionalChainSegment[] = [];
    let current = node;
    let earliestOptionalIndex: number | undefined;
    while (true) {
        reversed.push(current);
        if (current.questionDotToken !== undefined) {
            earliestOptionalIndex = reversed.length - 1;
        }
        let parent: ts.Expression = current.expression;
        while (ts.isNonNullExpression(parent) && ts.isNonNullChain(parent)) {
            parent = parent.expression;
        }
        if (!isOptionalChainSegment(parent)) {
            break;
        }
        current = parent;
    }
    if (earliestOptionalIndex === undefined) return undefined;
    return { segments: reversed.slice(0, earliestOptionalIndex + 1).reverse() };
}

function isOptionalChainSegment(node: ts.Node): node is OptionalChainSegment {
    return ts.isPropertyAccessChain(node) || ts.isElementAccessChain(node) || ts.isCallExpression(node);
}

/** Calls, getters and implicit coercions can mutate any captured binding. */
function containsPossibleSideEffect(node: ts.Node): boolean {
    let found = false;
    const visit = (current: ts.Node): void => {
        if (found) return;
        if (ts.isFunctionLike(current)) {
            // Method/accessor names run at creation; their bodies run only when invoked.
            found = current.name !== undefined && ts.isComputedPropertyName(current.name);
            return;
        }
        if (
            ts.isCallExpression(current)
            || ts.isNewExpression(current)
            || ts.isAwaitExpression(current)
            || ts.isYieldExpression(current)
            || ts.isTaggedTemplateExpression(current)
            || ts.isSpreadElement(current)
            || ts.isSpreadAssignment(current)
            || ts.isPropertyAccessExpression(current)
            || ts.isElementAccessExpression(current)
            || ts.isComputedPropertyName(current)
            || ts.isBinaryExpression(current)
            || ts.isPrefixUnaryExpression(current)
            || ts.isPostfixUnaryExpression(current)
            || ts.isTemplateExpression(current)
        ) {
            found = true;
            return;
        }
        ts.forEachChild(current, visit);
    };
    visit(node);
    return found;
}


/** Erase syntax that leaves an expression's runtime value unchanged. */
function unwrapTransparentExpression(node: ts.Expression): ts.Expression {
    while (
        ts.isParenthesizedExpression(node)
        || ts.isAsExpression(node)
        || ts.isTypeAssertionExpression(node)
        || ts.isNonNullExpression(node)
        || ts.isSatisfiesExpression(node)
    ) {
        node = node.expression;
    }
    return node;
}

/** Identifier binding written by an assignment or an increment/decrement, if any. */
function assignmentTarget(node: ts.Node): ts.Identifier | undefined {
    if (ts.isBinaryExpression(node) && (
        node.operatorToken.kind === ts.SyntaxKind.EqualsToken
        || COMPOUND_ASSIGN_BY_SYNTAX[node.operatorToken.kind] !== undefined
    )) {
        return bindingIdentifier(node.left);
    }
    if (
        (ts.isPrefixUnaryExpression(node) || ts.isPostfixUnaryExpression(node))
        && (node.operator === ts.SyntaxKind.PlusPlusToken || node.operator === ts.SyntaxKind.MinusMinusToken)
    ) {
        return bindingIdentifier(node.operand);
    }
    return undefined;
}

function sameBinding(
    candidateSymbol: ts.Symbol | undefined,
    candidate: ts.Identifier,
    sourceSymbol: ts.Symbol | undefined,
    source: ts.Identifier,
): boolean {
    return sourceSymbol !== undefined ? candidateSymbol === sourceSymbol : candidate.text === source.text;
}

/**
 * Collect source locals referenced from outside a lifted function. Traversing
 * nested functions as well propagates transitive captures through nested
 * lexical environments; declarations owned by the current function subtree
 * are excluded.
 */
function collectCapturedIdentifiers(
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration | ts.GetAccessorDeclaration,
    checker: ts.TypeChecker,
): ts.Identifier[] {
    if (closure.body === undefined) return [];
    const captures = new Map<ts.Symbol, ts.Identifier>();
    // Declarations owned by the closure subtree are collected on the way down, so
    // ownership is a cheap set lookup instead of an O(depth) ancestor walk, and every
    // node (including nested closures) is visited exactly once.
    const ownDeclarations = new Set<ts.Declaration>();
    // The closure's OWN declaration node counts as owned: a `function f` that calls `f`
    // recursively must not capture itself (it is bound by the enclosing statement that
    // creates it, so capturing would materialize the slot before the assignment).
    ownDeclarations.add(closure as ts.Declaration);
    // The parameters of the closure itself live outside its body but are still its own.
    const collectOwn = (node: ts.Node): void => {
        if (isCapturableDeclaration(node as ts.Declaration)) {
            ownDeclarations.add(node as ts.Declaration);
        }
        ts.forEachChild(node, collectOwn);
    };
    for (const parameter of closure.parameters) {
        collectOwn(parameter);
    }
    const seen = new Set<ts.Symbol>();
    const visit = (node: ts.Node): void => {
        if (isCapturableDeclaration(node as ts.Declaration)) {
            ownDeclarations.add(node as ts.Declaration);
        }
        if (ts.isIdentifier(node) && !(ts.isPropertyAccessExpression(node.parent) && node.parent.name === node)) {
            let symbol: ts.Symbol | undefined;
            try {
                symbol = checker.getSymbolAtLocation(node);
                if (ts.isShorthandPropertyAssignment(node.parent) && node.parent.name === node) {
                    symbol = checker.getShorthandAssignmentValueSymbol(node.parent) ?? symbol;
                }
            } catch {
                // Ignore unresolved names; they remain ordinary ambient locals.
            }
            if (symbol !== undefined && !seen.has(symbol)) {
                seen.add(symbol);
                if (symbol.declarations?.some(isCapturableDeclaration)) {
                    captures.set(symbol, node);
                }
            }
        }
        ts.forEachChild(node, visit);
    };
    for (const parameter of closure.parameters) visit(parameter);
    visit(closure.body);
    // Declaration nodes may be visited after their first reference, so filter at the end.
    for (const symbol of [...captures.keys()]) {
        if (symbol.declarations!.some((decl) => ownDeclarations.has(decl))) {
            captures.delete(symbol);
        }
    }
    return [...captures.values()];
}

function isCapturableDeclaration(node: ts.Declaration): boolean {
    return ts.isVariableDeclaration(node)
        || ts.isParameter(node)
        || ts.isBindingElement(node)
        || ts.isFunctionDeclaration(node);
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
