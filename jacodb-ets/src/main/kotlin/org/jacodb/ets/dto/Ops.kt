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

package org.jacodb.ets.dto

object Ops {
    object Unary {
        const val PLUS = "+"
        const val MINUS = "-"
        const val NOT = "!"
        const val BIT_NOT = "~"
        const val INC = "++"
        const val DEC = "--"
        // const val DELETE = "delete"
        // const val TYPEOF = "typeof"
        // const val VOID = "void"
    }

    object Binary {
        const val ADD = "+"
        const val SUB = "-"
        const val MUL = "*"
        const val DIV = "/"
        const val MOD = "%"
        const val EXP = "**"
        const val LSH = "<<"
        const val RSH = ">>"
        const val URSH = ">>>"
        const val BIT_AND = "&"
        const val BIT_OR = "|"
        const val BIT_XOR = "^"
        const val AND = "&&"
        const val OR = "||"
        const val NULLISH = "??"
        // const val COMMA = ","
        // const val AS = "as"
    }

    object Relational {
        const val EQ = "=="
        const val NOT_EQ = "!="
        const val STRICT_EQ = "==="
        const val STRICT_NOT_EQ = "!=="
        const val LT = "<"
        const val LT_EQ = "<="
        const val GT = ">"
        const val GT_EQ = ">="
        const val IN = "in"
        // const val INSTANCEOF = "instanceof"
    }
}
