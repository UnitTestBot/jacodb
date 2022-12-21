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

@file:JvmName("ClasspathExt")
package org.utbot.jacodb.api.ext

import kotlinx.collections.immutable.toImmutableList
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.utbot.jacodb.api.*

/**
 * find all methods used in bytecode of specified `method`
 * @param method method to analyze
 */
fun JcClasspath.findMethodsUsedIn(method: JcMethod): List<JcMethod> {
    val methodNode = method.body()
    val result = LinkedHashSet<JcMethod>()
    methodNode.instructions.forEach { instruction ->
        when (instruction) {
            is MethodInsnNode -> {
                val owner = Type.getObjectType(instruction.owner).className
                val clazz = findClassOrNull(owner)
                if (clazz != null) {
                    clazz.findMethodOrNull(instruction.name, instruction.desc)?.also {
                        result.add(it)
                    }
                }
            }
        }
    }
    return result.toImmutableList()
}

class FieldUsagesResult(
    val reads: List<JcField>,
    val writes: List<JcField>
)

/**
 * find all methods used in bytecode of specified `method`
 * @param method method to analyze
 */
fun JcClasspath.findFieldsUsedIn(method: JcMethod): FieldUsagesResult {
    val methodNode = method.body()
    val reads = LinkedHashSet<JcField>()
    val writes = LinkedHashSet<JcField>()
    methodNode.instructions.forEach { instruction ->
        when (instruction) {
            is FieldInsnNode -> {
                val owner = Type.getObjectType(instruction.owner).className
                val clazz = findClassOrNull(owner)
                if (clazz != null) {
                    val jcClass = clazz.findFieldOrNull(instruction.name)
                    if (jcClass != null) {
                        when (instruction.opcode) {
                            Opcodes.GETFIELD -> reads.add(jcClass)
                            Opcodes.GETSTATIC -> reads.add(jcClass)
                            Opcodes.PUTFIELD -> writes.add(jcClass)
                            Opcodes.PUTSTATIC -> writes.add(jcClass)
                        }
                    }
                }
            }
        }
    }
    return FieldUsagesResult(
        reads = reads.toImmutableList(),
        writes = writes.toImmutableList()
    )
}


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

/**
 * find a common supertype for a set of classes
 */
fun JcClasspath.findCommonSupertype(types: Set<JcType>, atLeast: JcType): JcType? = when {
    types.size == 1 -> types.first()
    types.all { it.typeName in integersMap } -> types.maxByOrNull { integersMap[it.typeName]!! }
    types.all { it is JcClassType } -> {
        val classes = types.map { it as JcClassType }
        val allHierarchies = classes.map { it.jcClass.allSuperHierarchy + it.jcClass }
        val allIntersect = allHierarchies.fold(allHierarchies[0]) { acc, set ->
            acc.intersect(set)
        }
        allIntersect.firstOrNull()?.toType() ?: anyType()
    }

    types.all { it is JcRefType } -> when {
        types.any { it is JcClassType } -> anyType()
        types.map { it as JcArrayType }.map { it.elementType }.toSet().size == 1 -> types.first()
        types.all { it is JcArrayType } -> {
            val components = types.map { (it as JcArrayType).elementType }.toSet()
            when (val merged = findCommonSupertype(components, atLeast)) {
                null -> anyType()
                else -> arrayTypeOf(merged)
            }
        }

        else -> anyType()
    }

    else -> null
}

private val integersMap
    get() = mapOf(
        PredefinedPrimitives.boolean to 1,
        PredefinedPrimitives.byte to 8,
        PredefinedPrimitives.char to 8,
        PredefinedPrimitives.short to 16,
        PredefinedPrimitives.int to 32,
        PredefinedPrimitives.long to 64
    )
