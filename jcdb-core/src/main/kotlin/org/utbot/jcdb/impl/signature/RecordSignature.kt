package org.utbot.jcdb.impl.signature

import org.objectweb.asm.signature.SignatureReader
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.Malformed
import org.utbot.jcdb.api.Raw
import org.utbot.jcdb.api.RecordComponentResolution

class RecordSignature(private val cp: Classpath) : TypeRegistrant {

    private lateinit var recordComponentType: SType

    override fun register(token: SType) {
        recordComponentType = token
    }

    protected fun resolve(): RecordComponentResolution {
        return RecordComponentResolutionImpl(recordComponentType)
    }

    companion object {
        fun of(signature: String?, cp: Classpath): RecordComponentResolution {
            signature ?: return Raw
            val signatureReader = SignatureReader(signature)
            val visitor = RecordSignature(cp)
            return try {
                signatureReader.acceptType(TypeExtractor(cp, visitor))
                visitor.resolve()
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}
