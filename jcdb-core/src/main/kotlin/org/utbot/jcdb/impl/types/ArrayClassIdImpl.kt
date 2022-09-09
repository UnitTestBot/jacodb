package org.utbot.jcdb.impl.types

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.utbot.jcdb.api.AnnotationId
import org.utbot.jcdb.api.ArrayClassId
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.ClassId
import org.utbot.jcdb.api.Classpath
import org.utbot.jcdb.api.FieldId
import org.utbot.jcdb.api.MethodId
import org.utbot.jcdb.api.Raw
import org.utbot.jcdb.api.boolean
import org.utbot.jcdb.api.byte
import org.utbot.jcdb.api.char
import org.utbot.jcdb.api.double
import org.utbot.jcdb.api.ext.findClassOrNull
import org.utbot.jcdb.api.float
import org.utbot.jcdb.api.int
import org.utbot.jcdb.api.long
import org.utbot.jcdb.api.short
import org.utbot.jcdb.api.throwClassNotFound

class ArrayClassIdImpl(override val elementClass: ClassId) : ArrayClassId {

    override val name = elementClass.name + "[]"
    override val simpleName = elementClass.simpleName + "[]"

    override val location: ByteCodeLocation?
        get() = elementClass.location

    override val classpath: Classpath
        get() = elementClass.classpath

    override suspend fun byteCode(): ClassNode? {
        return null
    }

    override suspend fun innerClasses() = emptyList<ClassId>()

    override suspend fun outerClass() = null

    override suspend fun isAnonymous() = false

    override suspend fun resolution() = Raw

    override suspend fun outerMethod() = null

    override suspend fun methods() = emptyList<MethodId>()

    override suspend fun superclass(): ClassId {
        return classpath.findClassOrNull<Any>() ?: throwClassNotFound<Any>()
    }

    override suspend fun interfaces() = emptyList<ClassId>()

    override suspend fun annotations() = emptyList<AnnotationId>()

    override suspend fun fields() = emptyList<FieldId>()

    override suspend fun access() = Opcodes.ACC_PUBLIC

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArrayClassIdImpl

        if (elementClass != other.elementClass) return false

        return true
    }

    override fun hashCode(): Int {
        return elementClass.hashCode()
    }

}

/**
 * Predefined arrays of primitive types
 */
val Classpath.booleanArray get() = ArrayClassIdImpl(boolean)
val Classpath.shortArray get() = ArrayClassIdImpl(short)
val Classpath.intArray get() = ArrayClassIdImpl(int)
val Classpath.longArray get() = ArrayClassIdImpl(long)
val Classpath.floatArray get() = ArrayClassIdImpl(float)
val Classpath.doubleArray get() = ArrayClassIdImpl(double)
val Classpath.byteArray get() = ArrayClassIdImpl(byte)
val Classpath.charArray get() = ArrayClassIdImpl(char)