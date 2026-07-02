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
 * Statement lowering: ts.Statement -> stmts/terminators in the CFG builder.
 *
 * Any statement kind that cannot be lowered degrades to a raw fallback stmt
 * plus a diagnostic — the frontend never fails on parseable input.
 */

import * as ts from "typescript";
import { unsupportedStmt } from "./diagnostics";
import { ExprLowerer, LoweringError } from "./exprLowering";
import { MethodContext } from "./methodBuilder";

export class StmtLowerer {
    readonly expr: ExprLowerer;

    constructor(private readonly m: MethodContext) {
        this.expr = new ExprLowerer(m);
    }

    lowerStatements(statements: readonly ts.Statement[]): void {
        for (const statement of statements) {
            this.lowerStatement(statement);
        }
    }

    lowerStatement(node: ts.Statement): void {
        try {
            this.lowerStatementImpl(node);
        } catch (e) {
            if (e instanceof LoweringError) {
                this.m.diagnostics.warn(node, `unsupported statement: ${e.message}`);
                this.m.cfg.emit(unsupportedStmt(node));
                return;
            }
            throw e;
        }
    }

    private lowerStatementImpl(node: ts.Statement): void {
        if (ts.isVariableStatement(node)) {
            this.lowerVariableDeclarations(node.declarationList);
            return;
        }
        if (ts.isExpressionStatement(node)) {
            this.expr.lowerDiscarded(node.expression);
            return;
        }
        if (ts.isReturnStatement(node)) {
            this.m.cfg.ret(node.expression !== undefined ? this.expr.lowerToImmediate(node.expression) : undefined);
            return;
        }
        if (ts.isThrowStatement(node)) {
            this.m.cfg.throwValue(this.expr.lowerToImmediate(node.expression));
            return;
        }
        if (ts.isBlock(node)) {
            this.lowerStatements(node.statements);
            return;
        }
        if (node.kind === ts.SyntaxKind.EmptyStatement) {
            return;
        }
        if (
            ts.isFunctionDeclaration(node) ||
            ts.isClassDeclaration(node) ||
            ts.isInterfaceDeclaration(node) ||
            ts.isEnumDeclaration(node) ||
            ts.isModuleDeclaration(node) ||
            ts.isTypeAliasDeclaration(node) ||
            ts.isImportDeclaration(node) ||
            ts.isExportDeclaration(node) ||
            ts.isExportAssignment(node) ||
            ts.isImportEqualsDeclaration(node)
        ) {
            // Declarations are handled at file/class level, not as body statements.
            return;
        }
        throw new LoweringError(ts.SyntaxKind[node.kind]);
    }

    lowerVariableDeclarations(list: ts.VariableDeclarationList): void {
        for (const decl of list.declarations) {
            this.lowerVariableDeclaration(decl);
        }
    }

    private lowerVariableDeclaration(decl: ts.VariableDeclaration): void {
        if (!ts.isIdentifier(decl.name)) {
            // Destructuring patterns are supported in M7.
            throw new LoweringError(`destructuring declaration: ${decl.name.getText().slice(0, 50)}`);
        }
        const declaredType =
            decl.type !== undefined
                ? this.m.converter.convertTypeNode(decl.type)
                : this.m.converter.typeOfNode(decl.name);
        const local = this.m.getOrCreateLocal(decl.name.text, declaredType);
        if (decl.initializer !== undefined) {
            const value = this.expr.lowerExpr(decl.initializer);
            this.m.cfg.emit({ _: "AssignStmt", left: local, right: value });
        }
    }
}
