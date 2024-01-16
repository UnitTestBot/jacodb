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

import mu.KLogging
import org.jacodb.api.jvm.FieldResolution
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.Malformed
import org.jacodb.api.jvm.Pure
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.bytecode.kmType
import org.jacodb.impl.types.allVisibleTypeParameters
import org.jacodb.impl.types.substition.RecursiveJvmTypeVisitor
import org.jacodb.impl.types.substition.fixDeclarationVisitor
import org.jacodb.api.jvm.JvmType
import org.jacodb.api.jvm.JvmTypeParameterDeclaration
import org.objectweb.asm.signature.SignatureReader

internal class FieldSignature(private val field: JcField?) : TypeRegistrant {

    private lateinit var fieldType: JvmType

    override fun register(token: JvmType) {
        fieldType = field?.kmType?.let { JvmTypeKMetadataUpdateVisitor.visitType(token, it) } ?: token
        (field as? JcFieldImpl)?.let {
            fieldType = fieldType.withTypeAnnotations(it.typeAnnotationInfos, it.enclosingClass.classpath)
        }
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object : KLogging() {

        private fun FieldResolutionImpl.apply(visitor: RecursiveJvmTypeVisitor) =
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