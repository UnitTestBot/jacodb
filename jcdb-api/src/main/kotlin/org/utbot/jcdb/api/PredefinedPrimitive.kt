package org.utbot.jcdb.api

import kotlinx.collections.immutable.persistentListOf
import org.objectweb.asm.Opcodes

object PredefinedPrimitives {

    val boolean = "boolean"
    val byte = "byte"
    val char = "char"
    val short = "short"
    val int = "int"
    val long = "long"
    val float = "float"
    val double = "double"
    val void = "void"

    private val values = persistentListOf(boolean, byte, char, short, int, long, float, double, void)
    private val valueSet = values.toHashSet()

    fun of(name: String, cp: Classpath): ClassId? {
        if (valueSet.contains(name)) {
            return PredefinedPrimitive(cp, name)
        }
        return null
    }

    fun matches(name: String): Boolean {
        return valueSet.contains(name)
    }
}

/**
 * Predefined primitive types
 */
class PredefinedPrimitive(override val classpath: Classpath, override val simpleName: String) : ClassId {

    override val name: String get() = simpleName
    override val location = null

    override suspend fun resolution() = Raw

    override suspend fun outerClass() = null

    override suspend fun outerMethod() = null

    override suspend fun isAnonymous() = false

    override suspend fun innerClasses() = emptyList<ClassId>()

    override suspend fun byteCode() = null

    override suspend fun methods() = emptyList<MethodId>()

    override suspend fun superclass() = null

    override suspend fun interfaces() = emptyList<ClassId>()

    override suspend fun annotations() = emptyList<AnnotationId>()

    override suspend fun fields() = emptyList<FieldId>()

    override suspend fun access() = Opcodes.ACC_PUBLIC

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PredefinedPrimitive

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

}

val Classpath.void get() = PredefinedPrimitive(this, PredefinedPrimitives.void)
val Classpath.boolean get() = PredefinedPrimitive(this, PredefinedPrimitives.boolean)
val Classpath.short get() = PredefinedPrimitive(this, PredefinedPrimitives.short)
val Classpath.int get() = PredefinedPrimitive(this, PredefinedPrimitives.int)
val Classpath.long get() = PredefinedPrimitive(this, PredefinedPrimitives.long)
val Classpath.float get() = PredefinedPrimitive(this, PredefinedPrimitives.float)
val Classpath.double get() = PredefinedPrimitive(this, PredefinedPrimitives.double)
val Classpath.byte get() = PredefinedPrimitive(this, PredefinedPrimitives.byte)
val Classpath.char get() = PredefinedPrimitive(this, PredefinedPrimitives.char)