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

import org.jacodb.api.common.CommonType
import org.jacodb.api.common.CommonTypeName

sealed interface PandaType : CommonType, CommonTypeName {
    override val nullable: Boolean
        get() = false
}

data class PandaNamedType(
    override val typeName: String,
) : PandaType {
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

interface PandaRefType : PandaType

object PandaObjectType : PandaRefType {
    override val typeName: String
        get() = "object"

    override fun toString(): String = typeName
}

// TODO: merge interface and single implementation (data class)
interface PandaArrayType : PandaRefType {
    val elementType: PandaType
    val dimensions: Int
}

data class PandaArrayTypeImpl(
    override val elementType: PandaType,
) : PandaArrayType {
    override val dimensions: Int
        get() = when (elementType) {
            is PandaArrayType -> elementType.dimensions + 1
            else -> 1
        }

    override val typeName: String
        get() = elementType.typeName + "[]"

    override fun toString(): String = typeName
}

// TODO: merge interface and single implementation (data class)
interface PandaClassType : PandaRefType

data class PandaClassTypeImpl(
    override val typeName: String,
) : PandaClassType {
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

// data class PandaTypeName(
//     override val typeName: String,
// ) : CommonTypeName {
//     override fun toString(): String = typeName
// }
