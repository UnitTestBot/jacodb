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

package org.jacodb.impl.types

import org.jacodb.api.JcAccessible
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypeVariableDeclaration
import org.jacodb.api.JvmType
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.objectType
import org.jacodb.impl.types.signature.JvmArrayType
import org.jacodb.impl.types.signature.JvmBoundWildcard
import org.jacodb.impl.types.signature.JvmClassRefType
import org.jacodb.impl.types.signature.JvmParameterizedType
import org.jacodb.impl.types.signature.JvmPrimitiveType
import org.jacodb.impl.types.signature.JvmTypeVariable
import org.jacodb.impl.types.signature.JvmUnboundWildcard

internal fun JcClasspath.typeOf(jvmType: JvmType, parameters: List<JvmType>? = null): JcType {
    return when (jvmType) {
        is JvmPrimitiveType -> {
            PredefinedPrimitives.of(jvmType.ref, this, jvmType.annotations)
                ?: throw IllegalStateException("primitive type ${jvmType.ref} not found")
        }

        is JvmClassRefType -> typeOf(findClass(jvmType.name), jvmType.isNullable, jvmType.annotations)
        is JvmArrayType -> arrayTypeOf(typeOf(jvmType.elementType), jvmType.isNullable, jvmType.annotations)
        is JvmParameterizedType -> {
            val params = parameters ?: jvmType.parameterTypes
            when {
                params.isNotEmpty() -> JcClassTypeImpl(
                    this,
                    jvmType.name,
                    null,
                    params,
                    nullable = jvmType.isNullable,
                    jvmType.annotations
                )
                // raw types
                else -> typeOf(findClass(jvmType.name)).copyWithNullability(jvmType.isNullable)
            }
        }

        is JvmParameterizedType.JvmNestedType -> {
            val outerParameters = (jvmType.ownerType as? JvmParameterizedType)?.parameterTypes
            val outerType = typeOf(jvmType.ownerType, parameters ?: outerParameters)
            JcClassTypeImpl(
                this,
                jvmType.name,
                outerType as JcClassTypeImpl,
                jvmType.parameterTypes,
                nullable = jvmType.isNullable,
                jvmType.annotations
            )
        }

        is JvmTypeVariable -> {
            val declaration = jvmType.declaration
            if (declaration != null) {
                JcTypeVariableImpl(
                    this,
                    declaration.asJcDeclaration(declaration.owner),
                    jvmType.isNullable,
                    jvmType.annotations
                )
            } else {
                objectType
            }
        }

        is JvmUnboundWildcard -> JcUnboundWildcardImpl(this)
        is JvmBoundWildcard.JvmUpperBoundWildcard -> JcBoundedWildcardImpl(
            upperBounds = listOf(typeOf(jvmType.bound) as JcRefType), lowerBounds = emptyList()
        )

        is JvmBoundWildcard.JvmLowerBoundWildcard -> JcBoundedWildcardImpl(
            upperBounds = emptyList(), lowerBounds = listOf(typeOf(jvmType.bound) as JcRefType)
        )

        else -> throw IllegalStateException("Unsupported type")
    }
}

class JcTypeVariableDeclarationImpl(
    override val symbol: String,
    private val classpath: JcClasspath,
    private val jvmBounds: List<JvmType>,
    override val owner: JcAccessible,
) : JcTypeVariableDeclaration {
    override val bounds: List<JcRefType> get() = jvmBounds.map { classpath.typeOf(it) as JcRefType }
}
