package org.utbot.jcdb.impl.types.signature

import org.objectweb.asm.signature.SignatureReader
import org.utbot.jcdb.api.FieldResolution
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.Malformed
import org.utbot.jcdb.api.Pure
import org.utbot.jcdb.impl.types.substition.JvmTypeVisitor
import org.utbot.jcdb.impl.types.typeParameters

internal class FieldSignature : TypeRegistrant {

    private lateinit var fieldType: JvmType

    override fun register(token: JvmType) {
        fieldType = token
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object {

        private fun FieldResolutionImpl.apply(visitor: JvmTypeVisitor) = FieldResolutionImpl(
            visitor.visitType(fieldType),
        )


        fun of(field: JcField): FieldResolution {
            field.signature ?: return Pure
            val signatureReader = SignatureReader(field.signature)
            val visitor = FieldSignature()
            return try {
                signatureReader.acceptType(TypeExtractor(visitor))
                val result = visitor.resolve()
                result.let {
                    if (it is FieldResolutionImpl) {
                        val declarations = (field.enclosingClass.typeParameters).associateBy { it.symbol }
                        val fixDeclarationVisitor = object : JvmTypeVisitor {

                            override fun visitTypeVariable(type: JvmTypeVariable): JvmType {
                                type.declaration = declarations[type.symbol]
                                return type
                            }
                        }
                        it.apply(fixDeclarationVisitor)
                    } else {
                        it
                    }
                }
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}