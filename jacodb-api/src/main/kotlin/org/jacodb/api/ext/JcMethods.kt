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

@file:JvmName("JcMethods")

package org.jacodb.api.ext

import kotlinx.collections.immutable.toImmutableList
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode


/**
 * is method has `native` modifier
 */
val JcMethod.isNative: Boolean
    get() {
        return access and Opcodes.ACC_NATIVE != 0
    }

/**
 * is item has `synchronized` modifier
 */
val JcMethod.isSynchronized: Boolean
    get() {
        return access and Opcodes.ACC_SYNCHRONIZED != 0
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


/**
 * is method has `strictfp` modifier
 */
val JcMethod.isStrict: Boolean
    get() {
        return access and Opcodes.ACC_STRICT != 0
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

val JcMethod.humanReadableSignature: String
    get() {
        val params = parameters.joinToString(",") { it.type.typeName }
        return "${returnType.typeName} $name($params)"
    }

@get:JvmName("hasBody")
val JcMethod.hasBody: Boolean
    get() {
        return !isNative && !isAbstract && body().instructions.first != null
    }


val JcMethod.usedMethods: List<JcMethod> get() {
    val cp = enclosingClass.classpath
    val methodNode = body()
    val result = LinkedHashSet<JcMethod>()
    methodNode.instructions.forEach { instruction ->
        when (instruction) {
            is MethodInsnNode -> {
                val owner = Type.getObjectType(instruction.owner).className
                val clazz = cp.findClassOrNull(owner)
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
val JcMethod.usedFields: FieldUsagesResult get() {
    val cp = enclosingClass.classpath
    val methodNode = body()
    val reads = LinkedHashSet<JcField>()
    val writes = LinkedHashSet<JcField>()
    methodNode.instructions.forEach { instruction ->
        when (instruction) {
            is FieldInsnNode -> {
                val owner = Type.getObjectType(instruction.owner).className
                val clazz = cp.findClassOrNull(owner)
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
