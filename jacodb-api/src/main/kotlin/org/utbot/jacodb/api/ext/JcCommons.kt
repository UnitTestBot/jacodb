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

@file:JvmName("JcCommons")
package org.utbot.jacodb.api.ext

import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.PredefinedPrimitives
import org.utbot.jacodb.api.throwClassNotFound
import java.io.Serializable
import java.lang.Cloneable
import java.util.*

fun String.jvmName(): String {
    return when {
        this == PredefinedPrimitives.Boolean -> "Z"
        this == PredefinedPrimitives.Byte -> "B"
        this == PredefinedPrimitives.Char -> "C"
        this == PredefinedPrimitives.Short -> "S"
        this == PredefinedPrimitives.Int -> "I"
        this == PredefinedPrimitives.Float -> "F"
        this == PredefinedPrimitives.Long -> "J"
        this == PredefinedPrimitives.Double -> "D"
        this == PredefinedPrimitives.Void -> "V"
        endsWith("[]") -> {
            val elementName = substring(0, length - 2)
            "[" + elementName.jvmName()
        }

        else -> "L${this.replace('.', '/')};"
    }
}

val jvmPrimitiveNames = hashSetOf("Z", "B", "C", "S", "I", "F", "J", "D", "V")

fun String.jcdbName(): String {
    return when {
        this == "Z" -> PredefinedPrimitives.Boolean
        this == "B" -> PredefinedPrimitives.Byte
        this == "C" -> PredefinedPrimitives.Char
        this == "S" -> PredefinedPrimitives.Short
        this == "I" -> PredefinedPrimitives.Int
        this == "F" -> PredefinedPrimitives.Float
        this == "J" -> PredefinedPrimitives.Long
        this == "D" -> PredefinedPrimitives.Double
        this == "V" -> PredefinedPrimitives.Void
        startsWith("[") -> {
            val elementName = substring(1, length)
            elementName.jcdbName() + "[]"
        }

        startsWith("L") -> {
            substring(1, length - 1).replace('/', '.')
        }

        else -> this.replace('/', '.')
    }
}


val JcClasspath.objectType: JcClassType
    get() = findTypeOrNull<Any>() as? JcClassType ?: throwClassNotFound<Any>()

val JcClasspath.objectClass: JcClassOrInterface
    get() = findClass<Any>()

val JcClasspath.cloneableClass: JcClassOrInterface
    get() = findClass<Cloneable>()

val JcClasspath.serializableClass: JcClassOrInterface
    get() = findClass<Serializable>()


// call with SAFE. comparator works only on methods from one hierarchy
internal object UnsafeHierarchyMethodComparator : Comparator<JcMethod> {

    override fun compare(o1: JcMethod, o2: JcMethod): Int {
        return (o1.name + o1.description).compareTo(o2.name + o2.description)
    }
}
