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

package org.jacodb.impl.types.signature

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JvmType

private data class JvmTypeUpdate(val newNullability: Boolean?, val newAnnotations: List<JcAnnotation>)

/**
 * Returns given type with nullability and annotations set according to given [JvmTypeUpdate] instance
 */
private object JvmTypeUpdateVisitor : JvmTypeVisitor<JvmTypeUpdate> {

    override fun visitUpperBound(type: JvmBoundWildcard.JvmUpperBoundWildcard, context: JvmTypeUpdate): JvmType {
        return visitWildcard(type, context)
    }

    override fun visitLowerBound(type: JvmBoundWildcard.JvmLowerBoundWildcard, context: JvmTypeUpdate): JvmType {
        return visitWildcard(type, context)
    }

    override fun visitArrayType(type: JvmArrayType, context: JvmTypeUpdate): JvmType {
        return JvmArrayType(type.elementType, context.newNullability, context.newAnnotations)
    }

    override fun visitTypeVariable(type: JvmTypeVariable, context: JvmTypeUpdate): JvmType {
        return JvmTypeVariable(type.symbol, context.newNullability, context.newAnnotations).also {
            it.declaration = type.declaration
        }
    }

    override fun visitClassRef(type: JvmClassRefType, context: JvmTypeUpdate): JvmType {
        return JvmClassRefType(type.name, context.newNullability, context.newAnnotations)
    }

    override fun visitNested(type: JvmParameterizedType.JvmNestedType, context: JvmTypeUpdate): JvmType {
        return JvmParameterizedType.JvmNestedType(
            type.name,
            type.parameterTypes,
            type.ownerType,
            context.newNullability,
            context.newAnnotations
        )
    }

    override fun visitParameterizedType(type: JvmParameterizedType, context: JvmTypeUpdate): JvmType {
        return JvmParameterizedType(type.name, type.parameterTypes, context.newNullability, context.newAnnotations)
    }

    private fun visitWildcard(type: JvmWildcard, context: JvmTypeUpdate): JvmType {
        if (context.newNullability != true)
            error("Attempting to make wildcard not-nullable, which are always nullable by convention")
        if (context.newAnnotations.isNotEmpty())
            error("Annotations on wildcards are not supported")
        return type
    }
}

internal fun JvmType.copyWith(nullability: Boolean?, annotations: List<JcAnnotation> = this.annotations): JvmType =
    JvmTypeUpdateVisitor.visitType(this, JvmTypeUpdate(nullability, annotations))