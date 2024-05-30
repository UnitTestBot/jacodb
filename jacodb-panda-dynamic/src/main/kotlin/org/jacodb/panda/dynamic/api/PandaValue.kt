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

package org.jacodb.panda.dynamic.api

import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonArrayAccess
import org.jacodb.api.common.cfg.CommonFieldRef
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue

interface PandaValue : PandaExpr, CommonValue

interface PandaSimpleValue : PandaValue {
    override val operands: List<PandaValue>
        get() = emptyList()
}

interface PandaLocal : PandaSimpleValue {
    // TODO: val name: String
}

data class PandaLocalVar(
    val index: Int,
    override val type: PandaType,
) : PandaLocal {

    override fun toString(): String = "%$index"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLocalVar(this)
    }
}

data class PandaLexVar(
    val lexenvIndex: Int,
    val lexvarIndex: Int,
    override val type: PandaType,
) : PandaSimpleValue {
    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        TODO("Not yet implemented")
    }
}

data class PandaThis(
    override val type: PandaType,
) : PandaLocal, CommonThis {

    override fun toString(): String = "this"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaThis(this)
    }
}

data class PandaArgument(
    override val index: Int,
    override val name: String = "arg$index",
    override val type: PandaType = PandaAnyType,
) : PandaLocal, CommonArgument {

    override fun toString(): String = "arg $index"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaArgument(this)
    }
}

interface PandaConstant : PandaSimpleValue

interface PandaConstantWithValue : PandaConstant {
    val value: Any?
}

data class TODOConstant(override val value: String?) : PandaConstantWithValue {
    override val type: PandaType
        get() = PandaAnyType

    override fun toString(): String = value?.let { "TODOConstant($it)" } ?: "null"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaTODOConstant(this)
    }
}

data class PandaBoolConstant(override val value: Boolean) : PandaConstantWithValue {
    override val type: PandaType
        get() = PandaBoolType

    override fun toString(): String = value.toString()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaBoolConstant(this)
    }
}

data class PandaNumberConstant(override val value: Int) : PandaConstantWithValue {
    override val type: PandaType
        get() = PandaNumberType

    override fun toString(): String = value.toString()

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNumberConstant(this)
    }
}

data class PandaStringConstant(override val value: String) : PandaConstantWithValue {
    override val type: PandaType
        get() = PandaStringType

    override fun toString(): String = "\"$value\""

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaStringConstant(this)
    }
}

object PandaUndefinedConstant : PandaConstant {
    override val type: PandaType
        get() = PandaUndefinedType

    override fun toString(): String = "undefined"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaUndefinedConstant(this)
    }
}

object PandaInfinityConstant : PandaConstant {
    override val type: PandaType
        get() = PandaNumberType

    override fun toString(): String = "Infinity"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaInfinityConstant(this)
    }
}

object PandaNaNConstant : PandaConstant {
    override val type: PandaType
        get() = PandaAnyType

    override fun toString(): String = "NaN"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNaNConstant(this)
    }
}

object PandaNullConstant : PandaConstant {
    override val operands: List<PandaValue>
        get() = emptyList()

    // actually "typeof null" is "object", so consider smth like PandaClassType but PandaObjectType
    override val type: PandaType
        get() = PandaObjectType

    override fun toString(): String = "null"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaNullConstant(this)
    }
}

data class PandaMethodConstant(
    val methodName: String,
) : PandaConstant {
    override val type: PandaType
        get() = PandaAnyType

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaMethodConstant(this)
    }
}

interface PandaComplexValue : PandaValue

data class PandaFieldRef(
    override val instance: PandaValue?, // null for static fields
    override val classField: PandaField,
    override val type: PandaType,
) : PandaComplexValue, CommonFieldRef {
    override val operands: List<PandaValue>
        get() = listOfNotNull(instance)

    override fun toString(): String = "${instance ?: classField.enclosingClass.simpleName}.${classField.name}"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaFieldRef(this)
    }
}

data class PandaArrayAccess(
    override val array: PandaValue,
    override val index: PandaValue,
    override val type: PandaType,
) : PandaComplexValue, CommonArrayAccess {
    override val operands: List<PandaValue>
        get() = listOf(array, index)

    override fun toString(): String = "$array[$index]"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaArrayAccess(this)
    }
}

data class PandaValueByInstance(
    val instance: PandaValue,
    val property: String,
) : PandaComplexValue {
    override val type: PandaType
        get() = PandaAnyType

    val className: String
        get() = when(instance) {
            is PandaLoadedValue -> instance.className
            else -> instance.typeName
        }

    override val operands: List<PandaValue>
        get() = listOf(instance)

    private fun PandaValue.resolve(): String {
        return when (this) {
            is PandaStringConstant -> this.value
            is PandaLocalVar -> this.typeName
            is PandaThis -> this.typeName
            is PandaLoadedValue -> this.getLoadedValueClassName()
            else -> this.typeName
        }
    }

    fun getClassAndMethodName(): List<String> {
        return listOf(instance.resolve(), property)
    }

    override fun toString(): String = "$instance.$property"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaValueByInstance(this)
    }
}

data class PandaLoadedValue(
    val instance: PandaValue,
) : PandaComplexValue {
    override val type: PandaType
        get() = PandaAnyType

    val className: String
        get() = when(instance) {
            is PandaStringConstant -> instance.value
            else -> instance.typeName
        }

    override val operands: List<PandaValue>
        get() = listOf(instance)

    private fun PandaValue.resolve(): String {
        return when (this) {
            is PandaStringConstant -> this.value
            is PandaLocalVar -> this.typeName
            is PandaThis -> this.typeName
            else -> throw IllegalArgumentException("couldn't resolve $this")
        }
    }

    fun getLoadedValueClassName(): String {
        return instance.resolve()
    }

    override fun toString(): String = "$instance"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaLoadedValue(this)
    }
}

class PandaCaughtError : PandaValue {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = emptyList()

    override fun toString(): String = "error"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaCaughtError(this)
    }
}

class PandaPhiValue(
    private val _inputs: Lazy<List<PandaValue>>,
    val basicBlockIds: List<Int>,
    override val type: PandaType
) : PandaValue {
    val inputs: List<PandaValue>
        get() = _inputs.value

    override val operands: List<PandaValue>
        get() = inputs

    override fun toString(): String = "Phi(${inputs.zip(basicBlockIds).joinToString { (input, id) -> "$id: $input" }})"

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaPhiValue(this)
    }
}

class PandaBuiltInError(override val typeName: String) : PandaValue {
    override val type: PandaType
        get() = PandaAnyType

    override val operands: List<PandaValue>
        get() = emptyList()

    override fun toString(): String = typeName

    override fun <T> accept(visitor: PandaExprVisitor<T>): T {
        return visitor.visitPandaBuiltInError(this)
    }
}
