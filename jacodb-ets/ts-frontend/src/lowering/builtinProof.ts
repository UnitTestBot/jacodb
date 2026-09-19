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
import { BuiltinCallProofDto } from "../dto/values";
import { MethodContext, VerifiedBuiltinEntry } from "./methodBuilder";

/**
 * Prove the one closed builtin context supported by the first model increment.
 * The result remains conditional on direct isolated execution of [entryMethod].
 */
export function verifiedBuiltinEntryFor(
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    m: MethodContext,
    entryMethod: MethodSignatureDto,
): VerifiedBuiltinEntry | undefined {
    if (!isExportedTopLevelScalarClosure(closure, m.checker)) return undefined;
    if (!hasAdmissibleModuleInitialization(closure.getSourceFile(), m.checker)) return undefined;
    if (containsThis(closure.body!)) return undefined;
    if (containsNestedFunction(closure)) return undefined;

    const calls = collectNumberIsIntegerCalls(closure, m);
    const admittedCalls = calls.filter((call) => hasEffectFreeScalarPrefix(closure, call, m.checker));
    if (admittedCalls.length === 0) return undefined;

    const admittedSet = new Set<ts.CallExpression>(admittedCalls);
    const receiver = admittedCalls[0].expression.expression;
    if (!ts.isIdentifier(receiver)) return undefined;
    const numberSymbol = m.converter.symbolOf(receiver);
    if (numberSymbol === undefined) return undefined;
    const memberSymbols = new Set<ts.Symbol>();
    for (const call of admittedCalls) {
        const member = m.converter.symbolOf(call.expression.name);
        if (member !== undefined) memberSymbols.add(member);
    }

    let unsafeBuiltinUse = false;
    visit(closure.body!, (node) => {
        if (!ts.isIdentifier(node)) return;
        const symbol = m.converter.symbolOf(node);
        if (symbol === numberSymbol && !isAdmittedNumberReceiver(node, admittedSet)) {
            unsafeBuiltinUse = true;
        }
        if (symbol !== undefined && memberSymbols.has(symbol) && !isAdmittedNumberMember(node, admittedSet)) {
            unsafeBuiltinUse = true;
        }
    });
    if (unsafeBuiltinUse) return undefined;

    const prunableCaptures = new Set<ts.Symbol>([numberSymbol]);
    const errorSymbols = referencedDefaultLibraryErrorSymbols(closure.body!, m);
    for (const errorSymbol of errorSymbols) {
        if (!isPrunableErrorCapture(errorSymbol, closure.body!, m)) return undefined;
        prunableCaptures.add(errorSymbol);
    }

    for (const identifier of collectFreeCaptureIdentifiers(closure, m.checker)) {
        if (m.moduleFieldForIdentifier(identifier) !== undefined) continue;
        const symbol = m.converter.symbolOf(identifier);
        if (symbol === undefined || !prunableCaptures.has(symbol)) {
            return undefined;
        }
    }

    const proofs = new Map<ts.CallExpression, BuiltinCallProofDto>();
    for (const call of admittedCalls) {
        proofs.set(call, {
            builtin: "NUMBER_IS_INTEGER",
            entryRequirement: "DIRECT_ISOLATED_ENTRY",
            entryMethod,
        });
    }
    return { calls: proofs, prunableCaptures };
}

function isExportedTopLevelScalarClosure(
    closure: ts.ArrowFunction | ts.FunctionExpression | ts.FunctionDeclaration,
    checker: ts.TypeChecker,
): closure is ts.ArrowFunction | ts.FunctionExpression {
    if (!ts.isArrowFunction(closure) && !ts.isFunctionExpression(closure)) return false;
    const declaration = closure.parent;
    if (!ts.isVariableDeclaration(declaration) || declaration.initializer !== closure) return false;
    if (!ts.isIdentifier(declaration.name)) return false;
    const declarationList = declaration.parent;
    if (!ts.isVariableDeclarationList(declarationList) || declarationList.declarations.length !== 1) return false;
    if ((declarationList.flags & ts.NodeFlags.Const) === 0) return false;
    const statement = declarationList.parent;
    if (!ts.isVariableStatement(statement) || !ts.isSourceFile(statement.parent)) return false;
    if (!statement.modifiers?.some((modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword)) return false;
    if (closure.parameters.some((parameter) =>
        !ts.isIdentifier(parameter.name)
        || parameter.initializer !== undefined
        || parameter.dotDotDotToken !== undefined
        || !isPrimitiveScalarType(checker.getTypeAtLocation(parameter.name)))) {
        return false;
    }
    const signature = checker.getSignatureFromDeclaration(closure);
    return signature !== undefined && isPrimitiveScalarType(checker.getReturnTypeOfSignature(signature));
}

function hasAdmissibleModuleInitialization(sourceFile: ts.SourceFile, checker: ts.TypeChecker): boolean {
    return sourceFile.statements.every((statement) => {
        if (ts.isEmptyStatement(statement) || ts.isInterfaceDeclaration(statement) || ts.isTypeAliasDeclaration(statement)) {
            return true;
        }
        if (ts.isFunctionDeclaration(statement)) {
            return statement.body !== undefined && statement.modifiers?.every((modifier) =>
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
                || isPureScalarExpression(declaration.initializer, checker, ScalarIdentifierPolicy.REJECT)
            ),
        );
    });
}

function collectNumberIsIntegerCalls(
    closure: ts.ArrowFunction | ts.FunctionExpression,
    m: MethodContext,
): Array<ts.CallExpression & { expression: ts.PropertyAccessExpression }> {
    const calls: Array<ts.CallExpression & { expression: ts.PropertyAccessExpression }> = [];
    const visitBody = (node: ts.Node): void => {
        if (node !== closure.body && ts.isFunctionLike(node)) return;
        if (isDefaultLibraryNumberIsIntegerCall(node, m)) calls.push(node);
        ts.forEachChild(node, visitBody);
    };
    visitBody(closure.body);
    return calls;
}

function isDefaultLibraryNumberIsIntegerCall(
    node: ts.Node,
    m: MethodContext,
): node is ts.CallExpression & { expression: ts.PropertyAccessExpression } {
    if (!ts.isCallExpression(node) || node.questionDotToken !== undefined || node.arguments.length !== 1) return false;
    const callee = node.expression;
    if (!ts.isPropertyAccessExpression(callee) || callee.questionDotToken !== undefined) return false;
    if (!ts.isIdentifier(callee.expression) || callee.expression.text !== "Number" || callee.name.text !== "isInteger") {
        return false;
    }
    return isDefaultLibrarySymbol(m.converter.symbolOf(callee.expression), m)
        && isDefaultLibrarySymbol(m.converter.symbolOf(callee.name), m)
        && isNumberLikeType(m.checker.getTypeAtLocation(node.arguments[0]));
}

function isDefaultLibrarySymbol(symbol: ts.Symbol | undefined, m: MethodContext): boolean {
    const declarations = symbol?.declarations;
    return declarations !== undefined
        && declarations.length > 0
        && declarations.every((declaration) => m.ctx.isDefaultLibrarySourceFile(declaration.getSourceFile()));
}

function hasEffectFreeScalarPrefix(
    closure: ts.ArrowFunction | ts.FunctionExpression,
    call: ts.CallExpression,
    checker: ts.TypeChecker,
): boolean {
    if (!call.arguments.every((argument) => !ts.isSpreadElement(argument) && isPureScalarExpression(argument, checker))) {
        return false;
    }
    if (!ts.isBlock(closure.body)) return hasPurePrefixWithinExpression(closure.body, call, checker);

    let containingStatement: ts.Node = call;
    while (containingStatement.parent !== closure.body) {
        containingStatement = containingStatement.parent;
        if (ts.isFunctionLike(containingStatement)) return false;
    }
    const statementIndex = closure.body.statements.findIndex((statement) => statement === containingStatement);
    if (statementIndex < 0) return false;
    if (!closure.body.statements.slice(0, statementIndex).every((statement) =>
        isPureScalarPrefixStatement(statement, checker))) return false;

    const statement = closure.body.statements[statementIndex];
    if (ts.isIfStatement(statement)) {
        return containsNode(statement.expression, call)
            && hasPurePrefixWithinExpression(statement.expression, call, checker);
    }
    if (ts.isReturnStatement(statement) && statement.expression !== undefined) {
        return hasPurePrefixWithinExpression(statement.expression, call, checker);
    }
    return false;
}

function isPureScalarPrefixStatement(statement: ts.Statement, checker: ts.TypeChecker): boolean {
    if (ts.isEmptyStatement(statement)) return true;
    if (!ts.isVariableStatement(statement)) return false;
    return statement.declarationList.declarations.every((declaration) =>
        ts.isIdentifier(declaration.name)
        && declaration.initializer !== undefined
        && isPrimitiveScalarType(checker.getTypeAtLocation(declaration.name))
        && isPureScalarExpression(declaration.initializer, checker),
    );
}

function hasPurePrefixWithinExpression(root: ts.Expression, target: ts.CallExpression, checker: ts.TypeChecker): boolean {
    if (root === target) return true;
    const children: ts.Node[] = [];
    ts.forEachChild(root, (child) => {
        children.push(child);
    });
    for (let index = 0; index < children.length; index++) {
        const child = children[index];
        if (!containsNode(child, target)) continue;
        const earlierExpressions = children.slice(0, index).filter(ts.isExpression);
        return earlierExpressions.every((expression) => isPureScalarExpression(expression, checker))
            && ts.isExpression(child)
            && hasPurePrefixWithinExpression(child, target, checker);
    }
    return false;
}

enum ScalarIdentifierPolicy {
    ALLOW,
    REJECT,
}

function isPureScalarExpression(
    node: ts.Expression,
    checker: ts.TypeChecker,
    identifierPolicy: ScalarIdentifierPolicy = ScalarIdentifierPolicy.ALLOW,
): boolean {
    if (!isPrimitiveScalarType(checker.getTypeAtLocation(node))) return false;
    if (ts.isIdentifier(node)) return identifierPolicy === ScalarIdentifierPolicy.ALLOW;
    if (ts.isNumericLiteral(node) || ts.isStringLiteral(node)
        || ts.isNoSubstitutionTemplateLiteral(node) || node.kind === ts.SyntaxKind.TrueKeyword
        || node.kind === ts.SyntaxKind.FalseKeyword || node.kind === ts.SyntaxKind.NullKeyword) return true;
    if (ts.isParenthesizedExpression(node) || ts.isAsExpression(node) || ts.isTypeAssertionExpression(node)
        || ts.isNonNullExpression(node) || ts.isSatisfiesExpression(node)) {
        return isPureScalarExpression(node.expression, checker, identifierPolicy);
    }
    if (ts.isPrefixUnaryExpression(node)) {
        if (node.operator === ts.SyntaxKind.PlusPlusToken || node.operator === ts.SyntaxKind.MinusMinusToken) {
            return false;
        }
        return isPureScalarExpression(node.operand, checker, identifierPolicy);
    }
    if (ts.isBinaryExpression(node)) {
        const operator = node.operatorToken.kind;
        if (isAssignmentOperator(operator) || operator === ts.SyntaxKind.CommaToken) return false;
        return isPureScalarExpression(node.left, checker, identifierPolicy)
            && isPureScalarExpression(node.right, checker, identifierPolicy);
    }
    if (ts.isConditionalExpression(node)) {
        return isPureScalarExpression(node.condition, checker, identifierPolicy)
            && isPureScalarExpression(node.whenTrue, checker, identifierPolicy)
            && isPureScalarExpression(node.whenFalse, checker, identifierPolicy);
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

function containsNestedFunction(closure: ts.ArrowFunction | ts.FunctionExpression): boolean {
    let found = false;
    ts.forEachChild(closure.body, function visitNested(node): void {
        if (ts.isFunctionLike(node)) {
            found = true;
            return;
        }
        ts.forEachChild(node, visitNested);
    });
    return found;
}

function isAdmittedNumberReceiver(identifier: ts.Identifier, calls: ReadonlySet<ts.CallExpression>): boolean {
    const access = identifier.parent;
    return ts.isPropertyAccessExpression(access) && access.expression === identifier
        && ts.isCallExpression(access.parent) && access.parent.expression === access && calls.has(access.parent);
}

function isAdmittedNumberMember(identifier: ts.Identifier, calls: ReadonlySet<ts.CallExpression>): boolean {
    const access = identifier.parent;
    return ts.isPropertyAccessExpression(access) && access.name === identifier
        && ts.isCallExpression(access.parent) && access.parent.expression === access && calls.has(access.parent);
}

function referencedDefaultLibraryErrorSymbols(body: ts.ConciseBody, m: MethodContext): Set<ts.Symbol> {
    const symbols = new Set<ts.Symbol>();
    visit(body, (node) => {
        if (!ts.isIdentifier(node) || node.text !== "Error") return;
        const symbol = m.converter.symbolOf(node);
        if (symbol !== undefined && isDefaultLibrarySymbol(symbol, m)) symbols.add(symbol);
    });
    return symbols;
}

function isPrunableErrorCapture(symbol: ts.Symbol, body: ts.ConciseBody, m: MethodContext): boolean {
    const uses: ts.Identifier[] = [];
    visit(body, (node) => {
        if (ts.isIdentifier(node) && m.converter.symbolOf(node) === symbol) uses.push(node);
    });
    return uses.length > 0 && uses.every((identifier) =>
        ts.isNewExpression(identifier.parent) && identifier.parent.expression === identifier);
}

function collectFreeCaptureIdentifiers(
    closure: ts.ArrowFunction | ts.FunctionExpression,
    checker: ts.TypeChecker,
): ts.Identifier[] {
    const ownDeclarations = new Set<ts.Declaration>([closure]);
    const collectOwn = (node: ts.Node): void => {
        if (isCapturableDeclaration(node)) ownDeclarations.add(node);
        ts.forEachChild(node, collectOwn);
    };
    closure.parameters.forEach(collectOwn);
    collectOwn(closure.body);

    const result = new Map<ts.Symbol, ts.Identifier>();
    visit(closure.body, (node) => {
        if (!ts.isIdentifier(node)) return;
        let symbol: ts.Symbol | undefined;
        try {
            symbol = checker.getSymbolAtLocation(node);
            if (ts.isShorthandPropertyAssignment(node.parent) && node.parent.name === node) {
                symbol = checker.getShorthandAssignmentValueSymbol(node.parent) ?? symbol;
            }
        } catch {
            return;
        }
        if (symbol === undefined || result.has(symbol)) return;
        const declarations = symbol.declarations?.filter(isCapturableDeclaration);
        if (declarations === undefined || declarations.length === 0) return;
        if (declarations.some((declaration) => ownDeclarations.has(declaration))) return;
        result.set(symbol, node);
    });
    return [...result.values()];
}

function isCapturableDeclaration(node: ts.Node): node is ts.Declaration {
    return ts.isVariableDeclaration(node)
        || ts.isParameter(node)
        || ts.isBindingElement(node)
        || ts.isFunctionDeclaration(node);
}
