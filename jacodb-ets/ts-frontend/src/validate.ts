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
 * Invariant checker for emitted EtsFileDto.
 *
 * Verifies the structural contracts required by the Kotlin side
 * (`EtsFileDto.loadFromJson` + `Convert.kt` + `EtsBlockCfg` init + `Linearize.kt`)
 * BEFORE anything is written to disk. Violations returned as human-readable strings;
 * an empty list means the file is safe to hand over to Kotlin.
 */

import { FORBIDDEN_LOCAL_PREFIX } from "./dto/constants";
import { isBinaryOp, isRelationOp, isUnaryOp } from "./dto/ops";
import { BodyDto, ClassDto, EtsFileDto, MethodDto, NamespaceDto } from "./dto/model";
import { StmtDto } from "./dto/stmts";
import { ValueDto } from "./dto/values";

const EXPR_KINDS = new Set([
    "NewExpr",
    "NewArrayExpr",
    "DeleteExpr",
    "AwaitExpr",
    "YieldExpr",
    "TypeOfExpr",
    "InstanceOfExpr",
    "CastExpr",
    "UnopExpr",
    "BinopExpr",
    "ConditionExpr",
    "InstanceCallExpr",
    "StaticCallExpr",
    "PtrCallExpr",
]);

const REF_KINDS = new Set([
    "ThisRef",
    "ParameterRef",
    "CaughtExceptionRef",
    "GlobalRef",
    "ClosureFieldRef",
    "ArrayRef",
    "InstanceFieldRef",
    "StaticFieldRef",
]);

const IMMEDIATE_KINDS = new Set(["Local", "Constant"]);

const KNOWN_VALUE_KINDS = new Set([...EXPR_KINDS, ...REF_KINDS, ...IMMEDIATE_KINDS]);

const KNOWN_STMT_KINDS = new Set([
    "NopStmt",
    "AssignStmt",
    "CallStmt",
    "ReturnVoidStmt",
    "ReturnStmt",
    "ThrowStmt",
    "IfStmt",
]);

const CALL_EXPR_KINDS = new Set(["InstanceCallExpr", "StaticCallExpr", "PtrCallExpr"]);

/** LValue kinds accepted by Kotlin Convert as AssignStmt.left (CastExpr is stripped there). */
const LVALUE_KINDS = new Set(["Local", "ClosureFieldRef", "ArrayRef", "InstanceFieldRef", "StaticFieldRef"]);

export function validateEtsFile(file: EtsFileDto): string[] {
    const errors: string[] = [];
    const ctx = file.signature.fileName;

    for (const clazz of file.classes) {
        validateClass(clazz, ctx, errors);
    }
    for (const ns of file.namespaces) {
        validateNamespace(ns, ctx, errors);
    }
    return errors;
}

function validateNamespace(ns: NamespaceDto, ctx: string, errors: string[]): void {
    const nsCtx = `${ctx}::${ns.signature.name}`;
    for (const clazz of ns.classes ?? []) {
        validateClass(clazz, nsCtx, errors);
    }
    for (const inner of ns.namespaces ?? []) {
        validateNamespace(inner, nsCtx, errors);
    }
}

function validateClass(clazz: ClassDto, ctx: string, errors: string[]): void {
    const classCtx = `${ctx}::${clazz.signature.name}`;
    if (clazz.superClassName === undefined) {
        errors.push(`${classCtx}: superClassName must be present (use "" for none)`);
    }
    for (const method of clazz.methods) {
        validateMethod(method, classCtx, errors);
    }
}

function validateMethod(method: MethodDto, ctx: string, errors: string[]): void {
    const methodCtx = `${ctx}::${method.signature.name}`;
    if (method.body !== undefined) {
        validateBody(method.body, methodCtx, errors);
    }
}

function validateBody(body: BodyDto, ctx: string, errors: string[]): void {
    const err = (msg: string) => errors.push(`${ctx}: ${msg}`);

    // --- Locals table ---
    const declaredLocals = new Set<string>();
    for (const local of body.locals) {
        if (declaredLocals.has(local.name)) {
            err(`duplicate local '${local.name}' in body.locals`);
        }
        declaredLocals.add(local.name);
        if (local.name.startsWith(FORBIDDEN_LOCAL_PREFIX)) {
            err(`local '${local.name}' uses reserved prefix '${FORBIDDEN_LOCAL_PREFIX}'`);
        }
    }

    // --- Blocks: ids, successors, terminators ---
    const blocks = body.cfg.blocks;
    const blockCount = blocks.length;
    blocks.forEach((block, index) => {
        const blockCtx = `block ${block.id}`;
        if (block.id !== index) {
            err(`${blockCtx}: id must equal its index ${index}`);
        }
        for (const succ of block.successors) {
            if (succ < 0 || succ >= blockCount) {
                err(`${blockCtx}: successor ${succ} out of range [0, ${blockCount})`);
            }
        }
        for (const pred of block.predecessors ?? []) {
            if (pred < 0 || pred >= blockCount) {
                err(`${blockCtx}: predecessor ${pred} out of range [0, ${blockCount})`);
            }
        }

        block.stmts.forEach((stmt, stmtIndex) => {
            const isLast = stmtIndex === block.stmts.length - 1;
            if (!isLast && isTerminator(stmt)) {
                err(`${blockCtx}: terminator '${stmt._}' at position ${stmtIndex} is not the last stmt`);
            }
            validateStmt(stmt, `${blockCtx}, stmt ${stmtIndex}`, declaredLocals, err);
        });

        const last = block.stmts.length > 0 ? block.stmts[block.stmts.length - 1] : undefined;
        if (last !== undefined && last._ === "IfStmt") {
            if (block.successors.length !== 2) {
                err(`${blockCtx}: ends with IfStmt but has ${block.successors.length} successors (need exactly 2: [false, true])`);
            }
        } else if (last !== undefined && (last._ === "ReturnStmt" || last._ === "ReturnVoidStmt" || last._ === "ThrowStmt")) {
            if (block.successors.length !== 0) {
                err(`${blockCtx}: ends with '${last._}' but has ${block.successors.length} successors`);
            }
        } else {
            if (block.successors.length > 1) {
                err(`${blockCtx}: non-branching block has ${block.successors.length} successors (max 1)`);
            }
        }
    });

    // --- Optional source-origin side table ---
    const originKeys = new Set<string>();
    for (const origin of body.stmtOrigins ?? []) {
        const key = `${origin.blockId}:${origin.stmtIndex}`;
        if (originKeys.has(key)) {
            err(`duplicate source origin for block ${origin.blockId}, stmt ${origin.stmtIndex}`);
        }
        originKeys.add(key);

        const block = blocks[origin.blockId];
        if (block === undefined) {
            err(`source origin references block ${origin.blockId} outside [0, ${blockCount})`);
        } else if (origin.stmtIndex < 0 || origin.stmtIndex >= block.stmts.length) {
            err(
                `source origin references stmt ${origin.stmtIndex} outside block ${origin.blockId} ` +
                    `[0, ${block.stmts.length})`,
            );
        }

        const source = origin.source;
        if (source.startOffset < 0 || source.endOffset < source.startOffset) {
            err(`source origin ${key} has invalid offset range [${source.startOffset}, ${source.endOffset})`);
        }
        if (
            source.startLine < 0 ||
            source.startColumn < 0 ||
            source.endLine < source.startLine ||
            source.endColumn < 0 ||
            (source.endLine === source.startLine && source.endColumn < source.startColumn)
        ) {
            err(
                `source origin ${key} has invalid line/column range ` +
                    `${source.startLine}:${source.startColumn}-${source.endLine}:${source.endColumn}`,
            );
        }
    }

    // --- Predecessor/successor consistency (when predecessors are emitted) ---
    if (blocks.length > 0 && blocks.every((b) => b.predecessors !== undefined)) {
        const expectedPreds = new Map<number, Set<number>>();
        blocks.forEach((b) => expectedPreds.set(b.id, new Set()));
        for (const b of blocks) {
            for (const succ of b.successors) {
                expectedPreds.get(succ)?.add(b.id);
            }
        }
        for (const b of blocks) {
            const actual = new Set(b.predecessors);
            const expected = expectedPreds.get(b.id)!;
            if (actual.size !== expected.size || [...expected].some((p) => !actual.has(p))) {
                err(`block ${b.id}: predecessors [${[...actual]}] inconsistent with successors (expected [${[...expected]}])`);
            }
        }
    }
}

function isTerminator(stmt: StmtDto): boolean {
    return stmt._ === "ReturnVoidStmt" || stmt._ === "ReturnStmt" || stmt._ === "ThrowStmt" || stmt._ === "IfStmt";
}

function validateStmt(
    stmt: StmtDto,
    ctx: string,
    declaredLocals: Set<string>,
    err: (msg: string) => void,
): void {
    // Raw fallback values are ONLY legal as the RHS of a Local assignment:
    // Kotlin's ensureOneAddress rejects EtsRawEntity in every other position.
    // Kotlin Convert strips a CastExpr on the LHS before that check, so
    // `CastExpr(Local) := <raw>` is a Local assignment too — mirror that here.
    const effectiveLeft =
        stmt._ === "AssignStmt" ? (stmt.left._ === "CastExpr" ? stmt.left.arg : stmt.left) : undefined;
    const rawAllowedFor =
        stmt._ === "AssignStmt" && effectiveLeft!._ === "Local" ? stmt.right : undefined;
    const values = stmtOperands(stmt);
    for (const value of values) {
        validateValue(value, ctx, declaredLocals, err, value === rawAllowedFor);
    }

    switch (stmt._) {
        case "AssignStmt": {
            let left = stmt.left;
            if (left._ === "CastExpr") {
                left = left.arg; // Kotlin Convert strips a cast on the LHS
            }
            if (!LVALUE_KINDS.has(left._)) {
                err(`${ctx}: AssignStmt.left has kind '${left._}', expected one of ${[...LVALUE_KINDS].join("/")}`);
            }
            break;
        }
        case "CallStmt": {
            if (!CALL_EXPR_KINDS.has(stmt.expr._)) {
                err(`${ctx}: CallStmt.expr has kind '${stmt.expr._}', expected a call expr`);
            }
            break;
        }
        case "IfStmt": {
            if (stmt.condition._ !== "ConditionExpr") {
                err(`${ctx}: IfStmt.condition has kind '${(stmt.condition as ValueDto)._}', expected ConditionExpr`);
            }
            break;
        }
        default:
            break;
    }
}

/** Direct value operands of a statement (non-recursive). */
function stmtOperands(stmt: StmtDto): ValueDto[] {
    switch (stmt._) {
        case "AssignStmt":
            return [stmt.left, stmt.right];
        case "CallStmt":
            return [stmt.expr];
        case "ReturnStmt":
        case "ThrowStmt":
            return [stmt.arg];
        case "IfStmt":
            return [stmt.condition];
        default:
            return [];
    }
}

function validateValue(
    value: ValueDto,
    ctx: string,
    declaredLocals: Set<string>,
    err: (msg: string) => void,
    rawAllowed: boolean = false,
): void {
    if (!KNOWN_VALUE_KINDS.has(value._)) {
        // Raw fallback value: Kotlin's RawValueSerializer requires a "type" key.
        if ((value as { type?: unknown }).type === undefined) {
            err(`${ctx}: raw value of kind '${value._}' is missing required 'type'`);
        }
        if (!rawAllowed) {
            err(
                `${ctx}: raw value of kind '${value._}' in an operand position — ` +
                    `raw values are only legal as the RHS of a Local assignment`,
            );
        }
        return; // do not recurse into unknown shapes
    }

    switch (value._) {
        case "Local":
            if (!declaredLocals.has(value.name)) {
                err(`${ctx}: local '${value.name}' is not declared in body.locals`);
            }
            if (value.name.startsWith(FORBIDDEN_LOCAL_PREFIX)) {
                err(`${ctx}: local '${value.name}' uses reserved prefix '${FORBIDDEN_LOCAL_PREFIX}'`);
            }
            break;
        case "UnopExpr":
            if (!isUnaryOp(value.op)) {
                err(`${ctx}: unknown unary op '${value.op}'`);
            }
            break;
        case "BinopExpr":
            if (!isBinaryOp(value.op)) {
                err(`${ctx}: unknown binary op '${value.op}'`);
            }
            break;
        case "ConditionExpr":
            if (!isRelationOp(value.op)) {
                err(`${ctx}: unknown relation op '${value.op}'`);
            }
            break;
        case "InstanceCallExpr":
            if (value.instance._ !== "Local") {
                err(`${ctx}: InstanceCallExpr.instance has kind '${value.instance._}', must be Local`);
            }
            break;
        case "InstanceFieldRef":
            if (value.instance._ !== "Local") {
                err(`${ctx}: InstanceFieldRef.instance has kind '${value.instance._}', must be Local`);
            }
            break;
        case "ArrayRef":
            if (EXPR_KINDS.has(value.index._)) {
                err(`${ctx}: ArrayRef.index has expr kind '${value.index._}', must be an immediate or ref`);
            }
            break;
        case "ClosureFieldRef":
            if (!declaredLocals.has(value.base.name)) {
                err(`${ctx}: closure environment '${value.base.name}' is not declared in body.locals`);
            }
            break;
        case "PtrCallExpr":
            // Kotlin Convert casts ptr to EtsValue — expr kinds would throw a ClassCastException.
            if (EXPR_KINDS.has(value.ptr._)) {
                err(`${ctx}: PtrCallExpr.ptr has expr kind '${value.ptr._}', must be an immediate or ref`);
            }
            break;
        default:
            break;
    }

    for (const child of valueOperands(value)) {
        validateValue(child, ctx, declaredLocals, err);
    }
}

/** Direct value operands of a value (non-recursive). */
export function valueOperands(value: ValueDto): ValueDto[] {
    switch (value._) {
        case "NewArrayExpr":
            return [value.size];
        case "DeleteExpr":
        case "AwaitExpr":
        case "YieldExpr":
        case "TypeOfExpr":
        case "InstanceOfExpr":
        case "CastExpr":
        case "UnopExpr":
            return [value.arg];
        case "BinopExpr":
        case "ConditionExpr":
            return [value.left, value.right];
        case "InstanceCallExpr":
            return [value.instance, ...value.args];
        case "StaticCallExpr":
            return [...value.args];
        case "PtrCallExpr":
            return [value.ptr, ...value.args];
        case "GlobalRef":
            return value.ref !== null ? [value.ref] : [];
        case "ClosureFieldRef":
            // base is a concrete LocalDto on the wire (without a discriminator)
            // and was checked directly in validateValue.
            return [];
        case "ArrayRef":
            return [value.array, value.index];
        case "InstanceFieldRef":
            return [value.instance];
        default:
            return [];
    }
}
