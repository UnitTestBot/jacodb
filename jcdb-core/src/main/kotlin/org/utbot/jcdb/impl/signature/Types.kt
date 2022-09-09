package org.utbot.jcdb.impl.signature

import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.PredefinedPrimitive
import org.utbot.jcdb.api.boolean
import org.utbot.jcdb.api.byte
import org.utbot.jcdb.api.char
import org.utbot.jcdb.api.double
import org.utbot.jcdb.api.float
import org.utbot.jcdb.api.int
import org.utbot.jcdb.api.long
import org.utbot.jcdb.api.short
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.api.void


abstract class GenericType(val classpath: Classpath) {

    suspend fun findClass(name: String): ClassId {
        return classpath.findClassOrNull(name) ?: name.throwClassNotFound()
    }

}

abstract class GenericClassType(classpath: Classpath) : GenericType(classpath) {

    abstract suspend fun findClass(): ClassId

}

class GenericArray(cp: Classpath, val elementType: GenericType) : GenericClassType(cp) {

    override suspend fun findClass(): ClassId {
        if (elementType is GenericClassType) {
            return findClass(elementType.findClass().name + "[]")
        }
        return findClass("java.lang.Object")
    }
}

class ParameterizedType(
    cp: Classpath,
    val name: String,
    val parameterTypes: List<GenericType>
) : GenericClassType(cp) {

    class Nested(
        cp: Classpath,
        val name: String,
        val parameterTypes: List<GenericType>,
        val ownerType: GenericType
    ) : GenericClassType(cp) {
        override suspend fun findClass(): ClassId {
            return findClass(name)
        }
    }

    override suspend fun findClass(): ClassId {
        return findClass(name)
    }
}

class RawType(cp: Classpath, val name: String) : GenericClassType(cp) {
    override suspend fun findClass(): ClassId {
        return findClass(name)
    }
}

class TypeVariable(cp: Classpath, val symbol: String) : GenericType(cp)

sealed class BoundWildcard(cp: Classpath, val boundType: GenericType) : GenericType(cp) {
    class UpperBoundWildcard(cp: Classpath, boundType: GenericType) : BoundWildcard(cp, boundType)
    class LowerBoundWildcard(cp: Classpath, boundType: GenericType) : BoundWildcard(cp, boundType)
}

class UnboundWildcard(cp: Classpath) : GenericType(cp)

class PrimitiveType(cp: Classpath, val ref: PredefinedPrimitive) : GenericClassType(cp) {

    companion object {
        fun of(descriptor: Char, cp: Classpath): GenericType {
            return when (descriptor) {
                'V' -> PrimitiveType(cp, cp.void)
                'Z' -> PrimitiveType(cp, cp.boolean)
                'B' -> PrimitiveType(cp, cp.byte)
                'S' -> PrimitiveType(cp, cp.short)
                'C' -> PrimitiveType(cp, cp.char)
                'I' -> PrimitiveType(cp, cp.int)
                'J' -> PrimitiveType(cp, cp.long)
                'F' -> PrimitiveType(cp, cp.float)
                'D' -> PrimitiveType(cp, cp.double)
                else -> throw IllegalArgumentException("Not a valid primitive type descriptor: $descriptor")
            }
        }
    }

    override suspend fun findClass() = ref
}