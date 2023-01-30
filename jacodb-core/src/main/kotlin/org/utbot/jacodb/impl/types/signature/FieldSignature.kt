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

import mu.KLogging
import org.objectweb.asm.signature.SignatureReader
import org.utbot.jacodb.api.FieldResolution
import org.utbot.jacodb.api.JcField
import org.utbot.jacodb.api.Malformed
import org.utbot.jacodb.api.Pure
import org.utbot.jacodb.impl.bytecode.kmType
import org.utbot.jacodb.impl.types.allVisibleTypeParameters
import org.utbot.jacodb.impl.types.substition.JvmTypeVisitor
import org.utbot.jacodb.impl.types.substition.fixDeclarationVisitor

internal class FieldSignature(private val field: JcField?) : TypeRegistrant {

    private lateinit var fieldType: JvmType

    override fun register(token: JvmType) {
        fieldType = field?.kmType?.let { token.relaxWithKmType(it) } ?: token
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object : KLogging() {

        private fun FieldResolutionImpl.apply(visitor: JvmTypeVisitor) =
            FieldResolutionImpl(visitor.visitType(fieldType))

        fun of(field: JcField): FieldResolution {
            return of(field.signature, field.enclosingClass.allVisibleTypeParameters(), field)
        }

        fun of(
            signature: String?,
            declarations: Map<String, JvmTypeParameterDeclaration>,
            field: JcField?
        ): FieldResolution {
            signature ?: return Pure
            val signatureReader = SignatureReader(signature)
            val visitor = FieldSignature(field)
            return try {
                signatureReader.acceptType(TypeExtractor(visitor))
                val result = visitor.resolve()
                result.let {
                    if (it is FieldResolutionImpl) {
                        it.apply(declarations.fixDeclarationVisitor)
                    } else {
                        it
                    }
                }
            } catch (ignored: RuntimeException) {
                logger.warn(ignored) { "Can't parse signature '$signature' of field $field" }
                Malformed
            }
        }
    }
}