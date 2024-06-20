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
import org.jacodb.panda.dynamic.ark.model.ArkClassSignature

interface ArkType : CommonType, CommonTypeName {
    override val typeName: String

    override val nullable: Boolean?
        get() = false

    interface Visitor<out R> {
        fun visit(type: ArkAnyType): R
        fun visit(type: ArkUnknownType): R
        fun visit(type: ArkUnionType): R
        fun visit(type: ArkTupleType): R
        fun visit(type: ArkBooleanType): R
        fun visit(type: ArkNumberType): R
        fun visit(type: ArkStringType): R
        fun visit(type: ArkNullType): R
        fun visit(type: ArkUndefinedType): R
        fun visit(type: ArkVoidType): R
        fun visit(type: ArkNeverType): R
        fun visit(type: ArkLiteralType): R
        fun visit(type: ArkClassType): R
        fun visit(type: ArkArrayType): R
        fun visit(type: ArkArrayObjectType): R
        fun visit(type: ArkUnclearRefType): R

        interface Default<R> : Visitor<R> {
            override fun visit(type: ArkAnyType): R = defaultVisit(type)
            override fun visit(type: ArkUnknownType): R = defaultVisit(type)
            override fun visit(type: ArkUnionType): R = defaultVisit(type)
            override fun visit(type: ArkTupleType): R = defaultVisit(type)
            override fun visit(type: ArkBooleanType): R = defaultVisit(type)
            override fun visit(type: ArkNumberType): R = defaultVisit(type)
            override fun visit(type: ArkStringType): R = defaultVisit(type)
            override fun visit(type: ArkNullType): R = defaultVisit(type)
            override fun visit(type: ArkUndefinedType): R = defaultVisit(type)
            override fun visit(type: ArkVoidType): R = defaultVisit(type)
            override fun visit(type: ArkNeverType): R = defaultVisit(type)
            override fun visit(type: ArkLiteralType): R = defaultVisit(type)
            override fun visit(type: ArkClassType): R = defaultVisit(type)
            override fun visit(type: ArkArrayType): R = defaultVisit(type)
            override fun visit(type: ArkArrayObjectType): R = defaultVisit(type)
            override fun visit(type: ArkUnclearRefType): R = defaultVisit(type)

            fun defaultVisit(type: ArkType): R
        }
    }

    fun <R> accept(visitor: Visitor<R>): R
}

object ArkAnyType : ArkType {
    override val typeName: String
        get() = "any"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkUnknownType : ArkType {
    override val typeName: String
        get() = "unknown"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkUnionType(
    val types: List<ArkType>,
) : ArkType {
    override val typeName: String
        get() = types.joinToString(separator = " | ") { it.typeName }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkTupleType(
    val types: List<ArkType>,
) : ArkType {
    override val typeName: String
        get() = types.joinToString(prefix = "[", postfix = "]") { it.typeName }

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface ArkPrimitiveType : ArkType

object ArkBooleanType : ArkPrimitiveType {
    override val typeName: String
        get() = "boolean"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkNumberType : ArkPrimitiveType {
    override val typeName: String
        get() = "number"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkStringType : ArkPrimitiveType {
    override val typeName: String
        get() = "string"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkNullType : ArkPrimitiveType {
    override val typeName: String
        get() = "null"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkUndefinedType : ArkPrimitiveType {
    override val typeName: String
        get() = "undefined"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkVoidType : ArkPrimitiveType {
    override val typeName: String
        get() = "void"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

object ArkNeverType : ArkPrimitiveType {
    override val typeName: String
        get() = "never"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkLiteralType(
    val literalTypeName: String,
) : ArkPrimitiveType {
    override val typeName: String
        get() = "literal"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface ArkRefType : ArkType

data class ArkClassType(
    val classSignature: ArkClassSignature,
) : ArkRefType {
    override val typeName: String
        get() = classSignature.name

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkArrayType(
    val elementType: ArkType,
    val dimensions: Int,
) : ArkRefType {
    override val typeName: String
        get() = elementType.typeName + "[]".repeat(dimensions)

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkArrayObjectType(
    val elementType: ArkType,
) : ArkRefType {
    override val typeName: String
        get() = "Array<${elementType.typeName}>"

    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkUnclearRefType(
    override val typeName: String,
) : ArkRefType {
    override fun toString(): String = typeName

    override fun <R> accept(visitor: ArkType.Visitor<R>): R {
        return visitor.visit(this)
    }
}
