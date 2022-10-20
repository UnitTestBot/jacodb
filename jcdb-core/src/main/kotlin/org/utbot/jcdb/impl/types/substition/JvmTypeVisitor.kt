package org.utbot.jcdb.impl.types.substition

import org.utbot.jcdb.impl.types.signature.JvmArrayType
import org.utbot.jcdb.impl.types.signature.JvmBoundWildcard.JvmLowerBoundWildcard
import org.utbot.jcdb.impl.types.signature.JvmBoundWildcard.JvmUpperBoundWildcard
import org.utbot.jcdb.impl.types.signature.JvmClassRefType
import org.utbot.jcdb.impl.types.signature.JvmParameterizedType
import org.utbot.jcdb.impl.types.signature.JvmPrimitiveType
import org.utbot.jcdb.impl.types.signature.JvmType
import org.utbot.jcdb.impl.types.signature.JvmTypeParameterDeclaration
import org.utbot.jcdb.impl.types.signature.JvmTypeParameterDeclarationImpl
import org.utbot.jcdb.impl.types.signature.JvmTypeVariable
import org.utbot.jcdb.impl.types.signature.JvmUnboundWildcard

internal interface JvmTypeVisitor {

    fun visitType(type: JvmType): JvmType {
        return when (type) {
            is JvmPrimitiveType -> type
            is JvmLowerBoundWildcard -> visitLowerBound(type)
            is JvmUpperBoundWildcard -> visitUpperBound(type)
            is JvmParameterizedType -> visitParameterizedType(type)
            is JvmArrayType -> visitArrayType(type)
            is JvmClassRefType -> visitClassRef(type)
            is JvmTypeVariable -> visitTypeVariable(type)
            is JvmUnboundWildcard -> type
            is JvmParameterizedType.JvmNestedType -> visitNested(type)
        }
    }


    fun visitUpperBound(type: JvmUpperBoundWildcard): JvmType {
        return JvmUpperBoundWildcard(visitType(type.bound))
    }

    fun visitLowerBound(type: JvmLowerBoundWildcard): JvmType {
        return JvmLowerBoundWildcard(visitType(type.bound))
    }

    fun visitArrayType(type: JvmArrayType): JvmType {
        return JvmArrayType(visitType(type.elementType))
    }

    fun visitTypeVariable(type: JvmTypeVariable): JvmType {
        return type
    }

    fun visitClassRef(type: JvmClassRefType): JvmType {
        return type
    }

    fun visitNested(type: JvmParameterizedType.JvmNestedType): JvmType {
        return JvmParameterizedType.JvmNestedType(
            type.name,
            type.parameterTypes.map { visitType(it) },
            visitType(type.ownerType)
        )
    }

    fun visitParameterizedType(type: JvmParameterizedType): JvmType {
        return JvmParameterizedType(type.name, type.parameterTypes.map { visitType(it) })
    }

    fun visitDeclaration(declaration: JvmTypeParameterDeclaration): JvmTypeParameterDeclaration {
        return JvmTypeParameterDeclarationImpl(
            declaration.symbol,
            declaration.owner,
            declaration.bounds?.map { visitType(it) }
        )
    }


}