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

package org.jacodb.api.jvm.storage.ers.typed

import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Base class for user-defined ERS entity types.
 *
 * Usage example:
 * ```
 * object PersonType : ErsType {
 *     val name by property(String::class) // searchable by default
 *     val age by property(Int::class, searchability = ErsSearchability.NonSearchable)
 *     val friend by link(PersonType)
 * }
 * ```
 */
interface ErsType {
    val typeName: String get() = javaClass.name.replace(".", "_")
}

fun <ENTITY_TYPE : ErsType, VALUE : Any> ENTITY_TYPE.property(valueClass: KClass<VALUE>)
    = property(valueClass, ErsSearchability.Searchable)

fun <ENTITY_TYPE : ErsType, VALUE : Any, SEARCH : ErsSearchability> ENTITY_TYPE.property(
    valueClass: KClass<VALUE>,
    searchability: SEARCH
) = NameDependentDelegate { name ->
    ErsProperty(name, this, valueClass.java, searchability)
}

fun <SOURCE_TYPE : ErsType, TARGET_TYPE : ErsType> SOURCE_TYPE.link(targetType: TARGET_TYPE) =
    NameDependentDelegate { name ->
        ErsLink(name, this, targetType)
    }

data class ErsProperty<ENTITY_TYPE : ErsType, VALUE : Any, SEARCH : ErsSearchability> internal constructor(
    val name: String,
    val ownerType: ENTITY_TYPE,
    val valueClass: Class<VALUE>,
    val searchability: SEARCH
)

sealed interface ErsSearchability {
    object Searchable : ErsSearchability
    object NonSearchable : ErsSearchability
}

data class ErsLink<SOURCE_TYPE : ErsType, TARGET_TYPE : ErsType> internal constructor(
    val name: String,
    val sourceType: SOURCE_TYPE,
    val targetType: TARGET_TYPE
)

class NameDependentDelegate<T>(private val initValue: (name: String) -> T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return initValue(property.name)
    }
}
