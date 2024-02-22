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

import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeParameter
import org.jacodb.api.jvm.JvmType
import org.jacodb.api.jvm.JvmTypeParameterDeclaration
import org.jacodb.impl.bytecode.isNullable

/**
 * Recursively visits type and take all info about nullability from given kmType
 */
internal object JvmTypeKMetadataUpdateVisitor : JvmTypeVisitor<KmType> {
    override fun visitUpperBound(type: JvmBoundWildcard.JvmUpperBoundWildcard, context: KmType): JvmType {
        return JvmBoundWildcard.JvmUpperBoundWildcard(visitType(type.bound, context))
    }

    override fun visitLowerBound(type: JvmBoundWildcard.JvmLowerBoundWildcard, context: KmType): JvmType {
        return JvmBoundWildcard.JvmLowerBoundWildcard(visitType(type.bound, context))
    }

    override fun visitArrayType(type: JvmArrayType, context: KmType): JvmType {
        // NB: kmType may have zero (for primitive arrays) or one (for object arrays) argument
        val updatedElementType = context.arguments.singleOrNull()?.type?.let {
            visitType(type.elementType, it)
        } ?: type.elementType

        return JvmArrayType(updatedElementType, context.isNullable, type.annotations)
    }

    override fun visitTypeVariable(type: JvmTypeVariable, context: KmType): JvmType {
        return visitFinal(type, context)
    }

    override fun visitClassRef(type: JvmClassRefType, context: KmType): JvmType {
        return visitFinal(type, context)
    }

    override fun visitNested(type: JvmParameterizedType.JvmNestedType, context: KmType): JvmType {
        val relaxedParameterTypes = visitList(type.parameterTypes, context.arguments.map { it.type })
        return JvmParameterizedType.JvmNestedType(
            type.name,
            relaxedParameterTypes,
            type.ownerType,
            context.isNullable,
            type.annotations
        )
    }

    override fun visitParameterizedType(type: JvmParameterizedType, context: KmType): JvmType {
        val relaxedParameterTypes = visitList(type.parameterTypes, context.arguments.map { it.type })
        return JvmParameterizedType(type.name, relaxedParameterTypes, context.isNullable, type.annotations)
    }

    fun visitDeclaration(
        declaration: JvmTypeParameterDeclaration,
        context: KmTypeParameter
    ): JvmTypeParameterDeclaration {
        val newBounds = declaration.bounds?.zip(context.upperBounds) { bound, kmType ->
            visitType(bound, kmType)
        }
        return JvmTypeParameterDeclarationImpl(declaration.symbol, declaration.owner, newBounds)
    }

    private fun visitList(types: List<JvmType>, kmTypes: List<KmType?>): List<JvmType> {
        return types.zip(kmTypes) { type, kmType ->
            if (kmType != null) {
                visitType(type, kmType)
            } else {
                type
            }
        }
    }

    private fun visitFinal(type: AbstractJvmType, context: KmType): JvmType {
        return type.copyWith(context.isNullable)
    }
}
