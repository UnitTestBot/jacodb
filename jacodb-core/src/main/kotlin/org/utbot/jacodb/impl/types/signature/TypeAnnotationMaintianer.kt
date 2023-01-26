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

package org.utbot.jacodb.impl.types.signature

import org.objectweb.asm.TypePath
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.impl.bytecode.JcAnnotationImpl
import org.utbot.jacodb.impl.types.AnnotationInfo

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

// TODO: move it from here
const val NotNull = "org.jetbrains.annotations.NotNull"
const val Nullable = "org.jetbrains.annotations.Nullable"

private fun JvmType.relaxWithAnnotation(annotationInfo: AnnotationInfo, cp: JcClasspath, curTypePathStep: Int): JvmType {
    val typePath = TypePath.fromString(annotationInfo.typePath)
    if (typePath == null || typePath.length == curTypePathStep) {
        val annotation = JcAnnotationImpl(annotationInfo, cp)
        return when {
            annotation.matches(NotNull) -> copyWith(false, annotations.plus(annotation))
            annotation.matches(Nullable) -> copyWith(true, annotations.plus(annotation))
            else -> copyWith(isNullable, annotations.plus(annotation))
        }
    }
    when (typePath.getStep(curTypePathStep)) {
        TypePath.TYPE_ARGUMENT -> {
            require(this is JvmParameterizedType)
            val index = typePath.getStepArgument(curTypePathStep)
            val newParameterTypes = parameterTypes.toMutableList()
            newParameterTypes[index] =
                newParameterTypes[index].relaxWithAnnotation(annotationInfo, cp, curTypePathStep + 1)
            return JvmParameterizedType(
                name,
                newParameterTypes,
                isNullable,
                annotations
            )
        }
        TypePath.WILDCARD_BOUND -> {
            require(this is JvmBoundWildcard)
            return when (this) {
                is JvmBoundWildcard.JvmLowerBoundWildcard -> JvmBoundWildcard.JvmLowerBoundWildcard(
                    bound.relaxWithAnnotation(annotationInfo, cp, curTypePathStep + 1)
                )
                is JvmBoundWildcard.JvmUpperBoundWildcard -> JvmBoundWildcard.JvmUpperBoundWildcard(
                    bound.relaxWithAnnotation(annotationInfo, cp, curTypePathStep + 1)
                )
            }
        }
        TypePath.ARRAY_ELEMENT -> {
            require(this is JvmArrayType)
            return JvmArrayType(
                elementType.relaxWithAnnotation(annotationInfo, cp, curTypePathStep + 1),
                isNullable,
                annotations
            )
        }
        TypePath.INNER_TYPE -> TODO("wtf is this")
        else -> error("Illegal type path step occurred while parsing type annotation")
    }
}

// NB: this method also changes nullability according to given annotations
internal fun JvmType.relaxWithAnnotations(annotationInfos: List<AnnotationInfo>, cp: JcClasspath): JvmType =
    annotationInfos.fold(this) { type, annotation ->
        type.relaxWithAnnotation(annotation, cp, 0)
    }