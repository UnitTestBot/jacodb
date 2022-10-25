package org.utbot.jcdb.impl.types.signature

import org.objectweb.asm.signature.SignatureVisitor
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.Malformed
import org.utbot.jcdb.api.Pure
import org.utbot.jcdb.api.TypeResolution
import org.utbot.jcdb.impl.types.allVisibleTypeParameters
import org.utbot.jcdb.impl.types.substition.JvmTypeVisitor
import org.utbot.jcdb.impl.types.substition.fixDeclarationVisitor

internal class TypeSignature(jcClass: JcClassOrInterface) : Signature<TypeResolution>(jcClass) {

    private val interfaceTypes = ArrayList<JvmType>()
    private lateinit var superClass: JvmType

    override fun visitSuperclass(): SignatureVisitor {
        collectTypeParameter()
        return TypeExtractor(SuperClassRegistrant())
    }

    override fun visitInterface(): SignatureVisitor {
        return TypeExtractor(InterfaceTypeRegistrant())
    }

    override fun resolve(): TypeResolution {
        return TypeResolutionImpl(superClass, interfaceTypes, typeVariables)
    }

    private inner class SuperClassRegistrant : TypeRegistrant {

        override fun register(token: JvmType) {
            superClass = token
        }
    }

    private inner class InterfaceTypeRegistrant : TypeRegistrant {

        override fun register(token: JvmType) {
            interfaceTypes.add(token)
        }
    }


    companion object {

        private fun TypeResolutionImpl.apply(visitor: JvmTypeVisitor) = TypeResolutionImpl(
            visitor.visitType(superClass),
            interfaceType.map { visitor.visitType(it) },
            typeVariables.map { visitor.visitDeclaration(it) }
        )


        fun of(jcClass: JcClassOrInterface): TypeResolution {
            val signature = jcClass.signature ?: return Pure
            return try {
                of(signature, TypeSignature(jcClass))
            } catch (ignored: RuntimeException) {
                Malformed
            }
        }

        fun withDeclarations(jcClass: JcClassOrInterface): TypeResolution {
            val signature = jcClass.signature ?: return Pure
            return try {
                of(signature, TypeSignature(jcClass)).let {
                    if (it is TypeResolutionImpl) {
                        it.apply(jcClass.allVisibleTypeParameters().fixDeclarationVisitor)
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