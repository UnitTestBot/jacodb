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

package org.jacodb.ets.base

enum class UnaryOp {
    /**
     * '-'
     */
    Minus,

    /**
     * '+'
     */
    Plus,

    /**
     * '!'
     */
    Bang,

    /**
     * '~'
     */
    Tilde,

    /**
     * 'typeof'
     */
    Typeof,

    /**
     * 'void'
     */
    Void,

    /**
     * 'delete'
     */
    Delete,
}

enum class UpdateOp {
    /**
     * '++'
     */
    Inc,

    /**
     * '--'
     */
    Dec,
}

interface BinaryOp {

    companion object {
        fun fromString(value: String): BinaryOp {
            return RelationOp.fromString(value)
                ?: ArithOp.fromString(value)
                ?: LogicalOp.fromString(value)
                ?: BitOp.fromString(value)
                ?: NullishCoalescing.fromString(value)
                ?: error("Unknown BinaryOp: $value")
        }
    }
}

enum class RelationOp(private val str: String) : BinaryOp {

    EqEq("=="),
    NotEq("!="),
    EqEqEq("==="),
    NotEqEq("!=="),
    Lt("<"),
    LtEq("<="),
    Gt(">"),
    GtEq(">=");

    companion object {
        fun fromString(value: String): RelationOp? = RelationOp.values().firstOrNull {it.str == value}
    }

    override fun toString(): String = str
}

enum class ArithOp(private val str: String) : BinaryOp {

    Add("+"),
    Sub("-"),
    Mul("*"),
    Div("/"),
    Mod("%"),
    Exp("**");

    companion object {
        fun fromString(value: String): ArithOp? = ArithOp.values().firstOrNull {it.str == value}
    }

    override fun toString(): String = str
}

enum class BitOp(private val str: String) : BinaryOp {

    LShift("<<"),
    RShift(">>"),
    ZeroFillRShift(">>>"),
    BitOr("|"),
    BitXor("^"),
    BitAnd("&");

    companion object {
        fun fromString(value: String): BitOp? = BitOp.values().firstOrNull {it.str == value}
    }

    override fun toString(): String = str
}

enum class LogicalOp(private val str: String) : BinaryOp {

    LogicalOr("||"),
    LogicalAnd("&&"),
    In("in"),
    InstanceOf("instanceof");

    companion object {
        fun fromString(value: String): LogicalOp? = LogicalOp.values().firstOrNull {it.str == value}
    }

    override fun toString(): String = str
}

object NullishCoalescing : BinaryOp {
    override fun toString(): String = "??"

    fun fromString(value: String): NullishCoalescing? = NullishCoalescing.takeIf { value == "??" }
}

enum class AssignOp {
    /**
     * '='
     */
    Assign,

    /**
     * '+='
     */
    AddAssign,

    /**
     * '-='
     */
    SubAssign,

    /**
     * '*='
     */
    MulAssign,

    /**
     * '/='
     */
    DivAssign,

    /**
     * '%='
     */
    ModAssign,

    /**
     * '<<='
     */
    LShiftAssign,

    /**
     * '>>='
     */
    RShiftAssign,

    /**
     * '>>>='
     */
    ZeroFillRShiftAssign,

    /**
     * '|='
     */
    BitOrAssign,

    /**
     * '^='
     */
    BitXorAssign,

    /**
     * '&='
     */
    BitAndAssign,

    /**
     * '**='
     */
    ExpAssign,

    /**
     * '&&='
     */
    AndAssign,

    /**
     * '||='
     */
    OrAssign,

    /**
     * '??='
     */
    NullishAssign,
}
