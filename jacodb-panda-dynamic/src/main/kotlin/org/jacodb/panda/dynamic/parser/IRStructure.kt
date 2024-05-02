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

package org.jacodb.panda.dynamic.parser

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jacodb.panda.dynamic.api.Mappable
import org.jacodb.panda.dynamic.api.PandaBasicBlock
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaParameterInfo
import org.jacodb.panda.dynamic.api.PandaValue

@Serializable
data class Program(val classes: List<ProgramClass>) {
    override fun toString(): String {
        return classes.joinToString("\n")
    }

    fun findClassOrNull(name: String): ProgramClass? = classes.find { it.name == name }

    fun findMethodOrNull(name: String, methodClassName: String): ProgramMethod? {
        // TODO: find better solution
        if (name == "log" && methodClassName == "console") {
            return ProgramMethod(name="log", signature = "std::.log")
        }
        return findClassOrNull(methodClassName)?.properties?.find { it.name == name }?.method
    }
}

@Serializable
data class ProgramClass(
    val name: String,
    val properties: List<ProgramProperty> = emptyList(),
) {
    @Transient
    val superClass: String = ""

    init {
        properties.forEach { it.method.clazz = this }
    }

    override fun toString(): String {
        return "Class: $name\nMethods:\n${properties.joinToString("\n")}"
    }
}

@Serializable
data class ProgramProperty(
    val method: ProgramMethod,
    val name: String,
) {
    override fun toString(): String {
        return "Property: $name\n$method"
    }
}

@Serializable
data class ProgramMethod(
    val accessFlags: Int? = null,
    val basicBlocks: List<ProgramBasicBlock> = emptyList(),
    val name: String,
    @SerialName("parameters")
    val programParameters: List<String> = emptyList(),
    val returnType: String? = null,
    val signature: String,
) {

    @Transient
    lateinit var clazz: ProgramClass
        internal set

    @Transient
    val idToMappable: MutableMap<Int, Mappable> = mutableMapOf()

    @Transient
    val insts: MutableList<PandaInst> = mutableListOf()

    // ArkTS id -> Panda input
    @Transient
    val idToInputs: MutableMap<Int, MutableList<PandaValue?>> = mutableMapOf()

    @Transient
    val idToIRInputs: MutableMap<Int, MutableList<ProgramInst>> = mutableMapOf()

    // ArkTS bb id -> bb
    @Transient
    val idToBB: MutableMap<Int, PandaBasicBlock> = mutableMapOf()

    @Transient
    val pandaMethod: PandaMethod = PandaMethod(name)

    @Transient
    val parameters: MutableList<PandaParameterInfo> = mutableListOf()

    @Transient
    var currentLocalVarId = 0

    @Transient
    var currentId = -1

    @Transient
    private val idToInst: MutableMap<Int, ProgramInst> = mutableMapOf()

    @Transient
    val nameToLocalVarId : MutableMap<String, PandaValue> = mutableMapOf()

    fun getInstViaId(instId: Int): ProgramInst {
        return idToInst.getOrPut(instId) {
            basicBlocks.forEach { bb ->
                bb.insts.find { it.id() == instId }?.let {
                    return@getOrPut it
                }
            }

            throw IllegalArgumentException("No instruction in method $name with id v$instId")
        }
    }

    fun inputsViaOp(op: ProgramInst): List<PandaValue> = idToInputs[op.id()].orEmpty().filterNotNull()

    init {
        basicBlocks.forEach { it.method = this }
    }

    override fun toString(): String {
        return "Method: $name\nClass: ${clazz.name}\nBasic blocks:\n${basicBlocks.joinToString("\n")}"
    }
}

@Serializable
data class ProgramBasicBlock(
    val id: Int,
    val insts: List<ProgramInst> = emptyList(),
    val successors: List<Int> = emptyList(),
    val predecessors: List<Int> = emptyList(),
) {

    @Transient
    lateinit var method: ProgramMethod
        internal set

    @Transient
    var start: Int = -1

    @Transient
    var end: Int = -1

    init {
        insts.forEach { it.basicBlock = this }
    }

    override fun toString(): String {
        return insts.takeIf { it.isNotEmpty() }?.let {
            "Basic block id: $id\nInstructions:\n${it.joinToString("\n")}"
        } ?: "Basic block id: $id\nNo instructions"
    }
}

@Serializable
data class ProgramInst(
    val id: String,
    val index: Int? = null,
    val imms: List<Int> = emptyList(),
    val inputs: List<String> = emptyList(),
    val inputBlocks: List<Int> = emptyList(),
    @SerialName("intrinsic_id")
    val intrinsicId: String? = null,
    var opcode: String,
    val operandsType: String? = null,
    val operator: String? = null,
    @SerialName("string_data")
    var stringData: String? = null,
    val string: String? = null,
    val stringOffset: Int? = null,
    val type: String? = null,
    val users: List<String> = emptyList(),
    val value: Int? = null,
    val visit: String? = null,
    val immediate: Int? = null,
    val constructorName: String? = null,
    val throwers: List<String> = emptyList()
) {

    @Transient
    lateinit var basicBlock: ProgramBasicBlock
        internal set

    @Transient
    private val _id: Int = id.trimId()

    init {
        opcode = intrinsicId ?: opcode
        // Remove later
        stringData = string ?: stringData
    }

    private fun String.trimId(): Int {
        return this.filter { it.isDigit() }.toInt()
    }

    fun id(): Int = _id

    fun inputs(): List<Int> = inputs.map { it.trimId() }

    fun outputs(): List<Int> = users.map { it.trimId() }

    override fun toString(): String {
        return "\tInst: $id\n\t\tOpcode: $opcode\n\t\tInputs: $inputs\n\t\tOutputs: $users\n\t\tValue: $value"
    }
}
