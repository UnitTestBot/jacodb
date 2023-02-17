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

package org.jacodb.api.ext

import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassType
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode


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
fun JcClassOrInterface.findFieldOrNull(name: String): JcField? {
    return findElements(
        allowSearchAll = true,
        packageName = { packageName },
        getAccessibles = { declaredFields },
        nextHierarchy = { superClass?.let { listOf(it) } },
    ) {
        it.name == name
    }
}

fun JcClassOrInterface.findDeclaredFieldOrNull(name: String): JcField? = declaredFields.firstOrNull { it.name == name }

fun JcClassOrInterface.findDeclaredMethodOrNull(name: String, desc: String? = null): JcMethod? {
    return declaredMethods.firstOrNull { it.name == name && (desc == null || it.description == desc) }
}


/**
 * find method by name and description
 */
fun JcClassOrInterface.findMethodOrNull(name: String, desc: String? = null): JcMethod? {
    // let's find method based on strict hierarchy
    // if method is not found then it's defined in interfaces
    val predicate: (JcMethod) -> Boolean = {
        it.name == name && (desc == null || it.description == desc)
    }
    val method = findElements(
        packageName = { packageName },
        getAccessibles = { declaredMethods },
        nextHierarchy = { superClass?.let { listOf(it) } },
        predicate = predicate
    )
    if (method != null) {
        return method
    }
    // let's search interfaces
    return findElements(
        packageName = { packageName },
        getAccessibles = { declaredMethods },
        nextHierarchy = { interfaces },
        predicate = predicate
    )
}

/**
 * find method by ASM node
 */
fun JcClassOrInterface.findMethodOrNull(methodNode: MethodNode): JcMethod? =
    findMethodOrNull(methodNode.name, methodNode.desc)


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

val JcClassOrInterface.fields: List<JcField>
    get() {
        return fields(allFields = true, fromSuperTypes = true, packageName = packageName)
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
    val result = methodSet.toSortedSet(UnsafeHierarchyMethodComparator)
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

private fun JcClassOrInterface.fields(
    allFields: Boolean,
    fromSuperTypes: Boolean,
    packageName: String
): List<JcField> {
    val classPackageName = this.packageName
    val fieldSet = if (allFields) {
        declaredFields
    } else {
        declaredFields.filter { (it.isPublic || it.isProtected || (it.isPackagePrivate && packageName == classPackageName)) }
    }

    if (!fromSuperTypes) {
        return fieldSet
    }
    val result = fieldSet.toMutableSet()
    result.addAll(
        superClass?.fields(false, fromSuperTypes = true, packageName).orEmpty()
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

fun JcClassOrInterface.isAccessibleFrom(packageName: String): Boolean {
    return when {
        isAnonymous || isPrivate -> false
        isPublic -> true
        isProtected || isPackagePrivate -> packageName == this.packageName
        else -> false
    }
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
