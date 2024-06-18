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

import org.jacodb.api.common.CommonType
import org.jacodb.api.common.CommonTypeName
import org.jacodb.panda.dynamic.ark.model.ClassSignature

interface ArkType : CommonType, CommonTypeName {
    override val typeName: String

    override val nullable: Boolean?
        get() = false

    interface Visitor<out R> {
        fun visit(type: AnyType): R
        fun visit(type: UnknownType): R
        fun visit(type: UnionType): R
        fun visit(type: TupleType): R
        fun visit(type: BooleanType): R
        fun visit(type: NumberType): R
        fun visit(type: StringType): R
        fun visit(type: NullType): R
        fun visit(type: UndefinedType): R
        fun visit(type: VoidType): R
        fun visit(type: NeverType): R
        fun visit(type: LiteralType): R
        fun visit(type: ClassType): R
        fun visit(type: ArrayType): R
        fun visit(type: ArrayObjectType): R
        fun visit(type: UnclearRefType): R

        interface Default<R> : Visitor<R> {
            override fun visit(type: AnyType): R = defaultVisit(type)
            override fun visit(type: UnknownType): R = defaultVisit(type)
            override fun visit(type: UnionType): R = defaultVisit(type)
            override fun visit(type: TupleType): R = defaultVisit(type)
            override fun visit(type: BooleanType): R = defaultVisit(type)
            override fun visit(type: NumberType): R = defaultVisit(type)
            override fun visit(type: StringType): R = defaultVisit(type)
            override fun visit(type: NullType): R = defaultVisit(type)
            override fun visit(type: UndefinedType): R = defaultVisit(type)
            override fun visit(type: VoidType): R = defaultVisit(type)
            override fun visit(type: NeverType): R = defaultVisit(type)
            override fun visit(type: LiteralType): R = defaultVisit(type)
            override fun visit(type: ClassType): R = defaultVisit(type)
            override fun visit(type: ArrayType): R = defaultVisit(type)
            override fun visit(type: ArrayObjectType): R = defaultVisit(type)
            override fun visit(type: UnclearRefType): R = defaultVisit(type)

            fun defaultVisit(type: ArkType): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

object AnyType : ArkType {
    override val typeName: String
        get() = "any"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object UnknownType : ArkType {
    override val typeName: String
        get() = "unknown"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class UnionType(
    val types: List<ArkType>,
) : ArkType {
    override val typeName: String
        get() = types.joinToString(separator = " | ") { it.typeName }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class TupleType(
    val types: List<ArkType>,
) : ArkType {
    override val typeName: String
        get() = types.joinToString(prefix = "[", postfix = "]") { it.typeName }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

// TODO: EnumType

interface PrimitiveType : ArkType

object BooleanType : PrimitiveType {
    override val typeName: String
        get() = "boolean"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object NumberType : PrimitiveType {
    override val typeName: String
        get() = "number"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object StringType : PrimitiveType {
    override val typeName: String
        get() = "string"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object NullType : PrimitiveType {
    override val typeName: String
        get() = "null"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object UndefinedType : PrimitiveType {
    override val typeName: String
        get() = "undefined"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object VoidType : PrimitiveType {
    override val typeName: String
        get() = "void"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object NeverType : PrimitiveType {
    override val typeName: String
        get() = "never"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class LiteralType(
    val literalTypeName: String,
) : PrimitiveType {
    override val typeName: String
        get() = "literal"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface RefType : ArkType

data class ClassType(
    val classSignature: ClassSignature,
) : RefType {
    override val typeName: String
        get() = classSignature.name

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArrayType(
    val elementType: ArkType,
    val dimensions: Int,
) : RefType {
    override val typeName: String
        get() = elementType.typeName + "[]".repeat(dimensions)

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArrayObjectType(
    val elementType: ArkType,
) : RefType {
    override val typeName: String
        get() = "Array<${elementType.typeName}>"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class UnclearRefType(
    override val typeName: String,
) : RefType {
    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}
