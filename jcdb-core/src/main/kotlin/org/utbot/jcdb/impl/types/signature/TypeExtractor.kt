/**
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

import org.objectweb.asm.signature.SignatureVisitor
import org.utbot.jcdb.impl.types.signature.JvmPrimitiveType.Companion.of
import org.utbot.jcdb.impl.types.signature.TypeExtractor.IncompleteToken.InnerClass
import org.utbot.jcdb.impl.types.signature.TypeExtractor.IncompleteToken.TopLevelType
import org.utbot.jcdb.impl.types.signature.TypeRegistrant.RejectingSignatureVisitor

internal class TypeExtractor(private val typeRegistrant: TypeRegistrant) :
    RejectingSignatureVisitor(),
    TypeRegistrant {

    private lateinit var incompleteToken: IncompleteToken

    override fun visitBaseType(descriptor: Char) {
        typeRegistrant.register(of(descriptor))
    }

    override fun visitTypeVariable(name: String) {
        typeRegistrant.register(JvmTypeVariable(name))
    }

    override fun visitArrayType(): SignatureVisitor {
        return TypeExtractor(this)
    }

    override fun register(token: JvmType) {
        typeRegistrant.register(JvmArrayType(token))
    }

    override fun visitClassType(name: String) {
        incompleteToken = TopLevelType(name)
    }

    override fun visitInnerClassType(name: String) {
        incompleteToken = InnerClass(name, incompleteToken)
    }

    override fun visitTypeArgument() {
        incompleteToken.appendPlaceholder()
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor {
        return when (wildcard) {
            SUPER -> incompleteToken.appendLowerBound()
            EXTENDS -> incompleteToken.appendUpperBound()
            INSTANCEOF -> incompleteToken.appendDirectBound()
            else -> throw IllegalArgumentException("Unknown wildcard: $wildcard")
        }
    }

    override fun visitEnd() {
        incompleteToken.toToken()?.let {
            typeRegistrant.register(it)
        }
    }

    interface IncompleteToken {

        fun appendLowerBound(): SignatureVisitor
        fun appendUpperBound(): SignatureVisitor
        fun appendDirectBound(): SignatureVisitor
        fun appendPlaceholder()

        val isParameterized: Boolean
        val name: String

        fun toToken(): JvmType?

        abstract class AbstractBase() : IncompleteToken {

            protected val parameters = ArrayList<JvmType>()


            override fun appendDirectBound(): SignatureVisitor {
                return TypeExtractor(DirectBound())
            }

            override fun appendUpperBound(): SignatureVisitor {
                return TypeExtractor(UpperBound())
            }

            override fun appendLowerBound(): SignatureVisitor {
                return TypeExtractor(LowerBound())
            }

            override fun appendPlaceholder() {
                parameters.add(JvmUnboundWildcard)
            }

            protected inner class DirectBound : TypeRegistrant {
                override fun register(token: JvmType) {
                    parameters.add(token)
                }
            }

            protected inner class UpperBound : TypeRegistrant {
                override fun register(token: JvmType) {
                    parameters.add(JvmBoundWildcard.JvmUpperBoundWildcard(token))
                }
            }

            protected inner class LowerBound : TypeRegistrant {
                override fun register(token: JvmType) {
                    parameters.add(JvmBoundWildcard.JvmLowerBoundWildcard(token))
                }
            }
        }

        class TopLevelType(private val internalName: String) : AbstractBase() {

            override fun toToken(): JvmType {
                return if (isParameterized) JvmParameterizedType(name, parameters) else JvmClassRefType(name)
            }

            override val isParameterized: Boolean
                get() {
                    return parameters.isNotEmpty()
                }

            override val name: String
                get() {
                    return internalName.replace('/', '.')
                }
        }

        class InnerClass(private val internalName: String, private val outerTypeToken: IncompleteToken?) :
            AbstractBase() {
            override fun toToken(): JvmType {
                return if (isParameterized || outerTypeToken!!.isParameterized) JvmParameterizedType.JvmNestedType(
                    name,
                    parameters,
                    outerTypeToken!!.toToken()!!
                ) else JvmClassRefType(name)
            }

            override val isParameterized: Boolean
                get() {
                    return parameters.isNotEmpty() || !outerTypeToken!!.isParameterized
                }

            override val name: String
                get() {
                    return outerTypeToken!!.name + INNER_CLASS_SEPARATOR + internalName.replace('/', '.')
                }

            companion object {
                private const val INNER_CLASS_SEPARATOR = '$'
            }
        }
    }
}