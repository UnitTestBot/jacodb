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

package org.utbot.jcdb.impl.types.signature

import org.objectweb.asm.signature.SignatureReader
import org.utbot.jcdb.api.FieldResolution
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.Pure
import org.utbot.jcdb.api.ext.kmType
import org.utbot.jcdb.impl.types.allVisibleTypeParameters
import org.utbot.jcdb.impl.types.substition.JvmTypeVisitor
import org.utbot.jcdb.impl.types.substition.fixDeclarationVisitor

internal class FieldSignature(private val field: JcField?) : TypeRegistrant {

    private lateinit var fieldType: JvmType

    override fun register(token: JvmType) {
        fieldType = field?.kmType?.let { token.relaxWithKmType(it) } ?: token
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object {

        private fun FieldResolutionImpl.apply(visitor: JvmTypeVisitor) =
            FieldResolutionImpl(visitor.visitType(fieldType))

        fun of(field: JcField): FieldResolution {
            return of(field.signature, field.enclosingClass.allVisibleTypeParameters(), field)
        }

        fun of(signature: String?, declarations: Map<String, JvmTypeParameterDeclaration>, field: JcField?): FieldResolution {
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
                throw ignored
            }
        }
    }
}