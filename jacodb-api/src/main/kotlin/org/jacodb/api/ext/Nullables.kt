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

@file:JvmName("Nullables")

package org.jacodb.api.ext

import org.jacodb.api.JcAnnotated
import org.jacodb.api.JcAnnotation
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.JcParameter
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.TypeName

val JcAnnotation.isNotNullAnnotation: Boolean
    get() = NullabilityAnnotations.notNullAnnotations.any { matches(it) }

val JcAnnotation.isNullableAnnotation: Boolean
    get() = NullabilityAnnotations.nullableAnnotations.any { matches(it) }

private object NullabilityAnnotations {
    val notNullAnnotations = listOf(
        "org.jetbrains.annotations.NotNull",
        "lombok.NonNull"
    )

    val nullableAnnotations = listOf(
        "org.jetbrains.annotations.Nullable"
    )
}

private fun JcAnnotated.isNullable(type: TypeName): Boolean? =
    when {
        PredefinedPrimitives.matches(type.typeName) -> false
        annotations.any { it.isNotNullAnnotation } -> false
        annotations.any { it.isNullableAnnotation } -> true
        else -> null
    }

// TODO: maybe move these methods from ext into class definitions?
//  We already have many nullability-related methods there, furthermore this way we can use jacodb-core in implementation
val JcField.isNullable: Boolean?
    get() = isNullable(type)

val JcParameter.isNullable: Boolean?
    get() = isNullable(type)

val JcMethod.isNullable: Boolean?
    get() = isNullable(returnType)
