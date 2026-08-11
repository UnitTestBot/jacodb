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
import { BOOLEAN_TYPE, NUMBER_TYPE, TypeDto, UNDEFINED_TYPE, UNKNOWN_TYPE } from "../dto/types";
import { LValueDto, LocalDto, ValueDto } from "../dto/values";
import { Label } from "./cfg";
import { unsupportedStmt } from "./diagnostics";
import { ExprLowerer, LoweringError, constant } from "./exprLowering";
import { MethodContext } from "./methodBuilder";

/** The lowered loop variable of a for-of/for-in statement. */
interface LoopBinding {
    target: LValueDto;
    type: TypeDto;
    pattern?: ts.BindingPattern;
    patternSource?: LocalDto;
}

/** Target labels for break/continue resolution. */
interface BreakableContext {
    kind: "loop" | "switch" | "labeled-block";
    breakTarget: Label;
    continueTarget?: Label;
    label?: string;
    /** finallyScopes depth at the moment this breakable was entered. */
    finallyDepth?: number;
}

export class StmtLowerer {
    readonly expr: ExprLowerer;
    private readonly breakables: BreakableContext[] = [];
    /** finally blocks of the enclosing try statements (outermost first). */
    private readonly finallyScopes: ts.Block[] = [];
    private pendingLabel: string | undefined;

    constructor(
        private readonly m: MethodContext,
        afterSuperCall?: () => void,
    ) {
        // Nested function bodies (closures, object-literal methods) are lowered
        // with a fresh StmtLowerer over their own MethodContext.
        this.expr = new ExprLowerer(m, (nestedContext, body, parameters) => {
            const nested = new StmtLowerer(nestedContext);
            parameters?.forEach((parameter) => {
                if (parameter.pattern !== undefined) {
                    nested.lowerParameterBindingPattern(
                        parameter.pattern,
                        nestedContext.getOrCreateLocal(parameter.name, parameter.type),
                    );
                }
            });
            if (ts.isBlock(body)) {
                nested.lowerStatements(body.statements);
            } else {
                nestedContext.cfg.ret(nested.expr.lowerToImmediate(body));
            }
        }, afterSuperCall);
    }

    lowerStatements(statements: readonly ts.Statement[]): void {
        // Function declarations are hoisted in JavaScript, but lowering materializes
        // their captured values at the creation point: hoisting unconditionally would
        // snapshot captures BEFORE the captured variables are assigned. Hoist only the
        // declarations that are actually referenced earlier in the same statement list;
        // everything else keeps source order, so its captures are already initialized.
        const hoisted = new Set<ts.Statement>();
        statements.forEach((statement, index) => {
            if (!ts.isFunctionDeclaration(statement) || statement.name === undefined) {
                return;
            }
            const name = statement.name.text;
            for (let i = 0; i < index; i++) {
                if (referencesName(statements[i], name)) {
                    hoisted.add(statement);
                    return;
                }
            }
        });
        for (const statement of statements) {
            if (hoisted.has(statement)) {
                this.lowerStatement(statement);
            }
        }
        for (const statement of statements) {
            if (!hoisted.has(statement)) {
                this.lowerStatement(statement);
            }
        }
    }

    lowerStatement(node: ts.Statement): void {
        this.m.withOrigin(node, () => {
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
        });
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
        if (ts.isTryStatement(node)) {
            this.lowerTry(node);
            return;
        }
        if (ts.isExpressionStatement(node)) {
            this.expr.lowerDiscarded(node.expression);
            return;
        }
        if (ts.isReturnStatement(node)) {
            // JS evaluates the return value BEFORE finally blocks run; capture it
            // in a temp so finally-code mutations cannot change what is returned.
            let value = node.expression !== undefined ? this.expr.lowerToImmediate(node.expression) : undefined;
            if (value !== undefined && value._ === "Local" && this.finallyScopes.length > 0) {
                value = this.m.snapshotToLocal(value, value.type);
            }
            this.emitFinallies(0);
            this.m.cfg.ret(value);
            return;
        }
        if (ts.isThrowStatement(node)) {
            // Exceptions propagate through finally blocks: run them before the throw.
            let value = this.expr.lowerToImmediate(node.expression);
            if (value._ === "Local" && this.finallyScopes.length > 0) {
                value = this.m.snapshotToLocal(value, value.type);
            }
            this.emitFinallies(0);
            this.m.cfg.throwValue(value);
            return;
        }
        if (ts.isBlock(node)) {
            this.lowerStatements(node.statements);
            return;
        }
        if (node.kind === ts.SyntaxKind.EmptyStatement) {
            return;
        }
        if (ts.isFunctionDeclaration(node)) {
            // Top-level / namespace-level functions are lowered by the scope
            // builder (fileBuilder); NESTED functions would otherwise be lost —
            // lift them onto the file's %dflt class, like closures but keeping
            // their real name (call sites resolve them as %dflt static calls).
            const parent: ts.Node | undefined = node.parent;
            const handledByScopeBuilder =
                parent !== undefined && (ts.isSourceFile(parent) || ts.isModuleBlock(parent));
            if (!handledByScopeBuilder) {
                this.lowerNestedFunction(node);
            }
            return;
        }
        if (
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
        this.emitLoopBinding(node.initializer, (binding) => ({
            _: "InstanceFieldRef",
            instance: result,
            field: { declaringClass: UNKNOWN_CLASS_SIGNATURE, name: "value", type: binding.type },
        }));
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
        this.emitLoopBinding(node.initializer, () => ({
            _: "ArrayRef",
            array: keys,
            index,
            type: { _: "StringType" },
        }));
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

    /**
     * Bind the loop variable inside an already placed body block.
     *
     * An unsupported binding (`for (obj.x of xs)`, `for (const [a, ...rest] of xs)`, ...)
     * must NOT escape as a LoweringError: the enclosing labels of the loop are already
     * allocated, and aborting here would leave them unplaced, which makes `finalize()`
     * fail and takes the whole file down. Degrade to a raw fallback statement instead.
     */
    private emitLoopBinding(
        initializer: ts.ForInitializer,
        right: (binding: LoopBinding) => ValueDto,
    ): void {
        try {
            const binding = this.loopBinding(initializer);
            this.m.cfg.emit({ _: "AssignStmt", left: binding.target, right: right(binding) });
            if (binding.pattern !== undefined && binding.patternSource !== undefined) {
                this.lowerBindingPattern(binding.pattern, binding.patternSource);
            }
        } catch (e) {
            if (e instanceof LoweringError) {
                this.m.diagnostics.warn(initializer, `unsupported loop binding: ${e.message}`);
                this.m.cfg.emit(unsupportedStmt(initializer));
                return;
            }
            throw e;
        }
    }

    /** The loop variable of for-of/for-in; destructuring goes through a temp + pattern. */
    private loopBinding(initializer: ts.ForInitializer): LoopBinding {
        if (ts.isVariableDeclarationList(initializer)) {
            const decl = initializer.declarations[0];
            if (decl !== undefined && ts.isIdentifier(decl.name)) {
                const type = this.m.converter.typeOfNode(decl.name);
                return {
                    target: this.m.moduleFieldForIdentifier(decl.name) ?? this.m.localForIdentifier(decl.name, type),
                    type,
                };
            }
            if (decl !== undefined) {
                const patternSource = this.m.newTemp(UNKNOWN_TYPE);
                return {
                    target: patternSource,
                    type: UNKNOWN_TYPE,
                    pattern: decl.name as ts.BindingPattern,
                    patternSource,
                };
            }
            throw new LoweringError("empty loop binding");
        }
        if (ts.isIdentifier(initializer)) {
            const type = this.m.converter.typeOfNode(initializer);
            return {
                target: this.m.moduleFieldForIdentifier(initializer) ?? this.m.localForIdentifier(initializer, type),
                type,
            };
        }
        throw new LoweringError("unsupported loop binding");
    }

    private lowerSwitch(node: ts.SwitchStatement, label: string | undefined): void {
        const cfg = this.m.cfg;
        const caseExpressions = node.caseBlock.clauses.flatMap((clause) =>
            ts.isCaseClause(clause) ? [clause.expression] : []);
        const discriminant = this.expr.lowerImmediateBefore(node.expression, caseExpressions);
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
        // Run finally blocks of try statements entered INSIDE the target breakable.
        this.emitFinallies(target.finallyDepth ?? 0);
        this.m.cfg.goto(target.breakTarget);
    }

    private lowerContinue(node: ts.ContinueStatement): void {
        const target = this.findBreakable(node.label?.text, /* forContinue */ true);
        if (target === undefined || target.continueTarget === undefined) {
            throw new LoweringError(`continue outside of a loop`);
        }
        // Run finally blocks of try statements entered INSIDE the target loop.
        this.emitFinallies(target.finallyDepth ?? 0);
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

    /**
     * `try { A } catch (e) { B } finally { C }`.
     *
     * The DTO has no trap tables and non-if blocks allow at most one successor,
     * so exception edges cannot be expressed. ArkAnalyzer simply DROPS catch
     * blocks; we instead keep them analyzable via a synthetic nondeterministic
     * branch on an undefined `%N` local:
     *
     *   if (%exc != 0) -> catch else -> try
     *   try:   A; goto join
     *   catch: e := CaughtExceptionRef; B; goto join
     *   join:  C (finally, shared by both paths)
     *
     * Finally blocks are DUPLICATED on abrupt exits: every return/throw and
     * every break/continue that leaves the try emits a copy of C (innermost
     * scopes first) before its terminator, so C never drops out of the IR even
     * when the try body always exits abruptly.
     */
    private lowerTry(node: ts.TryStatement): void {
        const cfg = this.m.cfg;
        const joinLabel = cfg.newLabel();
        const tryLabel = cfg.newLabel();

        if (node.finallyBlock !== undefined) {
            this.finallyScopes.push(node.finallyBlock);
        }
        try {
            if (node.catchClause !== undefined) {
                const catchLabel = cfg.newLabel();
                const excFlag = this.m.newTemp(BOOLEAN_TYPE);
                cfg.branch(this.expr.truthyCondition(excFlag), catchLabel, tryLabel);

                cfg.placeLabel(tryLabel);
                this.lowerStatement(node.tryBlock);
                cfg.goto(joinLabel);

                cfg.placeLabel(catchLabel);
                const decl = node.catchClause.variableDeclaration;
                if (decl !== undefined && ts.isIdentifier(decl.name)) {
                    const caughtType =
                        decl.type !== undefined ? this.m.converter.convertTypeNode(decl.type) : UNKNOWN_TYPE;
                    const caught = this.m.localForIdentifier(decl.name, caughtType);
                    cfg.emit({
                        _: "AssignStmt",
                        left: caught,
                        right: { _: "CaughtExceptionRef", type: caughtType },
                    });
                }
                this.lowerStatement(node.catchClause.block);
                cfg.goto(joinLabel);
            } else {
                cfg.goto(tryLabel);
                cfg.placeLabel(tryLabel);
                this.lowerStatement(node.tryBlock);
                cfg.goto(joinLabel);
            }
        } finally {
            if (node.finallyBlock !== undefined) {
                this.finallyScopes.pop();
            }
        }

        // Normal (fall-through) path.
        cfg.placeLabel(joinLabel);
        if (node.finallyBlock !== undefined) {
            this.lowerStatement(node.finallyBlock);
        }
    }

    /**
     * Emit copies of the enclosing finally blocks with stack depth > `downTo`,
     * innermost first — used before abrupt exits (return/throw/break/continue).
     * While a finally body is being emitted, its own scope (and deeper ones) is
     * masked so a nested abrupt exit only re-runs the OUTER finallies.
     */
    private emitFinallies(downTo: number): void {
        for (let i = this.finallyScopes.length - 1; i >= downTo; i--) {
            const masked = this.finallyScopes.splice(i);
            try {
                this.lowerStatement(masked[0]);
            } finally {
                this.finallyScopes.push(...masked);
            }
        }
    }

    private inBreakable(context: BreakableContext, body: () => void): void {
        context.finallyDepth = this.finallyScopes.length;
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
            // Destructuring: evaluate the initializer into a temp, then unpack.
            if (decl.initializer === undefined) {
                throw new LoweringError("destructuring declaration without initializer");
            }
            const source = this.expr.lowerToLocal(decl.initializer);
            this.lowerBindingPattern(decl.name, source);
            return;
        }
        const declaredType =
            decl.type !== undefined
                ? this.m.converter.convertTypeNode(decl.type)
                : this.m.converter.typeOfNode(decl.name);
        const local = this.m.moduleFieldForIdentifier(decl.name)
            ?? this.m.localForIdentifier(decl.name, declaredType);
        if (decl.initializer !== undefined) {
            const value = this.expr.lowerExpr(decl.initializer);
            this.m.cfg.emit({ _: "AssignStmt", left: local, right: value });
        }
    }

    /** A function declared inside a method body -> hoisted lexical closure. */
    private lowerNestedFunction(decl: ts.FunctionDeclaration): void {
        if (decl.name === undefined || decl.body === undefined) {
            return;
        }
        const closure = this.expr.lowerFunctionDeclaration(decl);
        const binding = this.m.localForIdentifier(decl.name, closure.type);
        this.m.cfg.emit({ _: "AssignStmt", left: binding, right: closure });
    }

    // ------------------------------------------------------------------
    // Destructuring
    // ------------------------------------------------------------------

    /** Unpack one pattern parameter after its `%patN := ParameterRef(i)` prologue binding. */
    lowerParameterBindingPattern(pattern: ts.BindingPattern, source: LocalDto): void {
        this.lowerBindingPattern(pattern, source);
    }

    /** `{a, b: {c}, d = 1}` / `[x, , y]` unpacked from `source` via field/array refs. */
    private lowerBindingPattern(pattern: ts.BindingPattern, source: LocalDto): void {
        if (ts.isObjectBindingPattern(pattern)) {
            for (const element of pattern.elements) {
                if (element.dotDotDotToken !== undefined) {
                    throw new LoweringError("rest element in object destructuring");
                }
                const propName =
                    element.propertyName !== undefined
                        ? propertyNameText(element.propertyName)
                        : ts.isIdentifier(element.name)
                          ? element.name.text
                          : undefined;
                if (propName === undefined) {
                    throw new LoweringError("computed property in destructuring");
                }
                const ref: ValueDto = {
                    _: "InstanceFieldRef",
                    instance: source,
                    field: {
                        declaringClass:
                            source.type._ === "ClassType" ? source.type.signature : UNKNOWN_CLASS_SIGNATURE,
                        name: propName,
                        type: this.bindingType(element.name),
                    },
                };
                this.bindDestructured(element.name, ref, element.initializer);
            }
            return;
        }
        // Array pattern.
        pattern.elements.forEach((element, index) => {
            if (ts.isOmittedExpression(element)) {
                return;
            }
            if (element.dotDotDotToken !== undefined) {
                throw new LoweringError("rest element in array destructuring");
            }
            const ref: ValueDto = {
                _: "ArrayRef",
                array: source,
                index: constant(String(index), NUMBER_TYPE),
                type: this.bindingType(element.name),
            };
            this.bindDestructured(element.name, ref, element.initializer);
        });
    }

    private bindingType(name: ts.BindingName): TypeDto {
        return ts.isIdentifier(name) ? this.m.converter.typeOfNode(name) : UNKNOWN_TYPE;
    }

    private bindDestructured(target: ts.BindingName, ref: ValueDto, defaultInit: ts.Expression | undefined): void {
        if (ts.isIdentifier(target)) {
            const type = this.m.converter.typeOfNode(target);
            const destination = this.m.moduleFieldForIdentifier(target)
                ?? this.m.localForIdentifier(target, type);
            this.m.cfg.emit({ _: "AssignStmt", left: destination, right: ref });
            if (defaultInit !== undefined) {
                this.emitDefaultValue(destination, type, defaultInit);
            }
            return;
        }
        // Nested pattern: unpack through a temp.
        const temp = this.m.newTemp(UNKNOWN_TYPE);
        this.m.cfg.emit({ _: "AssignStmt", left: temp, right: ref });
        if (defaultInit !== undefined) {
            this.emitDefaultValue(temp, UNKNOWN_TYPE, defaultInit);
        }
        this.lowerBindingPattern(target, temp);
    }

    /** `if (target === undefined) target := <default>` */
    private emitDefaultValue(target: LValueDto, type: TypeDto, defaultInit: ts.Expression): void {
        const cfg = this.m.cfg;
        const setLabel = cfg.newLabel();
        const doneLabel = cfg.newLabel();
        const tested = target._ === "Local" ? target : this.expr.materialize(target, type);
        cfg.branch(
            this.expr.relation("===", tested, constant("undefined", UNDEFINED_TYPE)),
            setLabel,
            doneLabel,
        );
        cfg.placeLabel(setLabel);
        cfg.emit({ _: "AssignStmt", left: target, right: this.expr.lowerExpr(defaultInit) });
        cfg.goto(doneLabel);
        cfg.placeLabel(doneLabel);
    }
}

/** Whether [node] mentions the identifier [name] anywhere in its subtree. */
function referencesName(node: ts.Node, name: string): boolean {
    let found = false;
    const visit = (current: ts.Node): void => {
        if (found) return;
        if (ts.isIdentifier(current) && current.text === name) {
            found = true;
            return;
        }
        ts.forEachChild(current, visit);
    };
    visit(node);
    return found;
}

function propertyNameText(name: ts.PropertyName): string | undefined {
    if (ts.isIdentifier(name) || ts.isStringLiteral(name) || ts.isNumericLiteral(name)) {
        return name.text;
    }
    return undefined;
}

function unknownMethod(name: string): MethodSignatureDto {
    return {
        declaringClass: UNKNOWN_CLASS_SIGNATURE,
        name,
        parameters: [],
        returnType: UNKNOWN_TYPE,
    };
}
