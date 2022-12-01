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
