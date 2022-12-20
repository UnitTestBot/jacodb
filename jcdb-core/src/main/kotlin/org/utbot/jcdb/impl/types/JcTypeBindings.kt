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

package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcAccessible
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.ext.anyType
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.types.signature.JvmArrayType
import org.utbot.jcdb.impl.types.signature.JvmBoundWildcard
import org.utbot.jcdb.impl.types.signature.JvmClassRefType
import org.utbot.jcdb.impl.types.signature.JvmParameterizedType
import org.utbot.jcdb.impl.types.signature.JvmPrimitiveType
import org.utbot.jcdb.impl.types.signature.JvmType
import org.utbot.jcdb.impl.types.signature.JvmTypeVariable
import org.utbot.jcdb.impl.types.signature.JvmUnboundWildcard

internal fun JcClasspath.typeOf(jvmType: JvmType, parameters: List<JvmType>? = null): JcType {
    return when (jvmType) {
        is JvmPrimitiveType -> {
            PredefinedPrimitives.of(jvmType.ref, this)
                ?: throw IllegalStateException("primitive type ${jvmType.ref} not found")
        }

        is JvmClassRefType -> typeOf(findClass(jvmType.name)).copyWithNullability(jvmType.isNullable)
        is JvmArrayType -> arrayTypeOf(typeOf(jvmType.elementType)).copyWithNullability(jvmType.isNullable)
        is JvmParameterizedType -> {
            val clazz = findClass(jvmType.name)
            JcClassTypeImpl(
                clazz,
                null,
                parameters ?: jvmType.parameterTypes,
                nullable = jvmType.isNullable
            )
        }

        is JvmParameterizedType.JvmNestedType -> {
            val clazz = findClass(jvmType.name)
            val outerParameters = (jvmType.ownerType as? JvmParameterizedType)?.parameterTypes
            val outerType = typeOf(jvmType.ownerType, parameters ?: outerParameters)
            JcClassTypeImpl(
                clazz,
                outerType as JcClassTypeImpl,
                jvmType.parameterTypes,
                nullable = jvmType.isNullable
            )
        }

        is JvmTypeVariable -> {
            val declaration = jvmType.declaration
            if (declaration != null) {
                JcTypeVariableImpl(this, declaration.asJcDeclaration(declaration.owner), jvmType.isNullable)
            } else {
                anyType()
            }
        }

        is JvmUnboundWildcard -> JcUnboundWildcardImpl(this)
        is JvmBoundWildcard.JvmUpperBoundWildcard -> JcBoundedWildcardImpl(
            upperBounds = listOf(typeOf(jvmType.bound) as JcRefType), lowerBounds = emptyList()
        )

        is JvmBoundWildcard.JvmLowerBoundWildcard -> JcBoundedWildcardImpl(
            upperBounds = emptyList(), lowerBounds = listOf(typeOf(jvmType.bound) as JcRefType)
        )
    }
}

class JcTypeVariableDeclarationImpl(
    override val symbol: String,
    override val bounds: List<JcRefType>,
    override val owner: JcAccessible
) : JcTypeVariableDeclaration
