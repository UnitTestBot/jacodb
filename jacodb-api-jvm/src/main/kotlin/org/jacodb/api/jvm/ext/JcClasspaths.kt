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

package org.jacodb.api.jvm.ext

import org.jacodb.api.core.TypeName
import org.jacodb.api.jvm.JcProject
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.NoClassInClasspathException
import org.jacodb.api.jvm.PredefinedJcPrimitive
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.throwClassNotFound

inline fun <reified T> JcProject.findClassOrNull(): JcClassOrInterface? {
    return findClassOrNull(T::class.java.name)
}

inline fun <reified T> JcProject.findTypeOrNull(): JcType? {
    return findClassOrNull(T::class.java.name)?.let {
        typeOf(it)
    }
}

fun JcProject.findTypeOrNull(typeName: TypeName): JcType? {
    return findTypeOrNull(typeName.typeName)
}


/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
fun JcProject.findClass(name: String): JcClassOrInterface {
    return findClassOrNull(name) ?: name.throwClassNotFound()
}

/**
 * find class. Tf there are none then throws `NoClassInClasspathException`
 * @throws NoClassInClasspathException
 */
inline fun <reified T> JcProject.findClass(): JcClassOrInterface {
    return findClassOrNull<T>() ?: throwClassNotFound<T>()
}

val JcProject.void: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Void)
val JcProject.boolean: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Boolean)
val JcProject.short: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Short)
val JcProject.int: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Int)
val JcProject.long: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Long)
val JcProject.float: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Float)
val JcProject.double: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Double)
val JcProject.byte: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Byte)
val JcProject.char: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Char)
val JcProject.nullType: JcPrimitiveType get() = PredefinedJcPrimitive(this, org.jacodb.api.jvm.PredefinedJcPrimitives.Null)