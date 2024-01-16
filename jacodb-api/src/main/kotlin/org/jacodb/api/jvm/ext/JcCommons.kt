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

package org.jacodb.api.jvm.ext

import org.jacodb.api.jvm.throwClassNotFound
import org.jacodb.api.jvm.JcAnnotated
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.PredefinedJcPrimitives
import java.io.Serializable

fun String.jvmName(): String {
    return when {
        this == PredefinedJcPrimitives.Boolean -> "Z"
        this == PredefinedJcPrimitives.Byte -> "B"
        this == PredefinedJcPrimitives.Char -> "C"
        this == PredefinedJcPrimitives.Short -> "S"
        this == PredefinedJcPrimitives.Int -> "I"
        this == PredefinedJcPrimitives.Float -> "F"
        this == PredefinedJcPrimitives.Long -> "J"
        this == PredefinedJcPrimitives.Double -> "D"
        this == PredefinedJcPrimitives.Void -> "V"
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
        this == "Z" -> PredefinedJcPrimitives.Boolean
        this == "B" -> PredefinedJcPrimitives.Byte
        this == "C" -> PredefinedJcPrimitives.Char
        this == "S" -> PredefinedJcPrimitives.Short
        this == "I" -> PredefinedJcPrimitives.Int
        this == "F" -> PredefinedJcPrimitives.Float
        this == "J" -> PredefinedJcPrimitives.Long
        this == "D" -> PredefinedJcPrimitives.Double
        this == "V" -> PredefinedJcPrimitives.Void
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


val JcProject.objectType: JcClassType
    get() = findTypeOrNull<Any>() as? JcClassType ?: throwClassNotFound<Any>()

val JcProject.objectClass: JcClassOrInterface
    get() = findClass<Any>()

val JcProject.cloneableClass: JcClassOrInterface
    get() = findClass<Cloneable>()

val JcProject.serializableClass: JcClassOrInterface
    get() = findClass<Serializable>()


// call with SAFE. comparator works only on methods from one hierarchy
internal object UnsafeHierarchyMethodComparator : Comparator<JcMethod> {

    override fun compare(o1: JcMethod, o2: JcMethod): Int {
        return (o1.name + o1.description).compareTo(o2.name + o2.description)
    }
}

fun JcAnnotated.hasAnnotation(className: String): Boolean {
    return annotations.any { it.matches(className) }
}

fun JcAnnotated.annotation(className: String): JcAnnotation? {
    return annotations.firstOrNull { it.matches(className) }
}