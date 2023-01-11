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

@file:JvmName("Commons")
package org.utbot.jacodb.api.ext

import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.PredefinedPrimitives
import org.utbot.jacodb.api.throwClassNotFound
import java.util.*

fun String.jvmName(): String {
    return when {
        this == PredefinedPrimitives.boolean -> "Z"
        this == PredefinedPrimitives.byte -> "B"
        this == PredefinedPrimitives.char -> "C"
        this == PredefinedPrimitives.short -> "S"
        this == PredefinedPrimitives.int -> "I"
        this == PredefinedPrimitives.float -> "F"
        this == PredefinedPrimitives.long -> "J"
        this == PredefinedPrimitives.double -> "D"
        this == PredefinedPrimitives.void -> "V"
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
        this == "Z" -> PredefinedPrimitives.boolean
        this == "B" -> PredefinedPrimitives.byte
        this == "C" -> PredefinedPrimitives.char
        this == "S" -> PredefinedPrimitives.short
        this == "I" -> PredefinedPrimitives.int
        this == "F" -> PredefinedPrimitives.float
        this == "J" -> PredefinedPrimitives.long
        this == "D" -> PredefinedPrimitives.double
        this == "V" -> PredefinedPrimitives.void
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


fun JcClasspath.anyType(): JcClassType =
    findTypeOrNull("java.lang.Object") as? JcClassType ?: throwClassNotFound<Any>()


// call with SAFE. comparator works only on methods from one hierarchy
internal object UnsafeHierarchyMethodComparator : Comparator<JcMethod> {

    override fun compare(o1: JcMethod, o2: JcMethod): Int {
        return (o1.name + o1.description).compareTo(o2.name + o2.description)
    }
}
