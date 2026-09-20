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
import { MethodSignatureDto } from "../dto/signatures";
import { BuiltinCallProofDto, ProvenBuiltinDto } from "../dto/values";
import { LoweringContext, VerifiedBuiltinEntry } from "./methodBuilder";

interface SupportedBuiltin {
    readonly receiverName: "Number" | "Math";
    readonly memberName: "isInteger" | "abs" | "min" | "max";
    readonly arity: number;
    readonly builtin: ProvenBuiltinDto;
}

interface BuiltinCandidate {
    readonly call: ts.CallExpression & { expression: ts.PropertyAccessExpression };
    readonly builtin: ProvenBuiltinDto;
    readonly receiverSymbol: ts.Symbol;
    readonly memberSymbol: ts.Symbol;
}

const SUPPORTED_BUILTINS: readonly SupportedBuiltin[] = [
    { receiverName: "Number", memberName: "isInteger", arity: 1, builtin: "NUMBER_IS_INTEGER" },
    { receiverName: "Math", memberName: "abs", arity: 1, builtin: "MATH_ABS" },
    { receiverName: "Math", memberName: "min", arity: 2, builtin: "MATH_MIN" },
    { receiverName: "Math", memberName: "max", arity: 2, builtin: "MATH_MAX" },
];

/**
 * Prove a bounded set of numeric builtins in a closed entry context.
 * The result remains conditional on direct isolated execution of [entryMethod].
 */
export function verifiedBuiltinEntryFor(
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    entryMethod: MethodSignatureDto,
    captures: readonly ts.Identifier[],
): VerifiedBuiltinEntry | undefined {
    if (!isExportedTopLevelEntry(closure, ctx.checker)) return undefined;
    if (!hasAdmissibleModuleInitialization(closure.getSourceFile(), ctx.checker)) return undefined;
    if (containsThis(closure.body!)) return undefined;
    if (containsNestedFunction(closure)) return undefined;

    const candidates = collectBuiltinCandidates(closure, ctx);
    const candidateCalls = new Set(candidates.map(({ call }) => call));
    const admittedCandidates = candidates.filter(({ call }) =>
        hasEffectFreePrefix(closure, call, ctx, candidateCalls));
    if (admittedCandidates.length === 0) return undefined;

    const admittedByReceiver = new Map<ts.Symbol, Set<ts.CallExpression>>();
    for (const candidate of admittedCandidates) {
        const calls = admittedByReceiver.get(candidate.receiverSymbol) ?? new Set<ts.CallExpression>();
        calls.add(candidate.call);
        admittedByReceiver.set(candidate.receiverSymbol, calls);
    }

    const unsafeReceivers = new Set<ts.Symbol>();
    for (const [receiverSymbol, admittedCalls] of admittedByReceiver) {
        visit(closure.body!, (node) => {
            if (!ts.isIdentifier(node) || ctx.converter.symbolOf(node) !== receiverSymbol) return;
            if (!isAdmittedBuiltinReceiver(node, admittedCalls)) unsafeReceivers.add(receiverSymbol);
        });
    }

    const admittedByMember = new Map<ts.Symbol, Set<ts.CallExpression>>();
    for (const candidate of admittedCandidates) {
        const calls = admittedByMember.get(candidate.memberSymbol) ?? new Set<ts.CallExpression>();
        calls.add(candidate.call);
        admittedByMember.set(candidate.memberSymbol, calls);
    }

    const unsafeMembers = new Set<ts.Symbol>();
    for (const [memberSymbol, admittedCalls] of admittedByMember) {
        visit(closure.body!, (node) => {
            if (!ts.isIdentifier(node) || ctx.converter.symbolOf(node) !== memberSymbol) return;
            if (!isAdmittedBuiltinMember(node, admittedCalls)) unsafeMembers.add(memberSymbol);
        });
    }

    const provenCandidates = admittedCandidates.filter((candidate) =>
        !unsafeReceivers.has(candidate.receiverSymbol) && !unsafeMembers.has(candidate.memberSymbol));
    if (provenCandidates.length === 0) return undefined;

    const prunableCaptures = new Set<ts.Symbol>(provenCandidates.map(({ receiverSymbol }) => receiverSymbol));
    const errorSymbols = referencedDefaultLibraryErrorSymbols(closure.body!, ctx);
    for (const errorSymbol of errorSymbols) {
        if (!isPrunableErrorCapture(errorSymbol, closure.body!, ctx)) return undefined;
        prunableCaptures.add(errorSymbol);
    }

    for (const identifier of captures) {
        const symbol = ctx.converter.symbolOf(identifier);
        if (symbol === undefined || !prunableCaptures.has(symbol)) {
            return undefined;
        }
    }

    const calls = new Map<ts.CallExpression, BuiltinCallProofDto>();
    for (const candidate of provenCandidates) {
        calls.set(candidate.call, {
            builtin: candidate.builtin,
            entryRequirement: "DIRECT_ISOLATED_ENTRY",
            entryMethod,
        });
    }

    return { calls, prunableCaptures };
}

function isExportedTopLevelEntry(
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    checker: ts.TypeChecker,
): boolean {
    let statement: ts.FunctionDeclaration | ts.VariableStatement;
    if (ts.isFunctionDeclaration(closure)) {
        if (closure.body === undefined || closure.name === undefined || !ts.isSourceFile(closure.parent)) return false;
        statement = closure;
    } else {
        const declaration = closure.parent;
        if (!ts.isVariableDeclaration(declaration) || declaration.initializer !== closure) return false;
        if (!ts.isIdentifier(declaration.name)) return false;

        const declarationList = declaration.parent;
        if (!ts.isVariableDeclarationList(declarationList) || declarationList.declarations.length !== 1) return false;
        if ((declarationList.flags & ts.NodeFlags.Const) === 0) return false;
        if (!ts.isVariableStatement(declarationList.parent) || !ts.isSourceFile(declarationList.parent.parent)) return false;
        statement = declarationList.parent;
    }

    if (!statement.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword)) return false;
    if (!closure.parameters.every((parameter) => isAdmissibleEntryParameter(parameter, checker))) return false;

    const signature = checker.getSignatureFromDeclaration(closure);
    return signature !== undefined && isAdmissibleEntryType(checker.getReturnTypeOfSignature(signature), checker);
}

function isAdmissibleEntryParameter(parameter: ts.ParameterDeclaration, checker: ts.TypeChecker): boolean {
    if (!ts.isIdentifier(parameter.name) || parameter.dotDotDotToken !== undefined) return false;
    if (!isAdmissibleEntryType(checker.getTypeAtLocation(parameter.name), checker)) return false;
    if (parameter.initializer === undefined) return true;

    return isNumberLikeType(checker.getTypeAtLocation(parameter.initializer))
        && isPureScalarExpression(
            parameter.initializer,
            checker,
            new Set(),
            ScalarIdentifierPolicy.REJECT,
        );
}

function isAdmissibleEntryType(type: ts.Type, checker: ts.TypeChecker): boolean {
    if (isPrimitiveScalarType(type) || checker.isArrayType(type) || checker.isTupleType(type)) return true;

    const symbol = type.aliasSymbol ?? type.getSymbol();
    return symbol?.getName() === "ReadonlyArray";
}

function hasAdmissibleModuleInitialization(sourceFile: ts.SourceFile, checker: ts.TypeChecker): boolean {
    return sourceFile.statements.every((statement) => {
        if (ts.isEmptyStatement(statement) || ts.isInterfaceDeclaration(statement) || ts.isTypeAliasDeclaration(statement)) {
            return true;
        }
        if (ts.isFunctionDeclaration(statement)) {
            if (statement.body === undefined) return true;

            return statement.modifiers?.every((modifier) =>
                modifier.kind === ts.SyntaxKind.ExportKeyword
                || modifier.kind === ts.SyntaxKind.DefaultKeyword
                || modifier.kind === ts.SyntaxKind.AsyncKeyword,
            ) !== false;
        }
        if (!ts.isVariableStatement(statement)) return false;
        if ((statement.declarationList.flags & ts.NodeFlags.Const) === 0) return false;

        return statement.declarationList.declarations.every((declaration) =>
            ts.isIdentifier(declaration.name)
            && declaration.initializer !== undefined
            && (
                ts.isArrowFunction(declaration.initializer)
                || ts.isFunctionExpression(declaration.initializer)
                || isPureScalarExpression(
                    declaration.initializer,
                    checker,
                    new Set(),
                    ScalarIdentifierPolicy.REJECT,
                )
            ),
        );
    });
}

function collectBuiltinCandidates(
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
): BuiltinCandidate[] {
    const calls: BuiltinCandidate[] = [];
    const visitBody = (node: ts.Node): void => {
        if (node !== closure.body && ts.isFunctionLike(node)) return;

        const candidate = builtinCandidateFor(node, ctx);
        if (candidate !== undefined) calls.push(candidate);
        ts.forEachChild(node, visitBody);
    };
    visitBody(closure.body!);

    return calls;
}

function builtinCandidateFor(node: ts.Node, ctx: LoweringContext): BuiltinCandidate | undefined {
    if (!ts.isCallExpression(node) || node.questionDotToken !== undefined) return undefined;

    const callee = node.expression;
    if (!ts.isPropertyAccessExpression(callee) || callee.questionDotToken !== undefined) return undefined;
    const receiver = callee.expression;
    if (!ts.isIdentifier(receiver)) return undefined;

    const definition = SUPPORTED_BUILTINS.find((candidate) =>
        candidate.receiverName === receiver.text
        && candidate.memberName === callee.name.text
        && candidate.arity === node.arguments.length);
    if (definition === undefined) return undefined;
    if (node.arguments.some(ts.isSpreadElement)) return undefined;
    if (!node.arguments.every((argument) => isNumberLikeType(ctx.checker.getTypeAtLocation(argument)))) return undefined;

    const receiverSymbol = ctx.converter.symbolOf(receiver);
    const memberSymbol = ctx.converter.symbolOf(callee.name);
    if (!isDefaultLibrarySymbol(receiverSymbol, ctx) || !isDefaultLibrarySymbol(memberSymbol, ctx)) return undefined;

    return {
        call: node as ts.CallExpression & { expression: ts.PropertyAccessExpression },
        builtin: definition.builtin,
        receiverSymbol,
        memberSymbol,
    };
}

function isDefaultLibrarySymbol(symbol: ts.Symbol | undefined, ctx: LoweringContext): symbol is ts.Symbol {
    const declarations = symbol?.declarations;
    return declarations !== undefined
        && declarations.length > 0
        && declarations.every((declaration) => ctx.isDefaultLibrarySourceFile(declaration.getSourceFile()));
}

function hasEffectFreePrefix(
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    call: ts.CallExpression,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
): boolean {
    if (!call.arguments.every((argument) =>
        !ts.isSpreadElement(argument)
        && isPureEntryScalarExpression(argument, closure, ctx, safeCalls))) {
        return false;
    }
    if (!ts.isBlock(closure.body!)) {
        return hasPurePrefixWithinExpression(closure.body!, call, closure, ctx, safeCalls);
    }

    return hasSafePrefixInStatements(closure.body.statements, call, closure, ctx, safeCalls);
}

function hasSafePrefixInStatements(
    statements: readonly ts.Statement[],
    target: ts.CallExpression,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
): boolean {
    for (const statement of statements) {
        if (containsNode(statement, target)) {
            return hasSafePrefixWithinStatement(statement, target, closure, ctx, safeCalls);
        }
        if (!isSafeCompleteStatement(statement, closure, ctx, safeCalls)) return false;
    }

    return false;
}

function hasSafePrefixWithinStatement(
    statement: ts.Statement,
    target: ts.CallExpression,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
): boolean {
    if (ts.isBlock(statement)) {
        return hasSafePrefixInStatements(statement.statements, target, closure, ctx, safeCalls);
    }
    if (ts.isExpressionStatement(statement)) {
        return isLocalScalarAssignmentShape(statement.expression, closure, ctx)
            && hasPurePrefixWithinExpression(statement.expression, target, closure, ctx, safeCalls);
    }
    if (ts.isVariableStatement(statement)) {
        for (const declaration of statement.declarationList.declarations) {
            if (declaration.initializer !== undefined && containsNode(declaration.initializer, target)) {
                return isSafeScalarDeclaration(declaration, closure, ctx, safeCalls, false)
                    && hasPurePrefixWithinExpression(declaration.initializer, target, closure, ctx, safeCalls);
            }
            if (!isSafeScalarDeclaration(declaration, closure, ctx, safeCalls, true)) return false;
        }

        return false;
    }
    if (ts.isIfStatement(statement)) {
        if (containsNode(statement.expression, target)) {
            return hasPurePrefixWithinExpression(statement.expression, target, closure, ctx, safeCalls);
        }
        if (!isPureEntryScalarExpression(statement.expression, closure, ctx, safeCalls)) return false;

        if (containsNode(statement.thenStatement, target)) {
            return (statement.elseStatement === undefined
                    || isSafeCompleteStatement(statement.elseStatement, closure, ctx, safeCalls))
                && hasSafePrefixWithinStatement(statement.thenStatement, target, closure, ctx, safeCalls);
        }
        if (statement.elseStatement !== undefined && containsNode(statement.elseStatement, target)) {
            return isSafeCompleteStatement(statement.thenStatement, closure, ctx, safeCalls)
                && hasSafePrefixWithinStatement(statement.elseStatement, target, closure, ctx, safeCalls);
        }

        return false;
    }
    if (ts.isWhileStatement(statement)) {
        return containsNode(statement.statement, target)
            && isPureEntryScalarExpression(statement.expression, closure, ctx, safeCalls)
            && isSafeCompleteStatement(statement.statement, closure, ctx, safeCalls);
    }
    if (ts.isReturnStatement(statement) && statement.expression !== undefined) {
        return hasPurePrefixWithinExpression(statement.expression, target, closure, ctx, safeCalls);
    }
    if (ts.isThrowStatement(statement)) {
        return hasPurePrefixWithinExpression(statement.expression, target, closure, ctx, safeCalls);
    }

    return false;
}

function isSafeCompleteStatement(
    statement: ts.Statement,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
): boolean {
    if (ts.isEmptyStatement(statement)) return true;
    if (ts.isBlock(statement)) {
        return statement.statements.every((nested) => isSafeCompleteStatement(nested, closure, ctx, safeCalls));
    }
    if (ts.isVariableStatement(statement)) {
        return statement.declarationList.declarations.every((declaration) =>
            isSafeScalarDeclaration(declaration, closure, ctx, safeCalls, true));
    }
    if (ts.isExpressionStatement(statement)) {
        return isSafeLocalScalarAssignment(statement.expression, closure, ctx, safeCalls);
    }
    if (ts.isIfStatement(statement)) {
        return isPureEntryScalarExpression(statement.expression, closure, ctx, safeCalls)
            && isSafeCompleteStatement(statement.thenStatement, closure, ctx, safeCalls)
            && (statement.elseStatement === undefined
                || isSafeCompleteStatement(statement.elseStatement, closure, ctx, safeCalls));
    }
    if (ts.isWhileStatement(statement)) {
        return isPureEntryScalarExpression(statement.expression, closure, ctx, safeCalls)
            && isSafeCompleteStatement(statement.statement, closure, ctx, safeCalls);
    }
    if (ts.isReturnStatement(statement)) {
        return statement.expression === undefined
            || isPureEntryScalarExpression(statement.expression, closure, ctx, safeCalls);
    }
    if (ts.isThrowStatement(statement)) {
        return isSafeThrowExpression(statement.expression, closure, ctx, safeCalls);
    }

    return false;
}

function isSafeScalarDeclaration(
    declaration: ts.VariableDeclaration,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
    checkInitializer: boolean,
): boolean {
    if (!ts.isIdentifier(declaration.name)) return false;
    if (!isPrimitiveScalarType(ctx.checker.getTypeAtLocation(declaration.name))) return false;
    if (!checkInitializer || declaration.initializer === undefined) return true;

    return isPureEntryScalarExpression(declaration.initializer, closure, ctx, safeCalls);
}

function isSafeLocalScalarAssignment(
    expression: ts.Expression,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
): boolean {
    return isLocalScalarAssignmentShape(expression, closure, ctx)
        && ts.isBinaryExpression(expression)
        && isPureEntryScalarExpression(expression.right, closure, ctx, safeCalls);
}

function isLocalScalarAssignmentShape(
    expression: ts.Expression,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
): boolean {
    if (!ts.isBinaryExpression(expression) || expression.operatorToken.kind !== ts.SyntaxKind.EqualsToken) return false;
    if (!ts.isIdentifier(expression.left)) return false;
    if (!isPrimitiveScalarType(ctx.checker.getTypeAtLocation(expression.left))) return false;

    const symbol = ctx.converter.symbolOf(expression.left);
    return symbol !== undefined && symbol.declarations?.some((declaration) =>
        declarationBelongsToEntry(declaration, closure)) === true;
}

function declarationBelongsToEntry(
    declaration: ts.Declaration,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
): boolean {
    for (let current: ts.Node | undefined = declaration; current !== undefined; current = current.parent) {
        if (ts.isFunctionLike(current)) return current === closure;
    }

    return false;
}

function isSafeThrowExpression(
    expression: ts.Expression,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
): boolean {
    if (isPureEntryScalarExpression(expression, closure, ctx, safeCalls)) return true;
    if (!ts.isNewExpression(expression) || !ts.isIdentifier(expression.expression)) return false;
    if (expression.expression.text !== "Error") return false;
    if (!isDefaultLibrarySymbol(ctx.converter.symbolOf(expression.expression), ctx)) return false;

    return (expression.arguments ?? []).every((argument) =>
        !ts.isSpreadElement(argument)
        && isPureEntryScalarExpression(argument, closure, ctx, safeCalls));
}

function hasPurePrefixWithinExpression(
    root: ts.Expression,
    target: ts.CallExpression,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
): boolean {
    if (root === target) return true;

    const children: ts.Node[] = [];
    ts.forEachChild(root, (child) => {
        children.push(child);
    });
    for (let index = 0; index < children.length; index++) {
        const child = children[index];
        if (!containsNode(child, target)) continue;

        const earlierExpressions = children.slice(0, index).filter(ts.isExpression);
        return earlierExpressions.every((expression) =>
            isPureEntryScalarExpression(expression, closure, ctx, safeCalls))
            && ts.isExpression(child)
            && hasPurePrefixWithinExpression(child, target, closure, ctx, safeCalls);
    }

    return false;
}

enum ScalarIdentifierPolicy {
    ALLOW,
    REJECT,
}

function isPureEntryScalarExpression(
    node: ts.Expression,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
    safeCalls: ReadonlySet<ts.CallExpression>,
): boolean {
    return isPureScalarExpression(
        node,
        ctx.checker,
        safeCalls,
        ScalarIdentifierPolicy.ALLOW,
        (identifier) => isSafeEntryScalarIdentifier(identifier, closure, ctx),
    );
}

function isSafeEntryScalarIdentifier(
    identifier: ts.Identifier,
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    ctx: LoweringContext,
): boolean {
    const symbol = ctx.converter.symbolOf(identifier);
    if (symbol === undefined) return false;
    if (symbol.declarations?.some((declaration) => declarationBelongsToEntry(declaration, closure)) === true) {
        return true;
    }
    if ((identifier.text === "Infinity" || identifier.text === "NaN")
        && isDefaultLibrarySymbol(symbol, ctx)) {
        return true;
    }

    return isSafeModuleScalarBinding(symbol, closure.getSourceFile(), ctx);
}

function isSafeModuleScalarBinding(
    symbol: ts.Symbol,
    sourceFile: ts.SourceFile,
    ctx: LoweringContext,
): boolean {
    const declarations = symbol.declarations;
    if (declarations?.length !== 1) return false;

    const declaration = declarations[0];
    if (!ts.isVariableDeclaration(declaration) || !ts.isIdentifier(declaration.name)) return false;
    if (declaration.getSourceFile() !== sourceFile || declaration.initializer === undefined) return false;
    if (!isPrimitiveScalarType(ctx.checker.getTypeAtLocation(declaration.name))) return false;

    const declarationList = declaration.parent;
    if (!ts.isVariableDeclarationList(declarationList)
        || (declarationList.flags & ts.NodeFlags.Const) === 0) {
        return false;
    }
    const statement = declarationList.parent;
    if (!ts.isVariableStatement(statement) || !ts.isSourceFile(statement.parent)) return false;

    return isPureScalarExpression(
        declaration.initializer,
        ctx.checker,
        new Set(),
        ScalarIdentifierPolicy.REJECT,
    );
}

function isPureScalarExpression(
    node: ts.Expression,
    checker: ts.TypeChecker,
    safeCalls: ReadonlySet<ts.CallExpression>,
    identifierPolicy: ScalarIdentifierPolicy = ScalarIdentifierPolicy.ALLOW,
    identifierGuard?: (identifier: ts.Identifier) => boolean,
): boolean {
    if (!isPrimitiveScalarType(checker.getTypeAtLocation(node))) return false;
    if (ts.isIdentifier(node)) {
        return identifierPolicy === ScalarIdentifierPolicy.ALLOW
            && (identifierGuard === undefined || identifierGuard(node));
    }
    if (ts.isNumericLiteral(node) || ts.isStringLiteral(node)
        || ts.isNoSubstitutionTemplateLiteral(node) || node.kind === ts.SyntaxKind.TrueKeyword
        || node.kind === ts.SyntaxKind.FalseKeyword || node.kind === ts.SyntaxKind.NullKeyword) return true;
    if (ts.isParenthesizedExpression(node) || ts.isAsExpression(node) || ts.isTypeAssertionExpression(node)
        || ts.isNonNullExpression(node) || ts.isSatisfiesExpression(node)) {
        return isPureScalarExpression(node.expression, checker, safeCalls, identifierPolicy, identifierGuard);
    }
    if (ts.isPrefixUnaryExpression(node)) {
        if (node.operator === ts.SyntaxKind.PlusPlusToken || node.operator === ts.SyntaxKind.MinusMinusToken) {
            return false;
        }

        return isPureScalarExpression(node.operand, checker, safeCalls, identifierPolicy, identifierGuard);
    }
    if (ts.isBinaryExpression(node)) {
        const operator = node.operatorToken.kind;
        if (isAssignmentOperator(operator) || operator === ts.SyntaxKind.CommaToken) return false;

        return isPureScalarExpression(node.left, checker, safeCalls, identifierPolicy, identifierGuard)
            && isPureScalarExpression(node.right, checker, safeCalls, identifierPolicy, identifierGuard);
    }
    if (ts.isConditionalExpression(node)) {
        return isPureScalarExpression(node.condition, checker, safeCalls, identifierPolicy, identifierGuard)
            && isPureScalarExpression(node.whenTrue, checker, safeCalls, identifierPolicy, identifierGuard)
            && isPureScalarExpression(node.whenFalse, checker, safeCalls, identifierPolicy, identifierGuard);
    }
    if (ts.isCallExpression(node) && safeCalls.has(node)) {
        return node.questionDotToken === undefined
            && node.arguments.every((argument) =>
                !ts.isSpreadElement(argument)
                && isPureScalarExpression(argument, checker, safeCalls, identifierPolicy, identifierGuard));
    }

    return false;
}

function isAssignmentOperator(kind: ts.SyntaxKind): boolean {
    return kind >= ts.SyntaxKind.FirstAssignment && kind <= ts.SyntaxKind.LastAssignment;
}

function isPrimitiveScalarType(type: ts.Type): boolean {
    if (type.isUnion()) return type.types.every(isPrimitiveScalarType);

    const primitive = ts.TypeFlags.NumberLike | ts.TypeFlags.BooleanLike | ts.TypeFlags.StringLike
        | ts.TypeFlags.Null | ts.TypeFlags.Undefined;
    return (type.flags & primitive) !== 0
        && (type.flags & (ts.TypeFlags.Any | ts.TypeFlags.Unknown | ts.TypeFlags.Intersection)) === 0;
}

function isNumberLikeType(type: ts.Type): boolean {
    if (type.isUnion()) return type.types.every(isNumberLikeType);

    return (type.flags & ts.TypeFlags.NumberLike) !== 0
        && (type.flags & (ts.TypeFlags.Any | ts.TypeFlags.Unknown | ts.TypeFlags.Intersection)) === 0;
}

function containsNode(root: ts.Node, target: ts.Node): boolean {
    if (root === target) return true;

    let found = false;
    ts.forEachChild(root, (child) => {
        if (!found && containsNode(child, target)) found = true;
    });

    return found;
}

function visit(root: ts.Node, visitor: (node: ts.Node) => void): void {
    visitor(root);
    ts.forEachChild(root, (child) => visit(child, visitor));
}

function containsThis(root: ts.Node): boolean {
    let found = false;
    visit(root, (node) => {
        if (node.kind === ts.SyntaxKind.ThisKeyword) found = true;
    });

    return found;
}

function containsNestedFunction(
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
): boolean {
    let found = false;
    ts.forEachChild(closure.body!, function visitNested(node): void {
        if (ts.isFunctionLike(node)) {
            found = true;
            return;
        }

        ts.forEachChild(node, visitNested);
    });

    return found;
}

function isAdmittedBuiltinReceiver(
    identifier: ts.Identifier,
    calls: ReadonlySet<ts.CallExpression>,
): boolean {
    const access = identifier.parent;
    return ts.isPropertyAccessExpression(access) && access.expression === identifier
        && ts.isCallExpression(access.parent) && access.parent.expression === access && calls.has(access.parent);
}

function isAdmittedBuiltinMember(
    identifier: ts.Identifier,
    calls: ReadonlySet<ts.CallExpression>,
): boolean {
    const access = identifier.parent;
    return ts.isPropertyAccessExpression(access) && access.name === identifier
        && ts.isCallExpression(access.parent) && access.parent.expression === access && calls.has(access.parent);
}

function referencedDefaultLibraryErrorSymbols(body: ts.ConciseBody, ctx: LoweringContext): Set<ts.Symbol> {
    const symbols = new Set<ts.Symbol>();
    visit(body, (node) => {
        if (!ts.isIdentifier(node) || node.text !== "Error") return;

        const symbol = ctx.converter.symbolOf(node);
        if (symbol !== undefined && isDefaultLibrarySymbol(symbol, ctx)) symbols.add(symbol);
    });

    return symbols;
}

function isPrunableErrorCapture(symbol: ts.Symbol, body: ts.ConciseBody, ctx: LoweringContext): boolean {
    const uses: ts.Identifier[] = [];
    visit(body, (node) => {
        if (ts.isIdentifier(node) && ctx.converter.symbolOf(node) === symbol) uses.push(node);
    });

    return uses.length > 0 && uses.every((identifier) =>
        ts.isNewExpression(identifier.parent) && identifier.parent.expression === identifier);
}
