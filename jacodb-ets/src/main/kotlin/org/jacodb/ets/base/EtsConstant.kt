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

interface EtsConstant : EtsImmediate

data class EtsStringConstant(
    val value: String,
) : EtsConstant {
    override val type: EtsType
        get() = EtsStringType

    override fun toString(): String {
        return "\"$value\""
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsBooleanConstant(
    val value: Boolean,
) : EtsConstant {
    override val type: EtsType
        get() = EtsBooleanType

    override fun toString(): String {
        return if (value) "true" else "false"
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }

    companion object {
        val TRUE = EtsBooleanConstant(true)
        val FALSE = EtsBooleanConstant(false)
    }
}

data class EtsNumberConstant(
    val value: Double,
) : EtsConstant {
    override val type: EtsType
        get() = EtsNumberType

    override fun toString(): String {
        return value.toString()
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object EtsNullConstant : EtsConstant {
    override val type: EtsType
        get() = EtsNullType

    override fun toString(): String = "null"

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object EtsUndefinedConstant : EtsConstant {
    override val type: EtsType
        get() = EtsUndefinedType

    override fun toString(): String = "undefined"

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsArrayLiteral(
    val elements: List<EtsEntity>,
    override val type: EtsType,
) : EtsConstant {

    init {
        if (type is EtsArrayType) {
            require(type.dimensions == 1) {
                "Array type of array literal must have exactly one dimension"
            }
        }
    }

    override fun toString(): String {
        return elements.joinToString(prefix = "[", postfix = "]")
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// TODO: see TypeScript reference for different types of elements inside object literals, such as property assignment, shorthand property assignment, method definition, spread assignment, and getter/setter accessors.
data class EtsObjectLiteral(
    val properties: List<Pair<String, EtsEntity>>,
    override val type: EtsType,
) : EtsConstant {
    override fun toString(): String {
        return properties.joinToString(prefix = "{", postfix = "}") { (name, value) ->
            "$name: $value"
        }
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}
