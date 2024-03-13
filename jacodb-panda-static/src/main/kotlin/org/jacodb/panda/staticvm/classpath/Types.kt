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

package org.jacodb.panda.staticvm.classpath

import org.jacodb.api.common.CommonClassType
import org.jacodb.api.common.CommonType
import org.jacodb.api.common.CommonTypeName

sealed interface PandaType : CommonType, CommonTypeName {
    val array: PandaArrayType
}

data class PandaArrayType(
    val dimensions: Int,
    private val wrappedType: PandaSingleType,
) : PandaType {
    init {
        require(dimensions > 0) { "Cannot create array with $dimensions dimensions" }
    }

    override val typeName: String
        get() = wrappedType.typeName + "[]".repeat(dimensions)
    override val nullable: Boolean
        get() = true

    val elementType: PandaType
        get() = if (dimensions == 1) wrappedType else PandaArrayType(dimensions - 1, wrappedType)

    override val array: PandaArrayType
        get() = PandaArrayType(dimensions + 1, wrappedType)
}

/** any kind of non-array type */
sealed interface PandaSingleType : PandaType {
    override val array: PandaArrayType
        get() = PandaArrayType(1, this)
}

sealed interface PandaObjectType : PandaSingleType, CommonClassType {
    val project: PandaProject

    val pandaClassOrInterface: PandaClassOrInterface

    override val typeName: String
        get() = pandaClassOrInterface.name

    override val nullable: Boolean
        get() = true

}

data class PandaClassType(
    override val project: PandaProject,
    override val pandaClassOrInterface: PandaClass,
) : PandaObjectType

data class PandaInterfaceType(
    override val project: PandaProject,
    override val pandaClassOrInterface: PandaInterface,
) : PandaObjectType
