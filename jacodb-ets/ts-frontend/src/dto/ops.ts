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
 * Operator strings accepted by the Kotlin side.
 * Mirrors `org.jacodb.ets.dto.Ops` (jacodb-ets/src/main/kotlin/org/jacodb/ets/dto/Ops.kt).
 * Any other operator string makes Kotlin `Convert.kt` fail hard.
 */

export const UNARY_OPS = ["+", "-", "!", "~", "++", "--"] as const;
export type UnaryOp = (typeof UNARY_OPS)[number];

export const BINARY_OPS = [
    "+", "-", "*", "/", "%", "**",
    "<<", ">>", ">>>",
    "&", "|", "^",
    "&&", "||", "??",
] as const;
export type BinaryOp = (typeof BINARY_OPS)[number];

export const RELATION_OPS = ["==", "!=", "===", "!==", "<", "<=", ">", ">=", "in"] as const;
export type RelationOp = (typeof RELATION_OPS)[number];

export function isUnaryOp(op: string): op is UnaryOp {
    return (UNARY_OPS as readonly string[]).includes(op);
}

export function isBinaryOp(op: string): op is BinaryOp {
    return (BINARY_OPS as readonly string[]).includes(op);
}

export function isRelationOp(op: string): op is RelationOp {
    return (RELATION_OPS as readonly string[]).includes(op);
}
