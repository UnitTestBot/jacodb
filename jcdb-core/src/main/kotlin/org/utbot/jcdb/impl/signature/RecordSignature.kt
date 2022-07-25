package org.utbot.jcdb.impl.signature

import org.objectweb.asm.signature.SignatureReader
import org.utbot.jcdb.api.ClasspathSet
import org.utbot.jcdb.api.Malformed
import org.utbot.jcdb.api.Raw
import org.utbot.jcdb.api.RecordComponentResolution

class RecordSignature(private val cp: ClasspathSet) : GenericTypeRegistrant {

    private lateinit var recordComponentType: GenericType

    override fun register(token: GenericType) {
        recordComponentType = token
    }

    protected fun resolve(): RecordComponentResolution {
        return RecordComponentResolutionImpl(recordComponentType)
    }

    companion object {
        fun of(signature: String?, cp: ClasspathSet): RecordComponentResolution {
            signature ?: return Raw
            val signatureReader = SignatureReader(signature)
            val visitor = RecordSignature(cp)
            return try {
                signatureReader.acceptType(GenericTypeExtractor(cp, visitor))
                visitor.resolve()
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }
    }
}
