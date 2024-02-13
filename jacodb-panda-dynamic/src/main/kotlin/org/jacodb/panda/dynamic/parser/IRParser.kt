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

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import mu.KLogging
import org.jacodb.panda.dynamic.api.*
import java.io.File

val logger = object : KLogging() {}.logger

class IRParser(jsonPath: String) {

    @Serializable
    data class ProgramIR(val classes: List<ProgramClass>)

    @Serializable
    data class ProgramClass(
        val is_interface: Boolean = false,
        val methods: List<ProgramMethod> = emptyList(),
        val name: String,
        val simple_name: String? = null,
        val super_class: String? = null,
    )

    @Serializable
    data class ProgramMethod(
        val basic_blocks: List<ProgramBasicBlock> = emptyList(),
        val is_class_initializer: Boolean = false,
        val is_constructor: Boolean = false,
        val is_native: Boolean = false,
        val is_synchronized: Boolean = false,
        val name: String,
        val return_type: String? = null,
        val signature: String,
        val args: Int = 0,
        val regs: Int = 0
    ) {

        @Transient
        val idToMappable: MutableMap<Int, Mappable> = mutableMapOf()

        @Transient
        val insts: MutableList<PandaInst> = mutableListOf()

        // ArkTS id -> Panda input
        @Transient
        val idToInputs: MutableMap<Int, MutableList<PandaExpr>> = mutableMapOf()

        @Transient
        val idToIRInputs: MutableMap<Int, MutableList<ProgramInst>> = mutableMapOf()

        // ArkTS bb id -> bb
        @Transient
        val idToBB: MutableMap<Int, PandaBasicBlock> = mutableMapOf()

        @Transient
        val pandaMethod: PandaMethod = PandaMethod(name, mapType(return_type))

        @Transient
        val parameters: MutableList<PandaParameterInfo> = mutableListOf()

        @Transient
        var currentLocalVarId = 0

        @Transient
        var currentId = 0

        fun inputsViaOp(op: ProgramInst): List<PandaExpr> = idToInputs.getOrDefault(op.id(), emptyList())

        init {
            basic_blocks.forEach { it.setMethod(this)}
        }
    }

    @Serializable
    data class ProgramBasicBlock(
        val id: Int,
        val insts: List<ProgramInst> = emptyList(),
        val successors: List<Int> = emptyList(),
        val predecessors: List<Int> = emptyList()
    ) {

        @Transient
        private var method: ProgramMethod? = null

        @Transient
        var start: Int = -1

        @Transient
        var end: Int = -1

        fun setMethod(m: ProgramMethod) {
            method = m
        }

        fun getMethod() = method ?: throw Exception("Method not set for basic block $id")

        init {
            insts.forEach { it.setBasicBlock(this) }
        }
    }

    @Serializable
    data class ProgramInst(
        val id: String,
        val inputs: List<String> = emptyList(),
        val opcode: String,
        val type: String? = null,
        val users: List<String> = emptyList(),
        val value: String? = null,
        val visit: String? = null,
        val bc: String? = null
    ) {

        private var basicBlock: ProgramBasicBlock? = null

        private fun String.trimId() = this.drop(1).toInt()

        private val _id: Int = id.trimId()

        fun setBasicBlock(bb: ProgramBasicBlock) {
            basicBlock = bb
        }

        fun getBasicBlock() = basicBlock ?: throw Exception("Basic block not set for inst $id")

        fun id() = _id

        fun inputs(): List<Int> = inputs.map { it.trimId() }

        fun outputs(): List<Int> = users.map { it.trimId() }
    }

    private val jsonFile: File = File(jsonPath)

    private val json = jsonFile.readText()

    companion object {

        /**
         * First 3 arguments in Panda IR are placeholders for "this", etc.
         * Filter them out to include real parameters.
         */
        private const val argThreshold = 3

        fun mapType(type: String?): PandaType = when (type) {
            "i64" -> PandaNumberType()
            "any" -> PandaAnyType()
            else -> PandaAnyType()
        }
    }

    fun getProgramIR(): ProgramIR {
        val programIR: ProgramIR = Json.decodeFromString(json)
        mapProgramIR(programIR)
        return programIR
    }

    private fun ProgramInst.currentMethod() = this.getBasicBlock().getMethod()

    private fun ProgramInst.currentBB() = this.getBasicBlock()

    private fun inputsViaOp(op: ProgramInst) = op.currentMethod().inputsViaOp(op)

    private fun mapProgramIR(programIR: ProgramIR) {
        mapIdToIRInputs(programIR)

        mapInstructions(programIR)

        mapMethods(programIR)
    }

    private fun mapIdToIRInputs(programIR: ProgramIR) {
        programIR.classes.forEach { clazz ->
            clazz.methods.forEach { method ->
                method.basic_blocks.forEach { bb ->
                    bb.insts.forEach { inst ->
                        // TODO(to delete?: map IfImm)
                        // TODO(reply): no.
                        /*
                        inst.opcode.startsWith("IfImm") -> {
                            val successors =
                                bb.successors ?: throw IllegalStateException("No bb succ after IfImm op")
                            val trueBB = method.basic_blocks.find { it.id == successors[0] }!!
                            val falseBB = method.basic_blocks.find { it.id == successors[1] }!!
                            listOfNotNull(
                                trueBB.insts?.minBy { it.id() }?.id(),
                                falseBB.insts?.minBy { it.id() }?.id()
                            ).forEach { output ->
                                method.idToIRInputs.getOrPut(output) { mutableListOf() }.add(inst)
                            }
                        }
                        */
                        when {
                            else -> inst.outputs().forEach { output ->
                                method.idToIRInputs.getOrPut(output) { mutableListOf() }.add(inst)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun mapMethods(programIR: ProgramIR) {
        val programMethods = programIR.classes
            .flatMap { it.methods }

        programMethods.forEach { programMethod ->
            programMethod.pandaMethod.initBlocks(
                programMethod.idToBB.values.toList(),
            )
            programMethod.pandaMethod.initInstructions(programMethod.insts)
            programMethod.pandaMethod.initParameters(programMethod.parameters)
        }
    }

    private fun mapInstructions(programIR: ProgramIR) {
        val programInstructions = programIR.classes
            .flatMap { it.methods }
            .flatMap { it.basic_blocks }
            .flatMap { it.insts }

        programInstructions.forEach { programInst ->
            val currentMethod: ProgramMethod = programInst.currentMethod()
            mapOpcode(programInst, currentMethod)
            currentMethod.idToBB[programInst.currentBB().id] = mapBasicBlock(programInst.currentBB())
        }
    }

    private fun mapBasicBlock(bb: ProgramBasicBlock): PandaBasicBlock {
        val start = bb.start
        val end = bb.end
        val successors = bb.successors.toSet()
        val predecessors = bb.predecessors.toSet()

        return PandaBasicBlock(
            bb.id,
            successors,
            predecessors,
            PandaInstRef(start),
            PandaInstRef(end)
        )
    }

    private fun addInput(method: ProgramMethod, inputId: Int, outputId: Int, input: PandaExpr) {
        method.idToIRInputs.getOrDefault(outputId, mutableListOf()).forEachIndexed { index, programInst ->
            if (inputId == programInst.id()) {
                method.idToInputs.getOrPut(outputId) { mutableListOf() }.add(index, input)
            }
        }
    }

    private var currentBasicBlock: ProgramBasicBlock? = null

    private fun mapOpcode(op: ProgramInst, method: ProgramMethod) = with(op) {
        val inputs = inputsViaOp(this)
        val outputs = outputs()
        val bb = currentBB()

        when {
            opcode == "Parameter" -> {
                val arg = PandaArgument(id())
                outputs.forEach { output ->
                    addInput(method, id(), output, arg)
                }
                if (id() >= argThreshold) {
                    val argInfo = PandaParameterInfo(id() - argThreshold, mapType(type))
                    method.parameters.add(argInfo)
                }
            }

            opcode == "Constant" -> {
                val c = mapConstant(this)
                outputs.forEach { output ->
                    addInput(method, id(), output, c)
                }
            }

            opcode == "Intrinsic.typeof" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(
                    locationFromOp(this),
                    lv,
                    PandaTypeofExpr(inputs[0] as PandaValue)
                )
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.noteq" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(
                    locationFromOp(this),
                    lv,
                    PandaNeqExpr(inputs[0] as PandaValue, inputs[1] as PandaValue)
                )
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode.startsWith("Compare") -> {
                val cmpOp = PandaCmpOp.valueOf(
                    Regex("Compare (.*) (.*)")
                        .find(opcode)
                        ?.groups
                        ?.get(1)
                        ?.value
                        ?: throw IllegalStateException("No compare op")
                )
                val cmp = PandaCmpExpr(cmpOp, inputs[0] as PandaValue, inputs[1] as PandaValue)
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, cmp)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode.startsWith("IfImm") -> {
                method.insts.add(mapIfInst(this, inputs))
            }

            opcode == "LoadString" -> {
                val sc = PandaStringConstant()
                outputs.forEach { output ->
                    addInput(method, id(), output, sc)
                }
            }

            opcode == "CastValueToAnyType" -> {
                outputs.forEach { output ->
                    inputs.forEach { input -> addInput(method, id(), output, input) }
                }
            }

            opcode == "Intrinsic.newobjrange" -> {
                val newExpr = PandaNewExpr(inputs[0] as PandaValue, inputs.drop(1).map { it as PandaValue })
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, newExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.throw" -> {
                val inst = PandaThrowInst(locationFromOp(this), inputs[0] as PandaValue)
                method.insts.add(inst)
            }

            opcode == "Intrinsic.add2" -> {
                val addExpr = PandaAddExpr(inputs[0] as PandaValue, inputs[1] as PandaValue)
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, addExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.return" -> {
                val inst = PandaReturnInst(locationFromOp(this), inputs.getOrNull(0) as? PandaValue)
                method.insts.add(inst)
            }

            else -> getInstType(this, method)
        }

        matchBasicBlockInstructionId(bb, method.currentId)
    }

    private fun matchBasicBlockInstructionId(
        bb: ProgramBasicBlock,
        currentId: Int
    ) {
        bb.end = currentId - 1

        if (currentBasicBlock == null) {
            bb.start = if (currentId == 0) -1 else 0
            currentBasicBlock = bb
        } else if (bb.id != currentBasicBlock?.id) {
            bb.start = currentId
            currentBasicBlock = bb
        }
    }


    private fun getInstType(op: ProgramInst, method: ProgramMethod) = with(op) {
        val operands = inputsViaOp(op).mapNotNull { it as? PandaValue }
        val outputs = op.outputs()

        when (opcode) {
            "Intrinsic.tryldglobalbyname" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(
                    locationFromOp(this),
                    lv,
                    TODOExpr(opcode, operands)
                )
                outputs.forEach { output ->
                    addInput(method, this.id(), output, lv)
                }
                method.insts.add(assign)
            }
            "SaveState" -> {}
            else -> {
//                logger.warn { "Unknown opcode: $opcode" }
            }
        }
    }

    private fun mapIfInst(op: ProgramInst, inputs: List<Mappable>): PandaIfInst {
        val cmpOp = PandaCmpOp.valueOf(
            Regex("IfImm (.*) (.*)")
                .find(op.opcode)
                ?.groups
                ?.get(1)
                ?.value
                ?: throw IllegalStateException("No IfImm op")
        )
        val immValue = mapImm(op.inputs.last())
        val condExpr: PandaConditionExpr = when (cmpOp) {
            PandaCmpOp.NE -> PandaNeqExpr(inputs[0] as PandaValue, immValue)
            PandaCmpOp.EQ -> PandaEqExpr(inputs[0] as PandaValue, immValue)
        }

        val trueBranch = lazy {
            op.currentMethod().idToBB[op.getBasicBlock().successors[0]]!!.start
        }

        val falseBranch = lazy {
            op.currentMethod().idToBB[op.getBasicBlock().successors[1]]!!.start
        }

        return PandaIfInst(locationFromOp(op), condExpr, trueBranch, falseBranch)
    }

    private fun locationFromOp(op: ProgramInst): PandaInstLocation {
        val method = op.currentMethod()
        return PandaInstLocation(
            method.pandaMethod,
            method.currentId++,
            0
        )
    }
    
    private fun mapImm(imm: String): PandaConstant {
        return when {
            imm.startsWith("0x") -> PandaNumberConstant(Integer.decode(imm))
            else -> TODOConstant(imm)
        }
    }
            
    private fun mapConstant(op: ProgramInst): PandaConstant = when (mapType(op.type)) {
        is PandaNumberType -> PandaNumberConstant(Integer.decode(op.value!!.toString()))
        else -> TODOConstant(op.value)
    }

    fun printProgramInfo(programIR: ProgramIR) {
        programIR.classes.forEach { programClass ->
            println("Class Name: ${programClass.name}")

            programClass.methods.forEach { programMethod ->
                println("\tMethod Name: ${programMethod.name}")
                programMethod.basic_blocks.forEach { programBlock ->
                    println("\t\tBasic Block ID: ${programBlock.id}")
                    programBlock.insts.forEach { programInst ->
                        println("\t\t\tInst ID: ${programInst.id()}, Opcode: ${programInst.opcode}")
                        println("\t\t\t\tType: ${programInst.type}, Users: ${programInst.users}, Value: ${programInst.value}, Visit: ${programInst.visit}")
                    }
                }
            }
        }
    }

    fun printSetOfProgramOpcodes(programIR: ProgramIR) {
        val opcodes = mutableSetOf<String>()
        programIR.classes.forEach { programClass ->
            programClass.methods.forEach { programMethod ->
                programMethod.basic_blocks.forEach { programBlock ->
                    programBlock.insts.forEach { programInst ->
                        opcodes.add(programInst.opcode)
                    }
                }
            }
        }
        println(opcodes)
    }

}
