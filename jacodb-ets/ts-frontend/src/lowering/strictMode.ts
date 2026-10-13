import * as ts from "typescript";

/** Strictness belongs to the lexical source scope, independently of its callers. */
export function isStrictMode(node: ts.Node): boolean {
    for (let scope: ts.Node | undefined = node; scope !== undefined; scope = scope.parent) {
        if (ts.isClassDeclaration(scope) || ts.isClassExpression(scope)) {
            return true;
        }
        if (ts.isSourceFile(scope)) {
            return ts.isExternalModule(scope) || hasUseStrictDirective(scope.statements);
        }

        const body = functionBody(scope);
        if (body === undefined || !ts.isBlock(body)) {
            continue;
        }
        if (hasUseStrictDirective(body.statements)) {
            return true;
        }
    }
    return false;
}

function functionBody(node: ts.Node): ts.ConciseBody | undefined {
    const hasFunctionBody = ts.isFunctionDeclaration(node) || ts.isFunctionExpression(node)
        || ts.isArrowFunction(node) || ts.isMethodDeclaration(node) || ts.isConstructorDeclaration(node)
        || ts.isGetAccessorDeclaration(node) || ts.isSetAccessorDeclaration(node);
    return hasFunctionBody ? node.body : undefined;
}

function hasUseStrictDirective(statements: readonly ts.Statement[]): boolean {
    for (const statement of statements) {
        if (!ts.isExpressionStatement(statement) || !ts.isStringLiteral(statement.expression)) {
            return false;
        }
        // Escaped spellings are string literals, but are not Use Strict Directives.
        const spelling = statement.expression.getText();
        if (spelling === '\"use strict\"' || spelling === "'use strict'") {
            return true;
        }
    }
    return false;
}
