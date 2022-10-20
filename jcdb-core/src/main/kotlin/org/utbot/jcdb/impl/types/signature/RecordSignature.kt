package org.utbot.jcdb.impl.types.signature

import org.objectweb.asm.signature.SignatureReader
import org.utbot.jcdb.api.Malformed
import org.utbot.jcdb.api.Pure
import org.utbot.jcdb.api.RecordComponentResolution

internal class RecordSignature : TypeRegistrant {

    private lateinit var recordComponentType: JvmType

    override fun register(token: JvmType) {
        recordComponentType = token
    }

    protected fun resolve(): RecordComponentResolution {
        return RecordComponentResolutionImpl(recordComponentType)
    }

    companion object {
        fun of(signature: String?): RecordComponentResolution {
            signature ?: return Pure
            val signatureReader = SignatureReader(signature)
            val visitor = RecordSignature()
            return try {
                signatureReader.acceptType(TypeExtractor(visitor))
                visitor.resolve()
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}
