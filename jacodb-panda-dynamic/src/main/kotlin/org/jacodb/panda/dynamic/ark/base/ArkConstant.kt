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

package org.jacodb.panda.dynamic.ark.base

interface ArkConstant : ArkImmediate {
    interface Visitor<out R> {
        fun visit(value: ArkStringConstant): R
        fun visit(value: ArkBooleanConstant): R
        fun visit(value: ArkNumberConstant): R
        fun visit(value: ArkNullConstant): R
        fun visit(value: ArkUndefinedConstant): R
        fun visit(value: ArkArrayLiteral): R
        fun visit(value: ArkObjectLiteral): R

        interface Default<out R> : Visitor<R> {
            override fun visit(value: ArkStringConstant): R = defaultVisit(value)
            override fun visit(value: ArkBooleanConstant): R = defaultVisit(value)
            override fun visit(value: ArkNumberConstant): R = defaultVisit(value)
            override fun visit(value: ArkNullConstant): R = defaultVisit(value)
            override fun visit(value: ArkUndefinedConstant): R = defaultVisit(value)
            override fun visit(value: ArkArrayLiteral): R = defaultVisit(value)
            override fun visit(value: ArkObjectLiteral): R = defaultVisit(value)

            fun defaultVisit(value: ArkConstant): R
        }
    }

    override fun <R> accept(visitor: ArkImmediate.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class ArkStringConstant(
    val value: String,
) : ArkConstant {
    override val type: ArkType
        get() = ArkStringType

    override fun toString(): String {
        return "\"$value\""
    }

    override fun <R> accept(visitor: ArkConstant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkBooleanConstant(
    val value: Boolean,
) : ArkConstant {
    override val type: ArkType
        get() = ArkBooleanType

    override fun toString(): String {
        return if (value) "true" else "false"
    }

    override fun <R> accept(visitor: ArkConstant.Visitor<R>): R {
        return visitor.visit(this)
    }

    companion object {
        val TRUE = ArkBooleanConstant(true)
        val FALSE = ArkBooleanConstant(false)
    }
}

data class ArkNumberConstant(
    val value: Double,
) : ArkConstant {
    override val type: ArkType
        get() = ArkNumberType

    override fun toString(): String {
        return value.toString()
    }

    override fun <R> accept(visitor: ArkConstant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkNullConstant : ArkConstant {
    override val type: ArkType
        get() = ArkNullType

    override fun toString(): String = "null"

    override fun <R> accept(visitor: ArkConstant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkUndefinedConstant : ArkConstant {
    override val type: ArkType
        get() = ArkUndefinedType

    override fun toString(): String = "undefined"

    override fun <R> accept(visitor: ArkConstant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkArrayLiteral(
    val elements: List<ArkEntity>,
    override val type: ArkArrayType,
) : ArkConstant {
    init {
        require(type.dimensions == 1) {
            "Array type of array literal must have exactly one dimension"
        }
    }

    override fun toString(): String {
        return elements.joinToString(prefix = "[", postfix = "]")
    }

    override fun <R> accept(visitor: ArkConstant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// TODO: replace `Pair<String, Value>` with `Property`
data class ArkObjectLiteral(
    val properties: List<Pair<String, ArkEntity>>,
    override val type: ArkType, // TODO: consider ClassType
) : ArkConstant {
    override fun toString(): String {
        return properties.joinToString(prefix = "{", postfix = "}") { (name, value) ->
            "$name: $value"
        }
    }

    override fun <R> accept(visitor: ArkConstant.Visitor<R>): R {
        return visitor.visit(this)
    }
}
