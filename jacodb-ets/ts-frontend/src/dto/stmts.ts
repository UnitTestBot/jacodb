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
 * Statement DTOs. Mirror `org.jacodb.ets.dto` statements
 * (jacodb-ets/src/main/kotlin/org/jacodb/ets/dto/Stmts.kt).
 *
 * Serialized polymorphically with the "_" discriminator.
 * Unknown discriminators fall back to RawStmtDto on the Kotlin side.
 */

import { CallExprDto, ConditionExprDto, ValueDto } from "./values";

/**
 * Closed union of the known statement kinds.
 * Raw fallback statements (RawStmtDto) are intentionally NOT part of this union
 * (index signature breaks narrowing); emission sites cast them explicitly.
 */
export type StmtDto =
    | NopStmtDto
    | AssignStmtDto
    | CallStmtDto
    | ReturnVoidStmtDto
    | ReturnStmtDto
    | ThrowStmtDto
    | IfStmtDto;

/** Fallback for constructs we cannot model. */
export interface RawStmtDto {
    readonly _: string; // any kind not listed below
    [extra: string]: unknown;
}

export interface NopStmtDto {
    readonly _: "NopStmt";
}

export interface AssignStmtDto {
    readonly _: "AssignStmt";
    left: ValueDto; // must convert to Local / FieldRef / ArrayRef on the Kotlin side
    right: ValueDto;
}

export interface CallStmtDto {
    readonly _: "CallStmt";
    expr: CallExprDto;
}

export interface ReturnVoidStmtDto {
    readonly _: "ReturnVoidStmt";
}

export interface ReturnStmtDto {
    readonly _: "ReturnStmt";
    arg: ValueDto;
}

export interface ThrowStmtDto {
    readonly _: "ThrowStmt";
    arg: ValueDto;
}

export interface IfStmtDto {
    readonly _: "IfStmt";
    condition: ConditionExprDto;
}

export const RETURN_VOID_STMT: ReturnVoidStmtDto = { _: "ReturnVoidStmt" };

/** Statements that must terminate a basic block (no stmts may follow them). */
export function isTerminatorStmt(stmt: StmtDto): boolean {
    return (
        stmt._ === "ReturnVoidStmt" ||
        stmt._ === "ReturnStmt" ||
        stmt._ === "ThrowStmt" ||
        stmt._ === "IfStmt"
    );
}
