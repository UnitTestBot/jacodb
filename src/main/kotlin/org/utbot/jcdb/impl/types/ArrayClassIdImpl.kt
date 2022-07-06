package org.utbot.jcdb.impl.types

import kotlinx.collections.immutable.persistentListOf
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.utbot.jcdb.api.*
import org.utbot.jcdb.impl.index.findClassOrNull
import org.utbot.jcdb.impl.signature.Raw

/**
 * Predefined arrays of primitive types
 */
object PredefinedArrays {

    val boolean = ArrayClassIdImpl(PredefinedPrimitive.boolean)
    val byte = ArrayClassIdImpl(PredefinedPrimitive.byte)
    val char = ArrayClassIdImpl(PredefinedPrimitive.char)
    val short = ArrayClassIdImpl(PredefinedPrimitive.short)
    val int = ArrayClassIdImpl(PredefinedPrimitive.int)
    val long = ArrayClassIdImpl(PredefinedPrimitive.long)
    val float = ArrayClassIdImpl(PredefinedPrimitive.float)
    val double = ArrayClassIdImpl(PredefinedPrimitive.double)

    val values = persistentListOf(boolean, byte, char, short, int, long, float, double)

}

class ArrayClassIdImpl(override val elementClass: ClassId) : ArrayClassId {

    override val name = elementClass.simpleName + "[]"
    override val simpleName = elementClass.simpleName + "[]"

    override val location: ByteCodeLocation?
        get() = elementClass.location

    override val classpath: ClasspathSet
        get() = elementClass.classpath

    override suspend fun byteCode(): ClassNode? {
        return null
    }

    override suspend fun innerClasses() = emptyList<ClassId>()

    override suspend fun outerClass() = null

    override suspend fun isAnonymous() = false

    override suspend fun signature() = Raw

    override suspend fun outerMethod() = null

    override suspend fun methods() = emptyList<MethodId>()

    override suspend fun superclass(): ClassId {
        return elementClass.classpath.findClassOrNull<Any>() ?: throwClassNotFound<Any>()
    }

    override suspend fun interfaces() = emptyList<ClassId>()

    override suspend fun annotations() = emptyList<ClassId>()

    override suspend fun fields() = emptyList<FieldId>()

    override suspend fun access() = Opcodes.ACC_PUBLIC

}