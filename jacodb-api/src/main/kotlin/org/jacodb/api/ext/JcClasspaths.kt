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

@file:JvmName("JcClasspaths")

package org.jacodb.api.ext

import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcType
import org.jacodb.api.NoClassInClasspathException
import org.jacodb.api.PredefinedPrimitive
import org.jacodb.api.PredefinedPrimitives
import org.jacodb.api.TypeName
import org.jacodb.api.throwClassNotFound

inline fun <reified T> JcClasspath.findClassOrNull(): JcClassOrInterface? {
    return findClassOrNull(T::class.java.name)
}

inline fun <reified T> JcClasspath.findTypeOrNull(): JcType? {
    return findClassOrNull(T::class.java.name)?.let {
        typeOf(it)
    }
}

fun JcClasspath.findTypeOrNull(typeName: TypeName): JcType? {
    return findTypeOrNull(typeName.typeName)
}


/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
fun JcClasspath.findClass(name: String): JcClassOrInterface {
    return findClassOrNull(name) ?: name.throwClassNotFound()
}

/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
inline fun <reified T> JcClasspath.findClass(): JcClassOrInterface {
    return findClassOrNull<T>() ?: throwClassNotFound<T>()
}


val JcClasspath.void: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Void)
val JcClasspath.boolean: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Boolean)
val JcClasspath.short: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Short)
val JcClasspath.int: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Int)
val JcClasspath.long: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Long)
val JcClasspath.float: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Float)
val JcClasspath.double: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Double)
val JcClasspath.byte: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Byte)
val JcClasspath.char: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Char)
val JcClasspath.nullType: JcPrimitiveType get() = PredefinedPrimitive(this, PredefinedPrimitives.Null)