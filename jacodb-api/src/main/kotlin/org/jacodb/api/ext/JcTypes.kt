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

package org.jacodb.api.ext

import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcPrimitiveType
import org.jacodb.api.JcRefType
import org.jacodb.api.JcType
import org.jacodb.api.JcTypedField
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.TypeNotFoundException
import org.jacodb.api.throwClassNotFound
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
val JcType.ifArrayGetElementType: JcType?
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
        classpath.boolean -> classpath.findTypeOrNull<Boolean>() ?: throwClassNotFound<Boolean>()
        classpath.byte -> classpath.findTypeOrNull<Byte>() ?: throwClassNotFound<Byte>()
        classpath.char -> classpath.findTypeOrNull<Character>() ?: throwClassNotFound<Character>()
        classpath.short -> classpath.findTypeOrNull<Short>() ?: throwClassNotFound<Short>()
        classpath.int -> classpath.findTypeOrNull<Integer>() ?: throwClassNotFound<Integer>()
        classpath.long -> classpath.findTypeOrNull<Long>() ?: throwClassNotFound<Long>()
        classpath.float -> classpath.findTypeOrNull<Float>() ?: throwClassNotFound<Float>()
        classpath.double -> classpath.findTypeOrNull<Double>() ?: throwClassNotFound<Double>()
        else -> this
    }
}

val JcArrayType.deepestElementType: JcType
    get() {
        var type = elementType
        while (type is JcArrayType) {
            val et = elementType.ifArrayGetElementType ?: return type
            type = et
        }
        return type
    }

fun JcType.isAssignable(declaration: JcType): kotlin.Boolean {
    val nullType = classpath.nullType
    if (this == declaration) {
        return true
    }
    return when {
        declaration == nullType -> false
        this == nullType -> declaration is JcRefType
        this is JcClassType ->
            when (declaration) {
                classpath.objectType -> true
                is JcClassType -> jcClass.isSubClassOf(declaration.jcClass)
                is JcPrimitiveType -> unboxIfNeeded() == declaration
                else -> false
            }

        this is JcPrimitiveType -> {
            when (declaration) {
                classpath.objectType -> true
                classpath.short -> this == classpath.short || this == classpath.byte
                classpath.int -> this == classpath.int || this == classpath.short || this == classpath.byte
                classpath.long -> this == classpath.long || this == classpath.int || this == classpath.short || this == classpath.byte
                classpath.float -> this == classpath.float || this == classpath.long || this == classpath.int || this == classpath.short || this == classpath.byte
                classpath.double -> this == classpath.double || this == classpath.float || this == classpath.long || this == classpath.int || this == classpath.short || this == classpath.byte
                !is JcPrimitiveType -> declaration.unboxIfNeeded() == this
                else -> false
            }
        }

        this is JcArrayType -> {
            when (declaration) {
                // From Java Language Spec 2nd ed., Chapter 10, Arrays
                classpath.objectType -> true
                classpath.serializableClass.toType() -> true
                classpath.cloneableClass.toType() -> true
                is JcArrayType -> {
                    // boolean[][] can be stored in a Object[].
                    // Interface[] can be stored in a Object[]
                    if (dimensions == declaration.dimensions) {
                        val thisElement = deepestElementType
                        val declarationElement = declaration.deepestElementType
                        when {
                            thisElement is JcRefType && declarationElement is JcRefType -> thisElement.jcClass.isSubClassOf(
                                declarationElement.jcClass
                            )

                            else -> false
                        }
                    } else if (dimensions > declaration.dimensions) {
                        val type = declaration.deepestElementType
                        if (type is JcRefType) {
                            // From Java Language Spec 2nd ed., Chapter 10, Arrays
                            type == classpath.objectType || type == classpath.serializableClass.toType() || type == classpath.cloneableClass.toType()
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }

                else -> false
            }
        }

        else -> false
    }
}

/**
 * find field by name
 *
 * @param name field name
 */
fun JcClassType.findFieldOrNull(name: String): JcTypedField? {
    return lookup.field(name)
}

/**
 * find method by name and description
 *
 * @param name method name
 * @param desc method description
 */
fun JcClassType.findMethodOrNull(name: String, desc: String): JcTypedMethod? {
    return lookup.method(name, desc)
}

/**
 * find method by name and description
 *
 * This method doesn't support [org.jacodb.impl.features.classpaths.UnknownClasses] feature.
 */
fun JcClassType.findMethodOrNull(predicate: (JcTypedMethod) -> kotlin.Boolean): JcTypedMethod? {
    // let's find method based on strict hierarchy
    // if method is not found then it's defined in interfaces
    return methods.firstOrNull(predicate)
}

val JcTypedMethod.humanReadableSignature: String
    get() {
        val params = parameters.joinToString(",") { it.type.typeName }
        val generics = typeParameters.takeIf { it.isNotEmpty() }?.let {
            it.joinToString(prefix = "<", separator = ",", postfix = ">") { it.symbol }
        } ?: ""
        return "${enclosingType.typeName}#$generics$name($params):${returnType.typeName}"
    }

fun JcClasspath.findType(name: String): JcType {
    return findTypeOrNull(name) ?: throw TypeNotFoundException(name)
}
