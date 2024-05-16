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

import org.jacodb.api.common.CommonArrayType
import org.jacodb.api.common.CommonClassType
import org.jacodb.api.common.CommonRefType
import org.jacodb.api.common.CommonType
import org.jacodb.api.common.CommonTypeName

sealed interface PandaType : CommonType {
    override val nullable: Boolean
        get() = false
}

class PandaNamedType(
    override val typeName: String,
) : PandaType {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PandaNamedType

        return typeName == other.typeName
    }

    override fun hashCode(): Int {
        return typeName.hashCode()
    }

    override fun toString(): String = typeName
}

object PandaAnyType : PandaType {
    override val typeName: String
        get() = "any"

    override fun toString(): String = typeName
}

object PandaVoidType : PandaType {
    override val typeName: String
        get() = "void"

    override fun toString(): String = "void"
}

object PandaUndefinedType : PandaType {
    override val typeName: String
        get() = "undefined_t"

    override fun toString(): String = typeName
}

interface PandaRefType : PandaType, CommonRefType

object PandaObjectType : PandaRefType {
    override val typeName: String
        get() = "object"

    override fun toString(): String = typeName
}

interface PandaArrayType : PandaRefType, CommonArrayType {
    override val elementType: PandaType
}

class PandaArrayTypeImpl(
    override val elementType: PandaType,
) : PandaArrayType {
    override val typeName: String
        get() = elementType.typeName + "[]"

    override val dimensions: Int
        get() = when (elementType) {
            is PandaArrayType -> elementType.dimensions + 1
            else -> 1
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PandaArrayTypeImpl

        if (elementType != other.elementType) return false
        if (dimensions != other.dimensions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = elementType.hashCode()
        result = 31 * result + dimensions
        return result
    }

    override fun toString(): String = typeName
}

interface PandaClassType : PandaRefType, CommonClassType

class PandaClassTypeImpl(
    override val typeName: String,
) : PandaClassType {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PandaClassTypeImpl

        return typeName == other.typeName
    }

    override fun hashCode(): Int {
        return typeName.hashCode()
    }

    override fun toString(): String = typeName
}

interface PandaPrimitiveType : PandaType

object PandaBoolType : PandaPrimitiveType {
    override val typeName: String
        get() = "bool"

    override fun toString(): String = typeName
}

object PandaNumberType : PandaPrimitiveType {
    override val typeName: String
        get() = "number"

    override fun toString(): String = typeName
}

object PandaStringType : PandaPrimitiveType {
    override val typeName: String
        get() = "string"

    override fun toString(): String = typeName
}

// ------------------------------------------------------

data class PandaTypeName(
    override val typeName: String,
) : CommonTypeName {
    override fun toString(): String = typeName
}
