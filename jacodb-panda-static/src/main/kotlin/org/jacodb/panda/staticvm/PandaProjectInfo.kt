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

package org.jacodb.panda.staticvm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PandaProgramInfo(
    val classes: List<PandaClassInfo> = emptyList()
) {
    fun toProject(): PandaProject {
        val project = PandaProject.blank()

        val nameToClass = classes.associateBy { PandaClassName(it.name) }
        val classesGraph = SimpleDirectedGraph<PandaClassName>()
        classes.forEach {
            val superClass = it.superClass
            if (!superClass.isNullOrBlank())
                classesGraph.withEdge(PandaClassName(it.name), PandaClassName(superClass))
        }

        project.typeHierarchy.apply {
            classesGraph.topsort().mapNotNull { nameToClass[it] }.forEach { cls ->
                if (AccessFlags(cls.accessFlags).isInterface) {
                    addInterface(
                        PandaClassName(cls.name),
                        cls.interfaces.map(::PandaClassName)
                    )
                } else {
                    addClass(
                        PandaClassName(cls.name),
                        cls.superClass?.let(::PandaClassName),
                        cls.interfaces.map(::PandaClassName)
                    )
                }
            }
        }

        val pandaFields = classes.flatMap { cls ->
            cls.fields.map { field ->
                PandaField(
                    project,
                    PandaClassName(cls.name),
                    field.type.pandaClassName,
                    field.name,
                    AccessFlags(field.accessFlags)
                )
            }
        }

        val pandaMethods = classes.flatMap { cls ->
            cls.methods.map { method ->
                val flags = AccessFlags(method.accessFlags)
                val parameters = (if (flags.isStatic) emptyList<PandaTypeName>() else listOf(cls.name.pandaClassName))
                    .plus(method.parameters.map(String::pandaTypeName))
                val blocks = method.basicBlocks.sortedBy { it.id }.takeIf { it.isNotEmpty() }
                val graph = blocks?.fold(SimpleDirectedGraph<PandaBasicBlockInfo>()) { graph, block ->
                    block.predecessors.forEach { graph.withEdge(blocks[it], block) }
                    block.successors.forEach { graph.withEdge(block, blocks[it]) }
                    graph
                }

                PandaMethod(
                    project,
                    PandaClassName(cls.name),
                    method.returnType.pandaTypeName,
                    parameters,
                    method.signature,
                    graph,
                    flags
                )
            }
        }

        val pandaClasses = classes.map { cls ->
            PandaClass(
                project,
                pandaFields.filter { it.declaringClassType == cls.name.pandaClassName },
                pandaMethods.filter { it.declaringClassType == cls.name.pandaClassName },
                cls.name,
                cls.superClass?.takeIf { it.isNotBlank() }?.pandaClassName,
                cls.interfaces.map(String::pandaClassName),
                AccessFlags(cls.accessFlags)
            )
        }

        project.fields.addAll(pandaFields)
        project.classes.addAll(pandaClasses)
        project.methods.addAll(pandaMethods)
        return project
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "opcode"
        }
    }
}

@Serializable
data class PandaClassInfo(
    val name: String,
    val simpleName: String,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val accessFlags: Int = 1,
    val fields: List<PandaFieldInfo> = emptyList(),
    val methods: List<PandaMethodInfo> = emptyList()
)

@Serializable
data class PandaFieldInfo(
    val name: String,
    val type: String,
    val accessFlags: Int,
)

@Serializable
data class PandaMethodInfo(
    val name: String,
    val signature: String,
    val returnType: String,
    val parameters: List<String> = emptyList(),
    val accessFlags: Int,
    val basicBlocks: List<PandaBasicBlockInfo> = emptyList()
)

@Serializable
data class PandaBasicBlockInfo(
    val id: Int,
    val predecessors: List<Int> = emptyList(),
    val successors: List<Int> = emptyList(),
    val insts: List<PandaInstInfo> = emptyList()
)

@Serializable
sealed interface PandaInstInfo {
    val id: String
    val inputs: List<String>
    val users: List<String>
    val opcode: String
    val type: String
}

@Serializable
@SerialName("Constant")
data class PandaConstantInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val value: ULong
) : PandaInstInfo

@Serializable
@SerialName("SafePoint")
data class PandaSafePointInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
) : PandaInstInfo

@Serializable
@SerialName("SaveState")
data class PandaSaveStateInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
) : PandaInstInfo

@Serializable
@SerialName("NewObject")
data class PandaNewObjectInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val objectClass: String,
) : PandaInstInfo

@Serializable
@SerialName("CallStatic")
data class PandaCallStaticInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val method: String
) : PandaInstInfo

@Serializable
@SerialName("NullCheck")
data class PandaNullCheckInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
) : PandaInstInfo

@Serializable
@SerialName("CallVirtual")
data class PandaCallVirtualInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val method: String
) : PandaInstInfo

@Serializable
@SerialName("LoadAndInitClass")
data class PandaLoadAndInitClassInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val loadedClass: String,
    val method: String,
) : PandaInstInfo

@Serializable
@SerialName("ReturnVoid")
data class PandaReturnVoidInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
) : PandaInstInfo

@Serializable
@SerialName("LoadStatic")
data class PandaLoadStaticInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val enclosingClass: String,
    val field: String,
) : PandaInstInfo

@Serializable
@SerialName("Return")
data class PandaReturnInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
) : PandaInstInfo

@Serializable
@SerialName("Parameter")
data class PandaParameterInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val index: Int
) : PandaInstInfo

@Serializable
@SerialName("LoadObject")
data class PandaLoadObjectInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val enclosingClass: String,
    val field: String,
) : PandaInstInfo

@Serializable
@SerialName("StoreObject")
data class PandaStoreObjectInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val enclosingClass: String,
    val field: String,
) : PandaInstInfo

@Serializable
@SerialName("StoreStatic")
data class PandaStoreStaticInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val enclosingClass: String,
    val field: String,
) : PandaInstInfo

@Serializable
@SerialName("Add")
data class PandaAddInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
) : PandaInstInfo

@Serializable
@SerialName("Cast")
data class PandaCastInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
) : PandaInstInfo

@Serializable
@SerialName("IfImm")
data class PandaIfImmInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val operator: String,
    val operandsType: String,
    val immediate: ULong
) : PandaInstInfo

@Serializable
@SerialName("Compare")
data class PandaCompareInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val operator: String,
    val operandsType: String,
) : PandaInstInfo

@Serializable
@SerialName("Phi")
data class PandaPhiInstInfo(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val opcode: String,
    override val type: String,
    val inputBlocks: List<Int>
) : PandaInstInfo
