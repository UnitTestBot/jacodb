/*
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

package org.utbot.jacodb.api

import kotlinx.collections.immutable.persistentListOf

object PredefinedPrimitives {

    const val Boolean = "boolean"

    const val Byte = "byte"
    const val Char = "char"
    const val Short = "short"
    const val Int = "int"
    const val Long = "long"
    const val Float = "float"
    const val Double = "double"
    const val Void = "void"
    const val Null = "null"

    private val values = persistentListOf(Boolean, Byte, Char, Short, Int, Long, Float, Double, Void, Null)
    private val valueSet = values.toHashSet()

    @JvmStatic
    fun of(name: String, cp: JcClasspath, annotations: List<JcAnnotation> = listOf()): JcPrimitiveType? {
        if (valueSet.contains(name)) {
            return PredefinedPrimitive(cp, name, annotations)
        }
        return null
    }

    @JvmStatic
    fun matches(name: String): Boolean {
        return valueSet.contains(name)
    }
}

/**
 * Predefined primitive types
 */
class PredefinedPrimitive(override val classpath: JcClasspath, override val typeName: String,
                          override val annotations: List<JcAnnotation> = listOf()) : JcPrimitiveType {

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

    override fun copyWithAnnotations(annotations: List<JcAnnotation>): JcType =
        PredefinedPrimitive(classpath, typeName, annotations)
}

val JcClasspath.void get() = PredefinedPrimitive(this, PredefinedPrimitives.Void)
val JcClasspath.boolean get() = PredefinedPrimitive(this, PredefinedPrimitives.Boolean)
val JcClasspath.short get() = PredefinedPrimitive(this, PredefinedPrimitives.Short)
val JcClasspath.int get() = PredefinedPrimitive(this, PredefinedPrimitives.Int)
val JcClasspath.long get() = PredefinedPrimitive(this, PredefinedPrimitives.Long)
val JcClasspath.float get() = PredefinedPrimitive(this, PredefinedPrimitives.Float)
val JcClasspath.double get() = PredefinedPrimitive(this, PredefinedPrimitives.Double)
val JcClasspath.byte get() = PredefinedPrimitive(this, PredefinedPrimitives.Byte)
val JcClasspath.char get() = PredefinedPrimitive(this, PredefinedPrimitives.Char)