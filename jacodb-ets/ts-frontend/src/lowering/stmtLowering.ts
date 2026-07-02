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
import { MethodSignatureDto, UNKNOWN_CLASS_SIGNATURE, UNKNOWN_FILE_SIGNATURE } from "../dto/signatures";
import { BOOLEAN_TYPE, NUMBER_TYPE, UNKNOWN_TYPE } from "../dto/types";
import { LocalDto } from "../dto/values";
import { Label } from "./cfg";
import { unsupportedStmt } from "./diagnostics";
import { ExprLowerer, LoweringError, constant } from "./exprLowering";
import { MethodContext } from "./methodBuilder";

/** Target labels for break/continue resolution. */
interface BreakableContext {
    kind: "loop" | "switch" | "labeled-block";
    breakTarget: Label;
    continueTarget?: Label;
    label?: string;
}

export class StmtLowerer {
    readonly expr: ExprLowerer;
    private readonly breakables: BreakableContext[] = [];
    private pendingLabel: string | undefined;

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
        // The pending label (from a LabeledStatement) applies only to the directly wrapped stmt.
        const label = this.pendingLabel;
        this.pendingLabel = undefined;

        if (ts.isVariableStatement(node)) {
            this.lowerVariableDeclarations(node.declarationList);
            return;
        }
        if (ts.isIfStatement(node)) {
            this.lowerIf(node);
            return;
        }
        if (ts.isWhileStatement(node)) {
            this.lowerWhile(node, label);
            return;
        }
        if (ts.isDoStatement(node)) {
            this.lowerDoWhile(node, label);
            return;
        }
        if (ts.isForStatement(node)) {
            this.lowerFor(node, label);
            return;
        }
        if (ts.isForOfStatement(node)) {
            this.lowerForOf(node, label);
            return;
        }
        if (ts.isForInStatement(node)) {
            this.lowerForIn(node, label);
            return;
        }
        if (ts.isSwitchStatement(node)) {
            this.lowerSwitch(node, label);
            return;
        }
        if (ts.isBreakStatement(node)) {
            this.lowerBreak(node);
            return;
        }
        if (ts.isContinueStatement(node)) {
            this.lowerContinue(node);
            return;
        }
        if (ts.isLabeledStatement(node)) {
            this.lowerLabeled(node);
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

    // ------------------------------------------------------------------
    // Control flow
    // ------------------------------------------------------------------

    private lowerIf(node: ts.IfStatement): void {
        const cfg = this.m.cfg;
        const thenLabel = cfg.newLabel();
        const endLabel = cfg.newLabel();
        const elseLabel = node.elseStatement !== undefined ? cfg.newLabel() : endLabel;

        this.expr.lowerCondition(node.expression, thenLabel, elseLabel);
        cfg.placeLabel(thenLabel);
        this.lowerStatement(node.thenStatement);
        cfg.goto(endLabel);
        if (node.elseStatement !== undefined) {
            cfg.placeLabel(elseLabel);
            this.lowerStatement(node.elseStatement);
            cfg.goto(endLabel);
        }
        cfg.placeLabel(endLabel);
    }

    private lowerWhile(node: ts.WhileStatement, label: string | undefined): void {
        const cfg = this.m.cfg;
        const headLabel = cfg.newLabel();
        const bodyLabel = cfg.newLabel();
        const exitLabel = cfg.newLabel();

        cfg.placeLabel(headLabel);
        this.expr.lowerCondition(node.expression, bodyLabel, exitLabel);
        cfg.placeLabel(bodyLabel);
        this.inBreakable({ kind: "loop", breakTarget: exitLabel, continueTarget: headLabel, label }, () => {
            this.lowerStatement(node.statement);
        });
        cfg.goto(headLabel);
        cfg.placeLabel(exitLabel);
    }

    private lowerDoWhile(node: ts.DoStatement, label: string | undefined): void {
        const cfg = this.m.cfg;
        const bodyLabel = cfg.newLabel();
        const condLabel = cfg.newLabel();
        const exitLabel = cfg.newLabel();

        cfg.placeLabel(bodyLabel);
        this.inBreakable({ kind: "loop", breakTarget: exitLabel, continueTarget: condLabel, label }, () => {
            this.lowerStatement(node.statement);
        });
        cfg.placeLabel(condLabel);
        this.expr.lowerCondition(node.expression, bodyLabel, exitLabel);
        cfg.placeLabel(exitLabel);
    }

    private lowerFor(node: ts.ForStatement, label: string | undefined): void {
        const cfg = this.m.cfg;
        if (node.initializer !== undefined) {
            if (ts.isVariableDeclarationList(node.initializer)) {
                this.lowerVariableDeclarations(node.initializer);
            } else {
                this.expr.lowerDiscarded(node.initializer);
            }
        }
        const headLabel = cfg.newLabel();
        const bodyLabel = cfg.newLabel();
        const updateLabel = cfg.newLabel();
        const exitLabel = cfg.newLabel();

        cfg.placeLabel(headLabel);
        if (node.condition !== undefined) {
            this.expr.lowerCondition(node.condition, bodyLabel, exitLabel);
        } else {
            cfg.goto(bodyLabel);
        }
        cfg.placeLabel(bodyLabel);
        this.inBreakable({ kind: "loop", breakTarget: exitLabel, continueTarget: updateLabel, label }, () => {
            this.lowerStatement(node.statement);
        });
        cfg.goto(updateLabel);
        cfg.placeLabel(updateLabel);
        if (node.incrementor !== undefined) {
            this.expr.lowerDiscarded(node.incrementor);
        }
        cfg.goto(headLabel);
        cfg.placeLabel(exitLabel);
    }

    /**
     * `for (const v of iterable)` — iterator protocol, ArkAnalyzer-shaped:
     *   %it := iterable.Symbol.iterator()
     *   head: %res := %it.next(); %done := %res.done
     *         if (%done == true) exit else body
     *   body: v := %res.value; ...
     */
    private lowerForOf(node: ts.ForOfStatement, label: string | undefined): void {
        const cfg = this.m.cfg;
        const iterable = this.expr.lowerToLocal(node.expression);

        const iterator = this.m.newTemp(UNKNOWN_TYPE);
        cfg.emit({
            _: "AssignStmt",
            left: iterator,
            right: {
                _: "InstanceCallExpr",
                instance: iterable,
                method: unknownMethod("Symbol.iterator"),
                args: [],
            },
        });

        const headLabel = cfg.newLabel();
        const bodyLabel = cfg.newLabel();
        const exitLabel = cfg.newLabel();

        cfg.placeLabel(headLabel);
        const result = this.m.newTemp(UNKNOWN_TYPE);
        cfg.emit({
            _: "AssignStmt",
            left: result,
            right: { _: "InstanceCallExpr", instance: iterator, method: unknownMethod("next"), args: [] },
        });
        const done = this.m.newTemp(BOOLEAN_TYPE);
        cfg.emit({
            _: "AssignStmt",
            left: done,
            right: {
                _: "InstanceFieldRef",
                instance: result,
                field: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "done", type: BOOLEAN_TYPE },
            },
        });
        // if (done == true) -> exit, else -> body
        cfg.branch(this.expr.relation("==", done, constant("true", BOOLEAN_TYPE)), exitLabel, bodyLabel);

        cfg.placeLabel(bodyLabel);
        const bindTarget = this.forEachBindingLocal(node.initializer);
        cfg.emit({
            _: "AssignStmt",
            left: bindTarget,
            right: {
                _: "InstanceFieldRef",
                instance: result,
                field: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "value", type: bindTarget.type },
            },
        });
        this.inBreakable({ kind: "loop", breakTarget: exitLabel, continueTarget: headLabel, label }, () => {
            this.lowerStatement(node.statement);
        });
        cfg.goto(headLabel);
        cfg.placeLabel(exitLabel);
    }

    /**
     * `for (const k in obj)` — index loop over `Object.keys(obj)`:
     *   %keys := Object.keys(obj); %i := 0
     *   head: %len := %keys.length; if (%i < %len) body else exit
     *   body: k := %keys[%i]; ...; %i := %i ++
     */
    private lowerForIn(node: ts.ForInStatement, label: string | undefined): void {
        const cfg = this.m.cfg;
        const target = this.expr.lowerToImmediate(node.expression);

        const keys = this.m.newTemp({ _: "ArrayType", elementType: { _: "StringType" }, dimensions: 1 });
        cfg.emit({
            _: "AssignStmt",
            left: keys,
            right: {
                _: "StaticCallExpr",
                method: {
                    declaringClass: { name: "Object", declaringFile: UNKNOWN_FILE_SIGNATURE },
                    name: "keys",
                    parameters: [],
                    returnType: { _: "ArrayType", elementType: { _: "StringType" }, dimensions: 1 },
                },
                args: [target],
            },
        });
        const index = this.m.newTemp(NUMBER_TYPE);
        cfg.emit({ _: "AssignStmt", left: index, right: constant("0", NUMBER_TYPE) });

        const headLabel = cfg.newLabel();
        const bodyLabel = cfg.newLabel();
        const exitLabel = cfg.newLabel();

        cfg.placeLabel(headLabel);
        const length = this.m.newTemp(NUMBER_TYPE);
        cfg.emit({
            _: "AssignStmt",
            left: length,
            right: {
                _: "InstanceFieldRef",
                instance: keys,
                field: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "length", type: NUMBER_TYPE },
            },
        });
        cfg.branch(this.expr.relation("<", index, length), bodyLabel, exitLabel);

        cfg.placeLabel(bodyLabel);
        const bindTarget = this.forEachBindingLocal(node.initializer);
        cfg.emit({
            _: "AssignStmt",
            left: bindTarget,
            right: { _: "ArrayRef", array: keys, index, type: { _: "StringType" } },
        });
        const continueLabel = cfg.newLabel();
        this.inBreakable({ kind: "loop", breakTarget: exitLabel, continueTarget: continueLabel, label }, () => {
            this.lowerStatement(node.statement);
        });
        cfg.goto(continueLabel);
        cfg.placeLabel(continueLabel);
        cfg.emit({ _: "AssignStmt", left: index, right: { _: "UnopExpr", op: "++", arg: index } });
        cfg.goto(headLabel);
        cfg.placeLabel(exitLabel);
    }

    /** The loop variable of for-of/for-in (identifier bindings only). */
    private forEachBindingLocal(initializer: ts.ForInitializer): LocalDto {
        if (ts.isVariableDeclarationList(initializer)) {
            const decl = initializer.declarations[0];
            if (decl !== undefined && ts.isIdentifier(decl.name)) {
                return this.m.getOrCreateLocal(decl.name.text, this.m.converter.typeOfNode(decl.name));
            }
            throw new LoweringError("destructuring loop binding");
        }
        if (ts.isIdentifier(initializer)) {
            return this.m.getOrCreateLocal(initializer.text, this.m.converter.typeOfNode(initializer));
        }
        throw new LoweringError("unsupported loop binding");
    }

    private lowerSwitch(node: ts.SwitchStatement, label: string | undefined): void {
        const cfg = this.m.cfg;
        const discriminant = this.expr.lowerToImmediate(node.expression);
        const exitLabel = cfg.newLabel();

        const clauses = node.caseBlock.clauses;
        const bodyLabels = clauses.map(() => cfg.newLabel());

        // Comparison chain (=== per case, default last).
        let defaultIndex = -1;
        clauses.forEach((clause, i) => {
            if (ts.isCaseClause(clause)) {
                const nextTest = cfg.newLabel();
                const caseValue = this.expr.lowerToImmediate(clause.expression);
                cfg.branch(this.expr.relation("===", discriminant, caseValue), bodyLabels[i], nextTest);
                cfg.placeLabel(nextTest);
            } else {
                defaultIndex = i;
            }
        });
        cfg.goto(defaultIndex >= 0 ? bodyLabels[defaultIndex] : exitLabel);

        // Bodies in source order with natural fallthrough.
        this.inBreakable({ kind: "switch", breakTarget: exitLabel, label }, () => {
            clauses.forEach((clause, i) => {
                cfg.placeLabel(bodyLabels[i]);
                for (const statement of clause.statements) {
                    this.lowerStatement(statement);
                }
                // fallthrough to the next body (or exit) happens via placeLabel/goto below
            });
        });
        cfg.goto(exitLabel);
        cfg.placeLabel(exitLabel);
    }

    private lowerBreak(node: ts.BreakStatement): void {
        const target = this.findBreakable(node.label?.text, /* forContinue */ false);
        if (target === undefined) {
            throw new LoweringError(`break outside of a breakable context`);
        }
        this.m.cfg.goto(target.breakTarget);
    }

    private lowerContinue(node: ts.ContinueStatement): void {
        const target = this.findBreakable(node.label?.text, /* forContinue */ true);
        if (target === undefined || target.continueTarget === undefined) {
            throw new LoweringError(`continue outside of a loop`);
        }
        this.m.cfg.goto(target.continueTarget);
    }

    private lowerLabeled(node: ts.LabeledStatement): void {
        const inner = node.statement;
        if (
            ts.isWhileStatement(inner) ||
            ts.isDoStatement(inner) ||
            ts.isForStatement(inner) ||
            ts.isForOfStatement(inner) ||
            ts.isForInStatement(inner) ||
            ts.isSwitchStatement(inner)
        ) {
            this.pendingLabel = node.label.text;
            this.lowerStatement(inner);
            return;
        }
        // Labeled block: breakable region.
        const cfg = this.m.cfg;
        const exitLabel = cfg.newLabel();
        this.inBreakable({ kind: "labeled-block", breakTarget: exitLabel, label: node.label.text }, () => {
            this.lowerStatement(inner);
        });
        cfg.goto(exitLabel);
        cfg.placeLabel(exitLabel);
    }

    private inBreakable(context: BreakableContext, body: () => void): void {
        this.breakables.push(context);
        try {
            body();
        } finally {
            this.breakables.pop();
        }
    }

    private findBreakable(label: string | undefined, forContinue: boolean): BreakableContext | undefined {
        for (let i = this.breakables.length - 1; i >= 0; i--) {
            const context = this.breakables[i];
            if (label !== undefined) {
                if (context.label === label && (!forContinue || context.kind === "loop")) {
                    return context;
                }
            } else if (!forContinue || context.kind === "loop") {
                return context;
            }
        }
        return undefined;
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

function unknownMethod(name: string): MethodSignatureDto {
    return {
        declaringClass: UNKNOWN_CLASS_SIGNATURE,
        name,
        parameters: [],
        returnType: UNKNOWN_TYPE,
    };
}
