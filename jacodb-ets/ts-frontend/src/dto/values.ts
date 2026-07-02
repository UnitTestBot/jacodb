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
 * Value DTOs. Mirror `org.jacodb.ets.dto` values
 * (jacodb-ets/src/main/kotlin/org/jacodb/ets/dto/Values.kt).
 *
 * Serialized polymorphically with the "_" discriminator.
 * Unknown discriminators fall back to RawValueDto on the Kotlin side
 * (RawValue REQUIRES a "type" key).
 *
 * IMPORTANT: only fields that are Kotlin constructor parameters are serialized.
 * Computed `type` getters (e.g. on NewExpr, UnopExpr, call exprs) must NOT be emitted —
 * the strict Kotlin Json would reject the extra key.
 */

import { BinaryOp, RelationOp, UnaryOp } from "./ops";
import { FieldSignatureDto, MethodSignatureDto } from "./signatures";
import { TypeDto } from "./types";

/**
 * Closed union of the known value kinds.
 * Raw fallback values (RawValueDto) are intentionally NOT part of this union:
 * they carry an index signature that would break discriminated-union narrowing.
 * Emission sites must cast them explicitly (see lowering/fallback.ts).
 */
export type ValueDto = ImmediateDto | ExprDto | RefDto;

export type ImmediateDto = LocalDto | ConstantDto;

export type ExprDto =
    | NewExprDto
    | NewArrayExprDto
    | DeleteExprDto
    | AwaitExprDto
    | YieldExprDto
    | TypeOfExprDto
    | InstanceOfExprDto
    | CastExprDto
    | UnopExprDto
    | BinopExprDto
    | ConditionExprDto
    | InstanceCallExprDto
    | StaticCallExprDto
    | PtrCallExprDto;

export type CallExprDto = InstanceCallExprDto | StaticCallExprDto | PtrCallExprDto;

export type RefDto =
    | ThisRefDto
    | ParameterRefDto
    | CaughtExceptionRefDto
    | GlobalRefDto
    | ClosureFieldRefDto
    | ArrayRefDto
    | InstanceFieldRefDto
    | StaticFieldRefDto;

/** LValue kinds accepted by Kotlin Convert as the LHS of AssignStmt. */
export type LValueDto = LocalDto | ArrayRefDto | InstanceFieldRefDto | StaticFieldRefDto;

/** Fallback for constructs we cannot model; Kotlin deserializes any unknown kind into RawValueDto. */
export interface RawValueDto {
    readonly _: string; // any kind not listed above
    type: TypeDto; // REQUIRED by RawValueSerializer
    [extra: string]: unknown;
}

export interface LocalDto {
    readonly _: "Local";
    name: string;
    type: TypeDto;
}

/** All constants carry a string-encoded value (e.g. "42", "true", "null"). */
export interface ConstantDto {
    readonly _: "Constant";
    value: string;
    type: TypeDto;
}

export interface NewExprDto {
    readonly _: "NewExpr";
    classType: TypeDto; // ClassType
}

export interface NewArrayExprDto {
    readonly _: "NewArrayExpr";
    elementType: TypeDto;
    size: ValueDto;
}

export interface DeleteExprDto {
    readonly _: "DeleteExpr";
    arg: ValueDto;
}

export interface AwaitExprDto {
    readonly _: "AwaitExpr";
    arg: ValueDto;
}

export interface YieldExprDto {
    readonly _: "YieldExpr";
    arg: ValueDto;
}

export interface TypeOfExprDto {
    readonly _: "TypeOfExpr";
    arg: ValueDto;
}

export interface InstanceOfExprDto {
    readonly _: "InstanceOfExpr";
    arg: ValueDto;
    checkType: TypeDto;
}

export interface CastExprDto {
    readonly _: "CastExpr";
    arg: ValueDto;
    type: TypeDto;
}

export interface UnopExprDto {
    readonly _: "UnopExpr";
    op: UnaryOp;
    arg: ValueDto;
}

export interface BinopExprDto {
    readonly _: "BinopExpr";
    op: BinaryOp;
    left: ValueDto;
    right: ValueDto;
    type?: TypeDto; // Kotlin default: UnknownType
}

export interface ConditionExprDto {
    readonly _: "ConditionExpr";
    op: RelationOp;
    left: ValueDto;
    right: ValueDto;
    type?: TypeDto; // Kotlin default: UnknownType
}

export interface InstanceCallExprDto {
    readonly _: "InstanceCallExpr";
    instance: LocalDto; // Kotlin Convert casts this to LocalDto — MUST be a Local
    method: MethodSignatureDto;
    args: ValueDto[];
}

export interface StaticCallExprDto {
    readonly _: "StaticCallExpr";
    method: MethodSignatureDto;
    args: ValueDto[];
}

export interface PtrCallExprDto {
    readonly _: "PtrCallExpr";
    ptr: ValueDto; // Local or FieldRef (must be a value, not an expr)
    method: MethodSignatureDto;
    args: ValueDto[];
}

export interface ThisRefDto {
    readonly _: "ThisRef";
    type: TypeDto; // ClassType
}

export interface ParameterRefDto {
    readonly _: "ParameterRef";
    index: number;
    type: TypeDto;
}

export interface CaughtExceptionRefDto {
    readonly _: "CaughtExceptionRef";
    type: TypeDto;
}

export interface GlobalRefDto {
    readonly _: "GlobalRef";
    name: string;
    ref: ValueDto | null; // nullable WITHOUT default on the Kotlin side — must be present
}

export interface ClosureFieldRefDto {
    readonly _: "ClosureFieldRef";
    base: LocalDto;
    fieldName: string;
    type: TypeDto;
}

export interface ArrayRefDto {
    readonly _: "ArrayRef";
    array: ValueDto;
    index: ValueDto; // must be a value (immediate/ref), not an expr — Kotlin casts to EtsValue
    type: TypeDto;
}

export interface InstanceFieldRefDto {
    readonly _: "InstanceFieldRef";
    instance: LocalDto; // Kotlin Convert casts this to LocalDto — MUST be a Local
    field: FieldSignatureDto;
}

export interface StaticFieldRefDto {
    readonly _: "StaticFieldRef";
    field: FieldSignatureDto;
}
