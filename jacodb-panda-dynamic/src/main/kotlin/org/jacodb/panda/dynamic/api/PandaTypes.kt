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

object PandaAnyType : PandaType {
    override val typeName: String
        get() = "any"
}

object PandaVoidType : PandaType {
    override val typeName: String
        get() = "void"
}

object PandaUndefinedType : PandaType {
    override val typeName: String
        get() = "undefined_t"
}

interface PandaRefType : PandaType, CommonRefType

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
}

interface PandaClassType : PandaRefType, CommonClassType

class PandaClassTypeImpl(
    override val typeName: String,
) : PandaClassType

interface PandaPrimitiveType : PandaType

object PandaBoolType : PandaPrimitiveType {
    override val typeName: String
        get() = "bool"
}

object PandaNumberType : PandaPrimitiveType {
    override val typeName: String
        get() = "number"
}

// ------------------------------------------------------

data class PandaTypeName(
    override val typeName: String,
) : CommonTypeName
