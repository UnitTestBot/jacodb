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

import kotlinx.metadata.KmTypeParameter
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.utbot.jcdb.api.JcAccessible
import org.utbot.jcdb.api.Resolution

internal abstract class Signature<T : Resolution>(val owner: JcAccessible, private val kmTypeParameters: List<KmTypeParameter>?) :
    TypeRegistrant.RejectingSignatureVisitor(), TypeRegistrant {

    protected val typeVariables = ArrayList<JvmTypeParameterDeclaration>()
    protected var currentTypeParameter: String? = null
    protected var currentBounds: MutableList<JvmType>? = null

    override fun visitFormalTypeParameter(name: String) {
        collectTypeParameter()
        currentTypeParameter = name
        currentBounds = ArrayList()
    }

    override fun visitClassBound(): SignatureVisitor {
        return TypeExtractor(this)
    }

    override fun visitInterfaceBound(): SignatureVisitor {
        return TypeExtractor(this)
    }

    override fun register(token: JvmType) {
        checkNotNull(currentBounds) { "Did not expect $token before finding formal parameter" }
        currentBounds!!.add(token)
    }

    protected fun collectTypeParameter() {
        val current = currentTypeParameter
        if (current != null) {
            val toAdd = JvmTypeParameterDeclarationImpl(current, owner, currentBounds)
            typeVariables.add(kmTypeParameters?.let {
                toAdd.relaxWithKmTypeParameter(it[typeVariables.size])
            } ?: toAdd)
        }
    }

    abstract fun resolve(): T

    companion object {
        fun <S : Resolution> of(genericSignature: String?, visitor: Signature<S>): S {
            val signatureReader = SignatureReader(genericSignature)
            signatureReader.accept(visitor)
            return visitor.resolve()
        }
    }
}