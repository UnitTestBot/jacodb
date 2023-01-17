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

@file:JvmName("JcClasses")

package org.utbot.jacodb.api.ext

import kotlinx.collections.immutable.toPersistentList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcField
import org.utbot.jacodb.api.JcMethod


val JcClassOrInterface.isAnnotation: Boolean
    get() {
        return access and Opcodes.ACC_ANNOTATION != 0
    }

/**
 * is class is interface
 */
val JcClassOrInterface.isInterface: Boolean
    get() {
        return access and Opcodes.ACC_INTERFACE != 0
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

fun JcClassOrInterface.toType(): JcClassType {
    return classpath.typeOf(this) as JcClassType
}

val JcClassOrInterface.packageName get() = name.substringBeforeLast(".")


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


val JcClassOrInterface.allSuperHierarchy: LinkedHashSet<JcClassOrInterface>
    get() {
        val result = LinkedHashSet<JcClassOrInterface>()
        forEachSuperClasses {
            result.add(it)
        }
        return result
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

infix fun JcClassOrInterface.isSubClassOf(another: JcClassOrInterface): Boolean {
    if (another == classpath.findClassOrNull<Any>()) {
        return true
    }
    return another == this || another in allSuperHierarchy
}

val JcClassOrInterface.isKotlin: Boolean
    get() {
        return annotations.any { it.matches("kotlin.Metadata") }
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
