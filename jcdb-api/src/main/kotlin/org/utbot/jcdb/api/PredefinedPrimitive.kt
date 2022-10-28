package org.utbot.jcdb.api

import kotlinx.collections.immutable.persistentListOf

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

    fun of(name: String, cp: JcClasspath): JcPrimitiveType? {
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
class PredefinedPrimitive(override val classpath: JcClasspath, override val typeName: String) : JcPrimitiveType {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PredefinedPrimitive

        if (typeName != other.typeName) return false

        return true
    }

    override fun hashCode(): Int {
        return typeName.hashCode()
    }

}

val JcClasspath.void get() = PredefinedPrimitive(this, PredefinedPrimitives.void)
val JcClasspath.boolean get() = PredefinedPrimitive(this, PredefinedPrimitives.boolean)
val JcClasspath.short get() = PredefinedPrimitive(this, PredefinedPrimitives.short)
val JcClasspath.int get() = PredefinedPrimitive(this, PredefinedPrimitives.int)
val JcClasspath.long get() = PredefinedPrimitive(this, PredefinedPrimitives.long)
val JcClasspath.float get() = PredefinedPrimitive(this, PredefinedPrimitives.float)
val JcClasspath.double get() = PredefinedPrimitive(this, PredefinedPrimitives.double)
val JcClasspath.byte get() = PredefinedPrimitive(this, PredefinedPrimitives.byte)
val JcClasspath.char get() = PredefinedPrimitive(this, PredefinedPrimitives.char)
