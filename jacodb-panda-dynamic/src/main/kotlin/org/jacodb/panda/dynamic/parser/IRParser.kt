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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jacodb.panda.dynamic.api.Mappable
import org.jacodb.panda.dynamic.api.PandaAddExpr
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaAssignInst
import org.jacodb.panda.dynamic.api.PandaBasicBlock
import org.jacodb.panda.dynamic.api.PandaCallInst
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaCmpExpr
import org.jacodb.panda.dynamic.api.PandaCmpOp
import org.jacodb.panda.dynamic.api.PandaConditionExpr
import org.jacodb.panda.dynamic.api.PandaConstant
import org.jacodb.panda.dynamic.api.PandaEqExpr
import org.jacodb.panda.dynamic.api.PandaGeExpr
import org.jacodb.panda.dynamic.api.PandaGtExpr
import org.jacodb.panda.dynamic.api.PandaIfInst
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaInstLocation
import org.jacodb.panda.dynamic.api.PandaInstRef
import org.jacodb.panda.dynamic.api.PandaLeExpr
import org.jacodb.panda.dynamic.api.PandaLocalVar
import org.jacodb.panda.dynamic.api.PandaLtExpr
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaNeqExpr
import org.jacodb.panda.dynamic.api.PandaNewExpr
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaParameterInfo
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.panda.dynamic.api.PandaStaticCallExpr
import org.jacodb.panda.dynamic.api.PandaStrictEqExpr
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaThrowInst
import org.jacodb.panda.dynamic.api.PandaToNumericExpr
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaTypeofExpr
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.panda.dynamic.api.PandaVirtualCallExpr
import org.jacodb.panda.dynamic.api.TODOConstant
import org.jacodb.panda.dynamic.api.TODOExpr
import java.io.File

private val logger = mu.KotlinLogging.logger {}

class IRParser(jsonPath: String, bcParser: ByteCodeParser) {

    private val connector = IrBcConnector(bcParser)

    @Serializable
    data class ProgramIR(val classes: List<ProgramClass>)

    @Serializable
    data class ProgramClass(
        @SerialName("is_interface")
        val isInterface: Boolean = false,
        val methods: List<ProgramMethod> = emptyList(),
        val name: String,
        @SerialName("simple_name")
        val simpleName: String? = null,
        @SerialName("super_class")
        val superClass: String? = null,
    ) {
        init {
            methods.forEach {
                it.setClass(this)
            }
        }
    }

    @Serializable
    data class ProgramMethod(
        @SerialName("basic_blocks")
        val basicBlocks: List<ProgramBasicBlock> = emptyList(),
        @SerialName("is_class_initializer")
        val isClassInitializer: Boolean = false,
        @SerialName("is_constructor")
        val isConstructor: Boolean = false,
        @SerialName("is_native")
        val isNative: Boolean = false,
        @SerialName("is_synchronized")
        val isSynchronized: Boolean = false,
        val name: String,
        @SerialName("return_type")
        val returnType: String? = null,
        val signature: String,
        val args: Int = 0,
        val regs: Int = 0,
    ) {

        @Transient
        private var clazz: ProgramClass? = null

        @Transient
        val idToMappable: MutableMap<Int, Mappable> = mutableMapOf()

        @Transient
        val insts: MutableList<PandaInst> = mutableListOf()

        // ArkTS id -> Panda input
        @Transient
        val idToInputs: MutableMap<Int, MutableList<PandaValue>> = mutableMapOf()

        @Transient
        val idToIRInputs: MutableMap<Int, MutableList<ProgramInst>> = mutableMapOf()

        // ArkTS bb id -> bb
        @Transient
        val idToBB: MutableMap<Int, PandaBasicBlock> = mutableMapOf()

        @Transient
        val pandaMethod: PandaMethod = PandaMethod(name, mapType(returnType))

        @Transient
        val parameters: MutableList<PandaParameterInfo> = mutableListOf()

        @Transient
        var currentLocalVarId = 0

        @Transient
        var currentId = 0

        fun setClass(c: ProgramClass) {
            clazz = c
        }

        fun getClass() = clazz ?: error("Class not set for method $name")

        fun inputsViaOp(op: ProgramInst): List<PandaValue> = idToInputs[op.id()].orEmpty()

        init {
            basicBlocks.forEach { it.setMethod(this) }
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
        private var method: ProgramMethod? = null

        @Transient
        var start: Int = -1

        @Transient
        var end: Int = -1

        fun setMethod(m: ProgramMethod) {
            method = m
        }

        fun getMethod(): ProgramMethod = method ?: error("Method not set for basic block $id")

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
        val bc: String? = null,
    ) {

        private var basicBlock: ProgramBasicBlock? = null

        private fun String.trimId(): Int = this.drop(1).toInt()

        private val _id: Int = id.trimId()

        fun setBasicBlock(bb: ProgramBasicBlock) {
            basicBlock = bb
        }

        fun getBasicBlock(): ProgramBasicBlock = basicBlock ?: error("Basic block not set for inst $id")

        fun id(): Int = _id

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
            "i64", "i32" -> PandaNumberType
            "any" -> PandaAnyType
            else -> PandaAnyType
        }
    }

    fun getProgramIR(): ProgramIR {
        val programIR: ProgramIR = Json.decodeFromString(json)
        val pandaProject = mapProgramIR(programIR)
        return programIR
    }

    fun getProject(): PandaProject {
        val programIR: ProgramIR = Json.decodeFromString(json)
        return mapProgramIR(programIR)
    }

    private fun ProgramInst.currentMethod() = this.getBasicBlock().getMethod()

    private fun ProgramInst.currentBB() = this.getBasicBlock()

    private fun inputsViaOp(op: ProgramInst) = op.currentMethod().inputsViaOp(op)

    private fun mapProgramIR(programIR: ProgramIR): PandaProject {
        mapIdToIRInputs(programIR)

        mapInstructions(programIR)

        val classes = mapMethods(programIR)
        return PandaProject(classes)
    }

    private fun mapIdToIRInputs(programIR: ProgramIR) {
        programIR.classes.forEach { clazz ->
            clazz.methods.forEach { method ->
                method.basicBlocks.forEach { bb ->
                    bb.insts.forEach { inst ->
                        // TODO(to delete?: map IfImm)
                        // TODO(reply): no.
                        /*
                        inst.opcode.startsWith("IfImm") -> {
                            val successors =
                                bb.successors ?: error("No bb succ after IfImm op")
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

    private fun mapMethods(programIR: ProgramIR): List<PandaClass> {
        val classes = buildList {
            programIR.classes.forEach { clazz ->
                val pandaMethods = clazz.methods
                    .onEach { method ->
                        method.pandaMethod.blocks = method.idToBB.values.toList()
                        method.pandaMethod.instructions = method.insts
                        method.pandaMethod.parameterInfos = method.parameters
                        method.pandaMethod.className = clazz.name
                    }.map { it.pandaMethod }

                add(PandaClass(clazz.name, clazz.superClass!!, pandaMethods))
            }
        }

        return classes
    }

    private fun mapInstructions(programIR: ProgramIR) {
        val programInstructions = programIR.classes
            .flatMap { it.methods }
            .flatMap { it.basicBlocks }
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

    private fun addInput(method: ProgramMethod, inputId: Int, outputId: Int, input: PandaValue) {
        method.idToIRInputs[outputId].orEmpty().forEachIndexed { index, programInst ->
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

            /*opcode == "CastValueToAnyType" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, inputs[0])
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }*/

            opcode == "Intrinsic.typeof" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(
                    locationFromOp(this),
                    lv,
                    PandaTypeofExpr(inputs[0])
                )
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.tonumeric" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(
                    locationFromOp(this),
                    lv,
                    PandaToNumericExpr(inputs[0])
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
                    PandaNeqExpr(inputs[0], inputs[1])
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
                        ?: error("No compare op")
                )
                val cmp = PandaCmpExpr(cmpOp, inputs[0], inputs[1])
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
                val sc = PandaStringConstant("")
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
                val newExpr = PandaNewExpr(inputs[0], inputs.drop(1))
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, newExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.throw" -> {
                val inst = PandaThrowInst(locationFromOp(this), inputs[0])
                method.insts.add(inst)
            }

            opcode == "Intrinsic.add2" -> {
                val addExpr = PandaAddExpr(inputs[0], inputs[1])
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, addExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.return" -> {
                val inst = PandaReturnInst(locationFromOp(this), inputs.getOrNull(0))
                method.insts.add(inst)
            }

            opcode == "Intrinsic.inc" -> {
                val addExpr = PandaAddExpr(inputs[0], PandaNumberConstant(1))
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, addExpr)
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.istrue" -> {
                val compareExpr = PandaEqExpr(inputs[0], PandaNumberConstant(1))
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, compareExpr)
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.isfalse" -> {
                val compareExpr = PandaEqExpr(inputs[0], PandaNumberConstant(0))
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, compareExpr)
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.ldfalse" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, PandaNumberConstant(0))
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.ldtrue" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, PandaNumberConstant(1))
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

//            opcode == "Intrinsic.ldundefined" -> {
//                val lv = PandaLocalVar(method.currentLocalVarId++)
//                val assign = PandaAssignInst(locationFromOp(this), lv, PandaUndefinedConstant)
//                outputs.forEach { output -> addInput(method, id(), output, lv) }
//                method.insts.add(assign)
//            }

            opcode == "Intrinsic.less" -> {
                val ltExpr = PandaLtExpr(inputs[0], inputs[1])
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, ltExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.returnundefined" -> {
                // TODO: consider 'returnValue = PandaUndefinedConstant'
                val inst = PandaReturnInst(locationFromOp(this), null)
                method.insts.add(inst)
            }

            opcode == "Intrinsic.stricteq" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(
                    locationFromOp(this),
                    lv,
                    PandaStrictEqExpr(inputs[0], inputs[1])
                )
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.callargs2" -> {
                val callExpr = PandaStaticCallExpr(
                    lazyMethod = lazy {
                        val callFuncName = (inputs[2] as PandaStringConstant).value
                        method.pandaMethod.project.findMethodOrNull(callFuncName, method.getClass().name)
                            ?: PandaMethod(callFuncName, PandaAnyType)
                    },
                    args = listOf(inputs[0], inputs[1])
                )
                if (outputs.isEmpty()) {
                    method.insts.add(PandaCallInst(locationFromOp(this), callExpr))
                } else {
                    val lv = PandaLocalVar(method.currentLocalVarId++)
                    val assign = PandaAssignInst(
                        locationFromOp(this),
                        lv,
                        callExpr
                    )
                    outputs.forEach { output ->
                        addInput(method, id(), output, lv)
                    }
                    method.insts.add(assign)
                }
            }

            opcode == "Intrinsic.tryldglobalbyname" -> {
                val name = connector.getLdName(method.name, bc!!)
                outputs.forEach { output ->
                    addInput(method, id(), output, PandaStringConstant(name))
                }
            }

            opcode == "Intrinsic.ldobjbyname" -> {
                val name = connector.getLdName(method.name, bc!!)
                outputs.forEach { output ->
                    addInput(method, id(), output, PandaStringConstant(name))
                }
            }

            opcode == "Intrinsic.ldglobalvar" -> {
                val name = connector.getLdName(method.name, bc!!)
                outputs.forEach { output ->
                    addInput(method, id(), output, PandaStringConstant(name))
                }
            }

            opcode == "Intrinsic.callthis1" -> {
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        method.pandaMethod.project.findInstanceMethodInStd(
                            (inputs[0] as PandaStringConstant).value,
                            (inputs[1] as PandaStringConstant).value
                        )
                    },
                    args = listOf(inputs[2]),
                    instance = inputs[0]
                )
                if (outputs.isEmpty()) {
                    method.insts.add(PandaCallInst(locationFromOp(this), callExpr))
                } else {
                    val lv = PandaLocalVar(method.currentLocalVarId++)
                    val assign = PandaAssignInst(
                        locationFromOp(this),
                        lv,
                        callExpr
                    )
                    outputs.forEach { output ->
                        addInput(method, id(), output, lv)
                    }
                    method.insts.add(assign)
                }
            }

            opcode == "Intrinsic.stglobalvar" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, TODOExpr(opcode, inputs))
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.callarg0" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, TODOExpr(opcode, inputs))
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            else -> getInstType(this, method)
        }

        matchBasicBlockInstructionId(bb, method.currentId)
    }

    private fun matchBasicBlockInstructionId(
        bb: ProgramBasicBlock,
        currentId: Int,
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
        when (opcode) {
            // Unuseful
            "SaveState" -> {}
            "Intrinsic.definefunc" -> {}
            "Intrinsic.ldundefined" -> {}
            else -> {
                logger.warn { "Unknown opcode: $opcode" }
            }
        }
    }

    private fun mapIfInst(op: ProgramInst, inputs: List<PandaValue>): PandaIfInst {
        val ifImmMatch = Regex("IfImm (.*) (.*)").find(op.opcode)
        val ifMatch = Regex("If (.*)").find(op.opcode)
        val cmpOp = (
            ifImmMatch?.groups?.get(1)
                ?: ifMatch?.groups?.get(1)
                ?: error("No compare operator")
            ).value.let(PandaCmpOp::valueOf)

        /*val cmpOp = PandaCmpOp.valueOf(
            Regex("IfImm (.*) (.*)")
                .find(op.opcode)
                ?.groups
                ?.get(1)
                ?.value
                ?: error("No IfImm op")
        )*/
        val immValue = mapImm(op.inputs.last())
        val condExpr: PandaConditionExpr = when (cmpOp) {
            PandaCmpOp.NE -> PandaNeqExpr(inputs[0], immValue)
            PandaCmpOp.EQ -> PandaEqExpr(inputs[0], immValue)
            PandaCmpOp.LT -> PandaLtExpr(inputs[0], immValue)
            PandaCmpOp.LE -> PandaLeExpr(inputs[0], immValue)
            PandaCmpOp.GT -> PandaGtExpr(inputs[0], immValue)
            PandaCmpOp.GE -> PandaGeExpr(inputs[0], immValue)
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
                programMethod.basicBlocks.forEach { programBlock ->
                    println("\t\tBasic Block ID: ${programBlock.id}")
                    programBlock.insts.forEach { programInst ->
                        println("\t\t\tInst ID: ${programInst.id()}, Opcode: ${programInst.opcode}")
                        println("\t\t\t\tType: ${programInst.type}, Users: ${programInst.users}, Value: ${programInst.value}, Inputs: ${programInst.inputs}")
                    }
                }
            }
        }
    }
}
