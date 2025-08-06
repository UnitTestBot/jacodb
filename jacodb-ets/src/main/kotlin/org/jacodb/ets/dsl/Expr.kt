/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jacodb.ets.dsl

sealed interface Expr

data class Local(val name: String) : Expr {
    override fun toString() = name
}

data class Parameter(val index: Int) : Expr {
    override fun toString() = "param($index)"
}

object ThisRef : Expr {
    override fun toString() = "this"
}

data class ConstantNumber(val value: Double) : Expr {
    override fun toString() = "const($value)"
}

data class ConstantBoolean(val value: Boolean) : Expr {
    override fun toString() = "const($value)"
}

data class ConstantString(val value: String) : Expr {
    override fun toString() = "const(\"$value\")"
}

enum class BinaryOperator {
    AND,  // &&
    OR,   // ||
    EQ,   // ==
    NEQ,  // !=
    EQQ,  // ===
    NEQQ, // !==
    LT,   // <
    LTE,  // <=
    GT,   // >
    GTE,  // >=
    ADD,  // +
    SUB,  // -
    MUL,  // *
    DIV,  // /
    REM,  // %
    // TODO: shift
}

data class BinaryExpr(
    val operator: BinaryOperator,
    val left: Expr,
    val right: Expr,
) : Expr {
    override fun toString() = "${operator.name.lowercase()}($left, $right)"
}

enum class UnaryOperator {
    NOT, NEG
}

data class UnaryExpr(
    val operator: UnaryOperator,
    val expr: Expr,
) : Expr {
    override fun toString() = "${operator.name.lowercase()}($expr)"
}
