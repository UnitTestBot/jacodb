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
import org.utbot.jacodb.impl.types.isNotNullAnnotation
import org.utbot.jacodb.impl.types.isNullableAnnotation

/**
 * Stores the result of [relaxWithAnnotation] method. See this method's docs for more details.
 */
private sealed class RelaxationResult {
    class Completed(val relaxedType: JvmType): RelaxationResult()
    class NeedsToRelaxInner(val fromStep: Int): RelaxationResult()

    fun takeCompleted(): JvmType = (this as Completed).relaxedType
}

/**
 * Recursive method that applies type annotation to correct part of type
 *
 * Note that inner types are handled in a special way. This is because in JvmTypes by default we look at the innermost
 * type, and we can access outer type via [JvmParameterizedType.JvmNestedType.ownerType].
 * However, in type annotations path is stored in the opposite way -- by default annotation is applied to the outermost
 * type, and a special symbol in typePath indicates that it should be applied to inner type -- see [TypePath.INNER_TYPE]
 *
 * So, if current type is [JvmParameterizedType.JvmNestedType] we should call this method on its ownertype. This call
 * may either complete successfully (and in this case we have to do nothing), or it can meet directive to go to inner type,
 * and in this case it will return [RelaxationResult.NeedsToRelaxInner], which will mean that we should continue parsing
 * and going through typePath for current type. fromStep property will denote, from which step of typePath we should
 * continue.
 *
 * @param annotationInfo annotation to be applied
 * @param cp [JcClasspath] instance needed to instantiate [JcAnnotationImpl]
 * @param curTypePathStep step of typePath that is currently being processed -- see [TypePath.getStep] for more details
 */
private fun JvmType.relaxWithAnnotation(
    annotationInfo: AnnotationInfo,
    cp: JcClasspath,
    curTypePathStep: Int,
): RelaxationResult {
    val step: Int
    if (this is JvmParameterizedType.JvmNestedType) {
        when (val result = ownerType.relaxWithAnnotation(annotationInfo, cp, curTypePathStep)) {
            is RelaxationResult.Completed -> return RelaxationResult.Completed(
                JvmParameterizedType.JvmNestedType(
                    name,
                    parameterTypes,
                    result.relaxedType,
                    isNullable,
                    annotations
                )
            )
            is RelaxationResult.NeedsToRelaxInner -> step = result.fromStep
        }
    } else {
        step = curTypePathStep
    }

    val typePath = TypePath.fromString(annotationInfo.typePath)
    if (typePath == null || typePath.length == step) {
        val annotation = JcAnnotationImpl(annotationInfo, cp)
        return when {
            annotation.isNotNullAnnotation-> RelaxationResult.Completed(copyWith(false, annotations.plus(annotation)))
            annotation.isNullableAnnotation-> RelaxationResult.Completed(copyWith(true, annotations.plus(annotation)))
            else -> RelaxationResult.Completed(copyWith(isNullable, annotations.plus(annotation)))
        }
    }

    return when (typePath.getStep(step)) {
        TypePath.TYPE_ARGUMENT -> {
            require(this is JvmParameterizedType)
            val index = typePath.getStepArgument(step)
            val newParameterTypes = parameterTypes.toMutableList()
            newParameterTypes[index] = newParameterTypes[index]
                .relaxWithAnnotation(annotationInfo, cp, step + 1).takeCompleted()
            RelaxationResult.Completed(
                when (this) {
                    is JvmParameterizedType.JvmNestedType -> JvmParameterizedType.JvmNestedType(
                        name,
                        newParameterTypes,
                        ownerType,
                        isNullable,
                        annotations
                    )
                    else -> JvmParameterizedType(
                        name,
                        newParameterTypes,
                        isNullable,
                        annotations
                    )
                }
            )
        }
        TypePath.WILDCARD_BOUND -> {
            require(this is JvmBoundWildcard)
            when (this) {
                is JvmBoundWildcard.JvmLowerBoundWildcard -> RelaxationResult.Completed(
                    JvmBoundWildcard.JvmLowerBoundWildcard(
                        bound.relaxWithAnnotation(annotationInfo, cp, step + 1).takeCompleted()
                    )
                )
                is JvmBoundWildcard.JvmUpperBoundWildcard -> RelaxationResult.Completed(
                    JvmBoundWildcard.JvmUpperBoundWildcard(
                        bound.relaxWithAnnotation(annotationInfo, cp, step + 1).takeCompleted()
                    )
                )
            }
        }
        TypePath.ARRAY_ELEMENT -> {
            require(this is JvmArrayType)
            RelaxationResult.Completed(
                JvmArrayType(
                    elementType.relaxWithAnnotation(annotationInfo, cp, step + 1).takeCompleted(),
                    isNullable,
                    annotations
                )
            )
        }
        TypePath.INNER_TYPE -> RelaxationResult.NeedsToRelaxInner(step + 1)
        else -> error("Illegal type path step occurred while parsing type annotation")
    }
}

/**
 * Adds all given type annotations to proper parts of type (which is given as receiver).
 * Also, for nullability annotations, changes the nullability of corresponding parts of type
 */
internal fun JvmType.relaxWithAnnotations(annotationInfos: List<AnnotationInfo>, cp: JcClasspath): JvmType =
    annotationInfos.fold(this) { type, annotation ->
        type.relaxWithAnnotation(annotation, cp, 0).takeCompleted()
    }