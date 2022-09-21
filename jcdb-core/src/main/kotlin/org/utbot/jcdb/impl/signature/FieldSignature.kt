package org.utbot.jcdb.impl.signature

import org.objectweb.asm.signature.SignatureReader
import org.utbot.jcdb.api.FieldResolution
import org.utbot.jcdb.api.Malformed
import org.utbot.jcdb.api.Pure

internal class FieldSignature : TypeRegistrant {

    private lateinit var fieldType: SType

    override fun register(token: SType) {
        fieldType = token
    }

    fun resolve(): FieldResolution {
        return FieldResolutionImpl(fieldType)
    }

    companion object {
        fun of(signature: String?): FieldResolution {
            signature ?: return Pure
            val signatureReader = SignatureReader(signature)
            val visitor = FieldSignature()
            return try {
                signatureReader.acceptType(TypeExtractor(visitor))
                visitor.resolve()
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}