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

@file:JvmName("Api")
package org.utbot.jcdb.api.ext

import kotlinx.collections.immutable.toPersistentList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.utbot.jcdb.api.JcAccessible
import org.utbot.jcdb.api.JcArrayType
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcField
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcParameter
import org.utbot.jcdb.api.JcType
import org.utbot.jcdb.api.PredefinedPrimitives
import org.utbot.jcdb.api.boolean
import org.utbot.jcdb.api.byte
import org.utbot.jcdb.api.char
import org.utbot.jcdb.api.double
import org.utbot.jcdb.api.float
import org.utbot.jcdb.api.int
import org.utbot.jcdb.api.long
import org.utbot.jcdb.api.short
import org.utbot.jcdb.api.throwClassNotFound
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short
import java.util.*

/**
 * is item has `public` modifier
 */
val JcAccessible.isPublic: Boolean
    get() {
        return access and Opcodes.ACC_PUBLIC != 0
    }

/**
 * is item has `private` modifier
 */
val JcAccessible.isPrivate: Boolean
    get() {
        return access and Opcodes.ACC_PRIVATE != 0
    }

/**
 * is item has `protected` modifier
 */
val JcAccessible.isProtected: Boolean
    get() {
        return access and Opcodes.ACC_PROTECTED != 0
    }

/**
 * is item has `package` modifier
 */
val JcAccessible.isPackagePrivate: Boolean
    get() {
        return !isPublic && !isProtected && !isPrivate
    }

/**
 * is item has `static` modifier
 */
val JcAccessible.isStatic: Boolean
    get() {
        return access and Opcodes.ACC_STATIC != 0
    }

/**
 * is item has `final` modifier
 */
val JcAccessible.isFinal: Boolean
    get() {
        return access and Opcodes.ACC_FINAL != 0
    }

val JcClassOrInterface.isAnnotation: Boolean
    get() {
        return access and Opcodes.ACC_ANNOTATION != 0
    }

/**
 * is item has `synchronized` modifier
 */
val JcMethod.isSynchronized: Boolean
    get() {
        return access and Opcodes.ACC_SYNCHRONIZED != 0
    }

/**
 * is item has `volatile` modifier
 */
val JcField.isVolatile: Boolean
    get() {
        return access and Opcodes.ACC_VOLATILE != 0
    }

/**
 * is field has `transient` modifier
 */
val JcField.isTransient: Boolean
    get() {
        return access and Opcodes.ACC_TRANSIENT != 0
    }

/**
 * is method has `native` modifier
 */
val JcMethod.isNative: Boolean
    get() {
        return access and Opcodes.ACC_NATIVE != 0
    }

/**
 * is class is interface
 */
val JcClassOrInterface.isInterface: Boolean
    get() {
        return access and Opcodes.ACC_INTERFACE != 0
    }

/**
 * is item has `abstract` modifier
 */
val JcAccessible.isAbstract: Boolean
    get() {
        return access and Opcodes.ACC_ABSTRACT != 0
    }

/**
 * is method has `strictfp` modifier
 */
val JcMethod.isStrict: Boolean
    get() {
        return access and Opcodes.ACC_STRICT != 0
    }

/**
 * return true if method is constructor
 */
val JcMethod.isConstructor: Boolean
    get() {
        return name == "<init>"
    }

val JcMethod.isClassInitializer: Boolean
    get() {
        return name == "<clinit>"
    }

val JcAccessible.isSynthetic: Boolean
    get() {
        return access and Opcodes.ACC_SYNTHETIC != 0
    }

val JcClassOrInterface.isLocalOrAnonymous: Boolean
    get() {
        return outerMethod != null
    }

val JcClassOrInterface.isLocal: Boolean
    get() {
        return outerClass != null && !isAnonymous
    }

val JcClassOrInterface.isMemberClass: Boolean
    get() {
        return simpleBinaryName != null && !isLocalOrAnonymous
    }

val JcClassOrInterface.isEnum: Boolean
    get() {
        return access and Opcodes.ACC_ENUM != 0 && superClass?.name == Enum::class.java.name
    }

private val JcClassOrInterface.simpleBinaryName: String?
    get() {
        // top level class
        val enclosingClass = outerClass ?: return null
        // Otherwise, strip the enclosing class' name
        return try {
            name.substring(enclosingClass.name.length)
        } catch (ex: IndexOutOfBoundsException) {
            throw InternalError("Malformed class name", ex)
        }
    }

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
        classpath.boolean -> classpath.findTypeOrNull<java.lang.Boolean>() ?: throwClassNotFound<java.lang.Boolean>()
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

/**
 * @return all interfaces and classes retrieved recursively from this ClassId
 */
fun JcClassOrInterface.forEachSuperClasses(action: (JcClassOrInterface) -> Unit): List<JcClassOrInterface> {
    val parents = (interfaces + superClass).filterNotNull()
    parents.forEach {
        action(it)
    }
    val result = parents.toMutableSet()
    parents.forEach {
        it.forEachSuperClasses(action)
    }
    return result.toPersistentList()
}

val JcClassOrInterface.allSuperHierarchy: List<JcClassOrInterface>
    get() {
        val result = hashSetOf<JcClassOrInterface>()
        forEachSuperClasses {
            result.add(it)
        }
        return result.toPersistentList()
    }

infix fun JcClassOrInterface.isSubtypeOf(another: JcClassOrInterface): Boolean {
    if (another == classpath.findClassOrNull<Any>()) {
        return true
    }
    return another in allSuperHierarchy
}


/**
 * find field by name
 */
fun JcClassOrInterface.findFieldOrNull(name: String): JcField? = declaredFields.firstOrNull { it.name == name }

/**
 * find method by name and description
 */
fun JcClassOrInterface.findMethodOrNull(name: String, desc: String? = null): JcMethod? =
    declaredMethods.firstOrNull { it.name == name && (desc == null || it.description == desc) }

/**
 * find method by ASM node
 */
fun JcClassOrInterface.findMethodOrNull(methodNode: MethodNode): JcMethod? =
    declaredMethods.firstOrNull { it.name == methodNode.name && it.description == methodNode.desc }


/**
 * @return null if ClassId is not enum and enum value names otherwise
 */
val JcClassOrInterface.enumValues: List<JcField>?
    get() {
        if (isEnum) {
            return declaredFields.filter { it.isStatic && it.type.typeName == name }
        }
        return null
    }


val JcClassOrInterface.methods: List<JcMethod>
    get() {
        return methods(allMethods = true, fromSuperTypes = true, packageName = packageName)
    }

private fun JcClassOrInterface.methods(
    allMethods: Boolean,
    fromSuperTypes: Boolean,
    packageName: String
): List<JcMethod> {
    val classPackageName = this.packageName
    val methodSet = if (allMethods) {
        declaredMethods
    } else {
        declaredMethods.filter { !it.isConstructor && (it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName)) }
    }

    if (!fromSuperTypes) {
        return methodSet
    }
    val result = declaredMethods.toSortedSet(UnsafeHierarchyMethodComparator)
    result.addAll(
        superClass?.methods(false, fromSuperTypes = true, packageName).orEmpty()
    )
    result.addAll(
        interfaces.flatMap {
            it.methods(false, fromSuperTypes = true, packageName).orEmpty()
        }
    )
    return result.toList()
}

val JcClassOrInterface.constructors: List<JcMethod>
    get() {
        return declaredMethods.filter { it.isConstructor }
    }

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


val JcMethod.jvmSignature: String
    get() {
        return name + description
    }

val JcMethod.jcdbSignature: String
    get() {
        val params = parameters.joinToString(";") { it.type.typeName } + (";".takeIf { parameters.isNotEmpty() } ?: "")
        return "$name($params)${returnType.typeName};"
    }

const val NotNull = "org.jetbrains.annotations.NotNull"

val JcMethod.isNullable: Boolean
    get() {
        return !PredefinedPrimitives.matches(returnType.typeName) && annotations.all { !it.matches(NotNull) }
    }

val JcField.isNullable: Boolean
    get() {
        return !PredefinedPrimitives.matches(type.typeName) && annotations.all { !it.matches(NotNull) }
    }

val JcParameter.isNullable: Boolean
    get() {
        return !PredefinedPrimitives.matches(type.typeName) && annotations.all { !it.matches(NotNull) }
    }

fun JcClasspath.anyType(): JcClassType =
    findTypeOrNull("java.lang.Object") as? JcClassType ?: throwClassNotFound<Any>()


fun JcClassOrInterface.toType(): JcClassType {
    return classpath.typeOf(this) as JcClassType
}

val JcClassType.constructors get() = declaredMethods.filter { it.method.isConstructor }

val JcClassOrInterface.packageName get() = name.substringBeforeLast(".")


// call with SAFE. comparator works only on methods from one hierarchy
internal object UnsafeHierarchyMethodComparator : Comparator<JcMethod> {

    override fun compare(o1: JcMethod, o2: JcMethod): Int {
        return (o1.name + o1.description).compareTo(o2.name + o2.description)
    }
}
