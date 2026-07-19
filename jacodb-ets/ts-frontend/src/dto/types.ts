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
 * Type DTOs. Mirror `org.jacodb.ets.dto` types
 * (jacodb-ets/src/main/kotlin/org/jacodb/ets/dto/Types.kt).
 *
 * Serialized polymorphically with the "_" discriminator.
 * Unknown discriminators fall back to RawTypeDto on the Kotlin side.
 *
 * IMPORTANT: the Kotlin Json is strict — no extra keys are allowed on known kinds.
 * Optional fields here correspond to Kotlin constructor parameters with defaults.
 */

import { ClassSignatureDto, LocalSignatureDto, MethodSignatureDto } from "./signatures";

export type TypeDto =
    | AnyTypeDto
    | UnknownTypeDto
    | GenericTypeDto
    | AliasTypeDto
    | LexicalEnvTypeDto
    | EnumValueTypeDto
    | VoidTypeDto
    | NeverTypeDto
    | UnionTypeDto
    | IntersectionTypeDto
    | BooleanTypeDto
    | NumberTypeDto
    | StringTypeDto
    | NullTypeDto
    | UndefinedTypeDto
    | LiteralTypeDto
    | ClassTypeDto
    | UnclearReferenceTypeDto
    | ArrayTypeDto
    | TupleTypeDto
    | FunctionTypeDto;

export interface AnyTypeDto {
    readonly _: "AnyType";
}

export interface UnknownTypeDto {
    readonly _: "UnknownType";
}

export interface GenericTypeDto {
    readonly _: "GenericType";
    name: string;
    constraint?: TypeDto;
    defaultType?: TypeDto;
}

export interface AliasTypeDto {
    readonly _: "AliasType";
    name: string;
    originalType: TypeDto;
    signature: LocalSignatureDto;
}

export interface LexicalEnvTypeDto {
    readonly _: "LexicalEnvType";
    method: MethodSignatureDto;
    // This list is statically List<LocalDto> on the Kotlin side, so entries do
    // not carry the polymorphic "_": "Local" discriminator.
    closures: LexicalEnvLocalDto[];
}

export interface LexicalEnvLocalDto {
    name: string;
    type: TypeDto;
}

export interface EnumValueTypeDto {
    readonly _: "EnumValueType";
    signature: ClassSignatureDto;
    name?: string;
}

export interface VoidTypeDto {
    readonly _: "VoidType";
}

export interface NeverTypeDto {
    readonly _: "NeverType";
}

export interface UnionTypeDto {
    readonly _: "UnionType";
    types: TypeDto[];
}

export interface IntersectionTypeDto {
    readonly _: "IntersectionType";
    types: TypeDto[];
}

export interface BooleanTypeDto {
    readonly _: "BooleanType";
}

export interface NumberTypeDto {
    readonly _: "NumberType";
}

export interface StringTypeDto {
    readonly _: "StringType";
}

export interface NullTypeDto {
    readonly _: "NullType";
}

export interface UndefinedTypeDto {
    readonly _: "UndefinedType";
}

/** The literal is a BARE JSON primitive (string | number | boolean), not an object. */
export interface LiteralTypeDto {
    readonly _: "LiteralType";
    literal: string | number | boolean;
}

export interface ClassTypeDto {
    readonly _: "ClassType";
    signature: ClassSignatureDto;
    typeParameters?: TypeDto[]; // Kotlin default: empty list
}

export interface UnclearReferenceTypeDto {
    readonly _: "UnclearReferenceType";
    name: string;
    typeParameters?: TypeDto[]; // Kotlin default: empty list
}

export interface ArrayTypeDto {
    readonly _: "ArrayType";
    elementType: TypeDto;
    dimensions: number;
}

export interface TupleTypeDto {
    readonly _: "TupleType";
    types: TypeDto[];
}

export interface FunctionTypeDto {
    readonly _: "FunctionType";
    signature: MethodSignatureDto;
    typeParameters?: TypeDto[]; // Kotlin default: empty list
}

// Singletons for field-less types.
export const ANY_TYPE: AnyTypeDto = { _: "AnyType" };
export const UNKNOWN_TYPE: UnknownTypeDto = { _: "UnknownType" };
export const VOID_TYPE: VoidTypeDto = { _: "VoidType" };
export const NEVER_TYPE: NeverTypeDto = { _: "NeverType" };
export const BOOLEAN_TYPE: BooleanTypeDto = { _: "BooleanType" };
export const NUMBER_TYPE: NumberTypeDto = { _: "NumberType" };
export const STRING_TYPE: StringTypeDto = { _: "StringType" };
export const NULL_TYPE: NullTypeDto = { _: "NullType" };
export const UNDEFINED_TYPE: UndefinedTypeDto = { _: "UndefinedType" };
