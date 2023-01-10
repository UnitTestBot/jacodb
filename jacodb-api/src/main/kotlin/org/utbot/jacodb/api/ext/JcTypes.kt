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

@file:JvmName("JcTypes")
package org.utbot.jacodb.api.ext

import org.utbot.jacodb.api.JcArrayType
import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcType
import org.utbot.jacodb.api.boolean
import org.utbot.jacodb.api.byte
import org.utbot.jacodb.api.char
import org.utbot.jacodb.api.double
import org.utbot.jacodb.api.float
import org.utbot.jacodb.api.int
import org.utbot.jacodb.api.long
import org.utbot.jacodb.api.short
import org.utbot.jacodb.api.throwClassNotFound
import java.lang.Boolean
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short


val JcClassType.constructors get() = declaredMethods.filter { it.method.isConstructor }

/**
 * @return element class in case of `this` is ArrayClass
 */
val JcType.ifArrayGetElementClass: JcType?
    get() {
        return when (this) {
            is JcArrayType -> elementType
            else -> null
        }
    }

/**
 * unboxes `this` class. That means that for 'java.lang.Integet' will be returned `PredefinedPrimitive.int`
 * and for `java.lang.String` will be returned `java.lang.String`
 */
fun JcType.unboxIfNeeded(): JcType {
    return when (typeName) {
        "java.lang.Boolean" -> classpath.boolean
        "java.lang.Byte" -> classpath.byte
        "java.lang.Char" -> classpath.char
        "java.lang.Short" -> classpath.short
        "java.lang.Integer" -> classpath.int
        "java.lang.Long" -> classpath.long
        "java.lang.Float" -> classpath.float
        "java.lang.Double" -> classpath.double
        else -> this
    }
}

/**
 * unboxes `this` class. That means that for 'java.lang.Integet' will be returned `PredefinedPrimitive.int`
 * and for `java.lang.String` will be returned `java.lang.String`
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
fun JcType.autoboxIfNeeded(): JcType {
    return when (this) {
        classpath.boolean -> classpath.findTypeOrNull<java.lang.Boolean>() ?: throwClassNotFound<Boolean>()
        classpath.byte -> classpath.findTypeOrNull<java.lang.Byte>() ?: throwClassNotFound<Byte>()
        classpath.char -> classpath.findTypeOrNull<Character>() ?: throwClassNotFound<Character>()
        classpath.short -> classpath.findTypeOrNull<java.lang.Short>() ?: throwClassNotFound<Short>()
        classpath.int -> classpath.findTypeOrNull<Integer>() ?: throwClassNotFound<Integer>()
        classpath.long -> classpath.findTypeOrNull<java.lang.Long>() ?: throwClassNotFound<Long>()
        classpath.float -> classpath.findTypeOrNull<java.lang.Float>() ?: throwClassNotFound<Float>()
        classpath.double -> classpath.findTypeOrNull<java.lang.Double>() ?: throwClassNotFound<Double>()
        else -> this
    }
}
