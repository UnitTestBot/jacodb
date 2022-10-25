package org.utbot.jcdb.impl.types

import org.utbot.jcdb.api.JcAccessible
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcRefType
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.JcTypeVariableDeclaration
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.anyType
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.types.signature.JvmArrayType
import org.utbot.jcdb.impl.types.signature.JvmBoundWildcard
import org.utbot.jcdb.impl.types.signature.JvmClassRefType
import org.utbot.jcdb.impl.types.signature.JvmParameterizedType
import org.utbot.jcdb.impl.types.signature.JvmPrimitiveType
import org.utbot.jcdb.impl.types.signature.JvmType
import org.utbot.jcdb.impl.types.signature.JvmTypeVariable
import org.utbot.jcdb.impl.types.signature.JvmUnboundWildcard

internal fun JcClasspath.typeOf(jvmType: JvmType, parameters: List<JvmType>? = null): JcType {
    return when (jvmType) {
        is JvmPrimitiveType -> {
            PredefinedPrimitives.of(jvmType.ref, this)
                ?: throw IllegalStateException("primitive type ${jvmType.ref} not found")
        }

        is JvmClassRefType -> typeOf(findClass(jvmType.name))
        is JvmArrayType -> arrayTypeOf(typeOf(jvmType.elementType))
        is JvmParameterizedType -> {
            val clazz = findClass(jvmType.name)
            JcClassTypeImpl(
                clazz,
                null,
                parameters ?: jvmType.parameterTypes,
                nullable = true
            )
        }

        is JvmParameterizedType.JvmNestedType -> {
            val clazz = findClass(jvmType.name)
            val outerParameters = (jvmType.ownerType as? JvmParameterizedType)?.parameterTypes
            val outerType = typeOf(jvmType.ownerType, parameters ?: outerParameters)
            JcClassTypeImpl(
                clazz,
                outerType as JcClassTypeImpl,
                jvmType.parameterTypes,
                nullable = true
            )
        }

        is JvmTypeVariable -> {
            val declaration = jvmType.declaration
            if (declaration != null) {
                JcTypeVariableImpl(this, declaration.asJcDeclaration(declaration.owner), true)
            } else {
                anyType()
            }
        }

        is JvmUnboundWildcard -> JcUnboundWildcardImpl(this)
        is JvmBoundWildcard.JvmUpperBoundWildcard -> JcBoundedWildcardImpl(
            upperBounds = listOf(typeOf(jvmType.bound) as JcRefType), lowerBounds = emptyList(), true
        )

        is JvmBoundWildcard.JvmLowerBoundWildcard -> JcBoundedWildcardImpl(
            upperBounds = emptyList(), lowerBounds = listOf(typeOf(jvmType.bound) as JcRefType), true
        )
    }
}

class JcTypeVariableDeclarationImpl(
    override val symbol: String,
    override val bounds: List<JcRefType>,
    override val owner: JcAccessible
) : JcTypeVariableDeclaration
