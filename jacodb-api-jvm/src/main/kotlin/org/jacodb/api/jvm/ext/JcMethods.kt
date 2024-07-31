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

package org.jacodb.api.jvm.ext

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.util.*


const val CONSTRUCTOR = "<init>"

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
        return "${enclosingClass.name}#$name($params):${returnType.typeName}"
    }

@get:JvmName("hasBody")
val JcMethod.hasBody: Boolean
    get() {
        return !isNative && !isAbstract && withAsmNode { it.instructions.first != null }
    }


val JcMethod.usedMethods: List<JcMethod>
    get() {
        val cp = enclosingClass.classpath
        val result = LinkedHashSet<JcMethod>()
        withAsmNode { methodNode ->
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
        }
        return result.toList()
    }

class FieldUsagesResult(
    val reads: List<JcField>,
    val writes: List<JcField>
)

/**
 * find all methods used in bytecode of specified `method`
 * @param method method to analyze
 */
val JcMethod.usedFields: FieldUsagesResult
    get() {
        val cp = enclosingClass.classpath
        val reads = LinkedHashSet<JcField>()
        val writes = LinkedHashSet<JcField>()
        withAsmNode { methodNode ->
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
        }
        return FieldUsagesResult(
            reads = reads.toList(),
            writes = writes.toList()
        )
    }
