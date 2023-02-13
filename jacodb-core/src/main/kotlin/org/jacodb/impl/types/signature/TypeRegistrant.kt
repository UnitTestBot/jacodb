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

import org.objectweb.asm.Opcodes
import org.objectweb.asm.signature.SignatureVisitor

internal interface TypeRegistrant {

    fun register(token: JvmType)

    open class RejectingSignatureVisitor : SignatureVisitor(Opcodes.ASM9) {

        override fun visitFormalTypeParameter(name: String) {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitClassBound(): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitInterfaceBound(): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitSuperclass(): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitInterface(): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitParameterType(): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitReturnType(): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitExceptionType(): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitBaseType(descriptor: Char) {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitTypeVariable(name: String) {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitArrayType(): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitClassType(name: String) {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitInnerClassType(name: String) {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitTypeArgument() {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitTypeArgument(wildcard: Char): SignatureVisitor {
            throw IllegalStateException(MESSAGE)
        }

        override fun visitEnd() {
            throw IllegalStateException(MESSAGE)
        }

        companion object {
            private const val MESSAGE = "Unexpected token in generic signature"
        }
    }
}