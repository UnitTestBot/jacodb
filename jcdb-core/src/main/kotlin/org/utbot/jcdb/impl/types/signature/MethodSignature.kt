package org.utbot.jcdb.impl.types.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.Malformed
import org.utbot.jcdb.api.MethodResolution
import org.utbot.jcdb.api.Pure
import org.utbot.jcdb.impl.types.substition.JvmTypeVisitor
import org.utbot.jcdb.impl.types.substition.VisitorContext
import org.utbot.jcdb.impl.types.typeParameters

internal class MethodSignature(method: JcMethod) : Signature<MethodResolution>(method) {

    private val parameterTypes = ArrayList<JvmType>()
    private val exceptionTypes = ArrayList<JvmClassRefType>()

    private lateinit var returnType: JvmType

    override fun visitParameterType(): SignatureVisitor {
        return TypeExtractor(ParameterTypeRegistrant())
    }

    override fun visitReturnType(): SignatureVisitor {
        collectTypeParameter()
        return TypeExtractor(ReturnTypeTypeRegistrant())
    }

    override fun visitExceptionType(): SignatureVisitor {
        return TypeExtractor(ExceptionTypeRegistrant())
    }

    override fun resolve(): MethodResolution {
        return MethodResolutionImpl(
            returnType,
            parameterTypes,
            exceptionTypes,
            typeVariables
        )
    }

    private inner class ParameterTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            parameterTypes.add(token)
        }
    }

    private inner class ReturnTypeTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            returnType = token
        }
    }

    private inner class ExceptionTypeRegistrant : TypeRegistrant {
        override fun register(token: JvmType) {
            exceptionTypes.add(token as JvmClassRefType)
        }
    }

    companion object {

        private fun MethodResolutionImpl.apply(visitor: JvmTypeVisitor) = MethodResolutionImpl(
            visitor.visitType(returnType),
            parameterTypes.map { visitor.visitType(it) },
            exceptionTypes,
            typeVariables.map { visitor.visitDeclaration(it) }
        )

        fun of(jcMethod: JcMethod): MethodResolution {
            val signature = jcMethod.signature
            signature ?: return Pure
            return try {
                of(signature, MethodSignature(jcMethod)).let {
                    if (it is MethodResolutionImpl) {
                        val declarations =
                            (jcMethod.enclosingClass.typeParameters + it.typeVariables).associateBy { it.symbol }
                        val fixDeclarationVisitor = object : JvmTypeVisitor {

                            override fun visitTypeVariable(type: JvmTypeVariable, context: VisitorContext): JvmType {
                                type.declaration = declarations[type.symbol]!!
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