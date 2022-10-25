package org.utbot.jcdb.impl.types.signature

import org.objectweb.asm.signature.SignatureReader
import org.utbot.jcdb.api.FieldResolution
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.Pure
import org.utbot.jcdb.impl.types.allVisibleTypeParameters
import org.utbot.jcdb.impl.types.substition.JvmTypeVisitor
import org.utbot.jcdb.impl.types.substition.fixDeclarationVisitor

internal class FieldSignature : TypeRegistrant {

    private lateinit var fieldType: JvmType

    override fun register(token: JvmType) {
        fieldType = token
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object {

        private fun FieldResolutionImpl.apply(visitor: JvmTypeVisitor) =
            FieldResolutionImpl(visitor.visitType(fieldType))

        fun of(field: JcField): FieldResolution {
            return of(field.signature, field.enclosingClass.allVisibleTypeParameters())
        }

        fun of(signature: String?, declarations: Map<String, JvmTypeParameterDeclaration>): FieldResolution {
            signature ?: return Pure
            val signatureReader = SignatureReader(signature)
            val visitor = FieldSignature()
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