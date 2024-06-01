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

package org.jacodb.panda.dynamic.ark

interface Constant : Immediate {
    fun <R> accept(visitor: ConstantVisitor<R>): R {
        return accept(visitor as ImmediateVisitor<R>)
    }

    interface Visitor<out R> {
        fun visit(value: StringConstant): R
        fun visit(value: BooleanConstant): R
        fun visit(value: NumberConstant): R
        fun visit(value: NullConstant): R
        fun visit(value: UndefinedConstant): R
        fun visit(value: ArrayLiteral): R
        fun visit(value: ObjectLiteral): R

        interface Default<out R> : Visitor<R> {
            fun defaultVisit(value: Constant): R

            override fun visit(value: StringConstant): R = defaultVisit(value)
            override fun visit(value: BooleanConstant): R = defaultVisit(value)
            override fun visit(value: NumberConstant): R = defaultVisit(value)
            override fun visit(value: NullConstant): R = defaultVisit(value)
            override fun visit(value: UndefinedConstant): R = defaultVisit(value)
            override fun visit(value: ArrayLiteral): R = defaultVisit(value)
            override fun visit(value: ObjectLiteral): R = defaultVisit(value)
        }
    }

    override fun <R> accept3(visitor: Immediate.Visitor<R>): R {
        return accept3(visitor as Visitor<R>)
    }

    fun <R> accept3(visitor: Visitor<R>): R
}

data class StringConstant(
    val value: String,
) : Constant {
    override val type: Type
        get() = StringType

    override fun toString(): String {
        return "\"$value\""
    }

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }

    override fun <R> accept3(visitor: Constant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class BooleanConstant(
    val value: Boolean,
) : Constant {
    override val type: Type
        get() = BooleanType

    override fun toString(): String {
        return if (value) "true" else "false"
    }

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }

    override fun <R> accept3(visitor: Constant.Visitor<R>): R {
        return visitor.visit(this)
    }

    companion object {
        val TRUE = BooleanConstant(true)
        val FALSE = BooleanConstant(false)
    }
}

data class NumberConstant(
    val value: Double,
) : Constant {
    override val type: Type
        get() = NumberType

    override fun toString(): String {
        return value.toString()
    }

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }

    override fun <R> accept3(visitor: Constant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object NullConstant : Constant {
    override val type: Type
        get() = NullType

    override fun toString(): String = "null"

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }

    override fun <R> accept3(visitor: Constant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object UndefinedConstant : Constant {
    override val type: Type
        get() = UndefinedType

    override fun toString(): String = "undefined"

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }

    override fun <R> accept3(visitor: Constant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArrayLiteral(
    val elements: List<Value>,
    override val type: ArrayType,
) : Constant {
    init {
        require(type.dimensions == 1) {
            "Array type of array literal must have exactly one dimension"
        }
    }

    override fun toString(): String {
        return elements.joinToString(prefix = "[", postfix = "]")
    }

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }

    override fun <R> accept3(visitor: Constant.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// TODO: replace `Pair<String, Value>` with `Property`
data class ObjectLiteral(
    val properties: List<Pair<String, Value>>,
    override val type: Type, // TODO: consider ClassType
) : Constant {
    override fun toString(): String {
        return properties.joinToString(prefix = "{", postfix = "}") { (name, value) ->
            "$name: $value"
        }
    }

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }

    override fun <R> accept3(visitor: Constant.Visitor<R>): R {
        return visitor.visit(this)
    }
}
