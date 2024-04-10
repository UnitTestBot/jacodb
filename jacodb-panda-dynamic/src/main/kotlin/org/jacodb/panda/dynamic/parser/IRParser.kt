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
import org.jacodb.panda.dynamic.api.*
import java.io.File

private val logger = mu.KotlinLogging.logger {}

class IRParser(jsonPath: String) {

    @Serializable
    data class Program(val classes: List<ProgramClass>) {
        override fun toString(): String {
            return classes.joinToString("\n")
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
            properties.forEach {
                it.setClass(this)
            }
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
        fun setClass(c: ProgramClass) {
            method.setClass(c)
        }

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
        private var clazz: ProgramClass? = null

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
        val pandaMethod: PandaMethod = PandaMethod(name, mapType(returnType))

        @Transient
        val parameters: MutableList<PandaParameterInfo> = mutableListOf()

        @Transient
        var currentLocalVarId = 0

        @Transient
        var currentId = 0

        @Transient
        private val idToInst: MutableMap<Int, ProgramInst> = mutableMapOf()

        @Transient
        val varNameToId : MutableMap<String, PandaValue> = mutableMapOf()

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

        fun setClass(c: ProgramClass?) {
            clazz = c
        }

        fun getClass() = clazz ?: error("Class not set for method $name")

        fun inputsViaOp(op: ProgramInst): List<PandaValue> = idToInputs[op.id()].orEmpty().filterNotNull()

        init {
            basicBlocks.forEach { it.setMethod(this) }
        }

        override fun toString(): String {
            return "Method: $name\nClass: ${clazz?.name}\nBasic blocks:\n${basicBlocks.joinToString("\n")}"
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

        init {
            insts.forEach { it.setBasicBlock(this) }
        }

        override fun toString(): String {
            return insts.takeIf { it.isNotEmpty() }?.let {
                "Basic block id: $id\nInstructions:\n${it.joinToString("\n")}"
            } ?: "Basic block id: $id\nNo instructions"
        }

        fun setMethod(m: ProgramMethod) {
            method = m
        }

        fun getMethod(): ProgramMethod = method ?: error("Method not set for basic block $id")
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
        val stringData: String? = null,
        val stringOffset: Int? = null,
        val type: String? = null,
        val users: List<String> = emptyList(),
        val value: Int? = null,
        val visit: String? = null,
        val immediate: Int? = null,
    ) {

        private var basicBlock: ProgramBasicBlock? = null

        private val _id: Int = id.trimId()

        init {
            opcode = intrinsicId ?: opcode
        }

        private fun String.trimId(): Int {
            return this.filter { it.isDigit() }.toInt()
        }

        fun setBasicBlock(bb: ProgramBasicBlock) {
            basicBlock = bb
        }

        fun getBasicBlock(): ProgramBasicBlock = basicBlock ?: error("Basic block not set for inst $id")

        fun id(): Int = _id

        fun inputs(): List<Int> = inputs.map { it.trimId() }

        fun outputs(): List<Int> = users.map { it.trimId() }

        override fun toString(): String {
            return "\tInst: $id\n\t\tOpcode: $opcode\n\t\tInputs: $inputs\n\t\tOutputs: $users\n\t\tValue: $value"
        }
    }

    private val jsonFile: File = File(jsonPath)

    private val json = jsonFile.readText()

    companion object {

        /**
         * First 3 arguments in Panda IR are placeholders for "this", etc.
         * Filter them out to include real parameters.
         */
        private const val ARG_THRESHOLD = 3

        fun mapType(type: String?): PandaType = when (type) {
            "i64", "i32" -> PandaNumberType
            "any" -> PandaAnyType
            else -> PandaAnyType
        }

        inline fun <reified T : PandaValue> List<PandaValue>.find(): List<T> {
            return this.filterIsInstance<T>()
        }
    }

    fun getProgram(): Program {
        val program: Program = Json.decodeFromString(json)
        mapProgramIR(program)
        return program
    }

    fun getProject(): PandaProject {
        val program: Program = Json.decodeFromString(json)
        return mapProgramIR(program)
    }

    private fun ProgramInst.currentMethod() = this.getBasicBlock().getMethod()

    private fun ProgramInst.currentBB() = this.getBasicBlock()

    private fun inputsViaOp(op: ProgramInst) = op.currentMethod().inputsViaOp(op)

    private fun mapProgramIR(program: Program): PandaProject {
        mapIdToIRInputs(program)

        mapInstructions(program)

        val classes = mapMethods(program)
        return PandaProject(classes)
    }

    private fun mapIdToIRInputs(program: Program) {
        program.classes.forEach { clazz ->
            clazz.properties.forEach { property ->
                property.method.basicBlocks.forEach { bb ->
                    bb.insts.forEach { inst ->
                        inst.outputs().forEach { output ->
                            property.method.idToIRInputs.getOrPut(output) { mutableListOf() }.add(inst)
                        }
                    }
                }
            }
        }
    }

    private fun mapMethods(program: Program): List<PandaClass> {
        return program.classes.map { clazz ->
            val pandaMethods = clazz.properties.map { property ->
                property.method.also { method ->
                    val panda = method.pandaMethod
                    panda.blocks = method.idToBB.values.toList()
                    panda.instructions = method.insts
                    panda.parameterInfos = method.parameters
                    panda.className = clazz.name
                    panda.localVarsCount = method.currentLocalVarId + 1
                }.pandaMethod
            }
            PandaClass(clazz.name, clazz.superClass, pandaMethods)
        }
    }

    private fun mapInstructions(program: Program) {
        val programInstructions = program.classes
            .flatMap { it.properties }
            .flatMap { it.method.basicBlocks }
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
        val outputInst = method.getInstViaId(outputId)
        val index = outputInst.inputs().indexOf(inputId)
        method.idToInputs.getOrPut(outputId) { MutableList(outputInst.inputs.size) { null } }.add(index, input)
    }

    private var currentBasicBlock: ProgramBasicBlock? = null

    private fun mapOpcode(op: ProgramInst, method: ProgramMethod) = with(op) {
        val inputs = inputsViaOp(this)
        val outputs = outputs()
        val bb = currentBB()

        when {
            opcode == "Parameter" -> {
                val arg = PandaArgument(id())
//                outputs.forEach { output ->
//                    addInput(method, id(), output, arg)
//                }
                val out = if (id() >= ARG_THRESHOLD) arg else PandaThis(PandaClassTypeImpl(method.getClass().name))
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                }
                if (id() >= ARG_THRESHOLD) {
                    val argInfo = PandaParameterInfo(id() - ARG_THRESHOLD, mapType(type))
                    method.parameters.add(argInfo)
                }
            }

            opcode == "Constant" -> {
                val c = mapConstant(this)
                outputs.forEach { output ->
                    addInput(method, id(), output, c)
                }
            }

            opcode == "Intrinsic.eq" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(
                    locationFromOp(this),
                    lv,
                    PandaEqExpr(inputs[0], inputs[1])
                )
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

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
                val cmpOp = operator?.let(PandaCmpOp::valueOf) ?: error("No operator")
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
                val sc = PandaStringConstant(stringData ?: error("No string data"))
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
//                val stringData = inputs[0] as PandaStringConstant
//                val newExpr = PandaNewExpr(stringData.value, inputs.drop(1))
//                outputs.forEach { output ->
//                    addInput(method, id(), output, newExpr as PandaValue)
//                }

                val stringData = inputs[0] as PandaStringConstant
                val newExpr = PandaNewExpr(stringData.value, inputs.drop(1))
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, newExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.stconsttoglobalrecord" -> {
                val variableName = "simpleObjjjjjjjjjj" // TODO stringData
                val localVar = inputs[0]
                method.varNameToId[variableName] = localVar
            }

            opcode == "Intrinsic.createemptyarray" -> {
                val createEmptyExpr = PandaCreateEmptyArrayExpr()
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, createEmptyExpr)
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
                val assign = PandaAssignInst(locationFromOp(this), lv, PandaBoolConstant(false))
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.ldtrue" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, PandaBoolConstant(true))
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.ldnull" -> {
                outputs.forEach { output ->
                    addInput(method, id(), output, PandaNullConstant)
                }
            }

            opcode == "Intrinsic.greater" -> {
                val gtExpr = PandaGtExpr(inputs[0], inputs[1])
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, gtExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

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
                val inst = PandaReturnInst(locationFromOp(this), PandaUndefinedConstant)
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
                val instCallValue = inputs.find<PandaInstanceCallValue>().first()
                val args = inputs.filterNot { it == instCallValue }
                val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.getClass().name
                        )
                    },
                    instance = instCallValue.instance,
                    args = args
                )
                handleOutputs(outputs, method, callExpr)
            }

            opcode == "Intrinsic.tryldglobalbyname" -> {
                val name = stringData ?: error("No string data")
                val out = method.varNameToId.getOrDefault(name, PandaStringConstant(name))
//                val out = PandaStringConstant(name)
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                }
            }

            opcode == "Intrinsic.ldobjbyname" -> {
                val name = stringData ?: error("No string data")
                var input = PandaLoadedValue(PandaStringConstant("WOCKHARDT"), PandaStringConstant(name))
                if (inputs.isEmpty()) {
                    logger.warn { "index out of range in: $opcode" }
                }
                else {
                    input = PandaLoadedValue(inputs[0], PandaStringConstant(name));
                }
                outputs.forEach { output ->
//                    addInput(
//                        method, id(), output,
//                        PandaLoadedValue(inputs[0], PandaStringConstant(name))
//                    )
                    addInput(
                        method, id(), output, input
                    )
                    // for call insts not to have "instance.object" and "instance, object" in inputs
                    if (!inputs.isEmpty()) {
                        method.idToInputs[output]?.remove(inputs[0])
                    }
                }
            }

            opcode == "Intrinsic.ldglobalvar" -> {
                val name = stringData ?: error("No string data")
                outputs.forEach { output ->
                    addInput(
                        method, id(), output,
                        PandaInstanceCallValueImpl(
                            PandaThis(PandaClassTypeImpl(method.getClass().name)),
                            PandaStringConstant(name)
                        )
                    )
                }
            }

//            opcode == "Intrinsic.defineclasswithbuffer" -> {
//                val name = "ClassName" // stringData
//                val pc = PandaClass(
//                    name,
//                    "",
//                    emptyList()
//                )
//                outputs.forEach { output ->
//                    addInput(
//                        method, id(), output, pc
//                    )
//                }
//            }

            opcode == "Intrinsic.definemethod" -> {
                val out = PandaLoadedValue(PandaStringConstant("AINTNORMALINSTANCE"),PandaStringConstant(stringData ?: "AINTNORMALMETHOD"))
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                }
            }

            opcode == "Intrinsic.callthis0" -> {
                val instCallValue = inputs[0] as PandaInstanceCallValue
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.getClass().name
                        )
                    },
                    args = emptyList(),
                    instance = instCallValue.instance
                )
                handleOutputs(outputs, method, callExpr)
            }

            opcode == "Intrinsic.callthis1" -> {
                val instCallValue = inputs.find<PandaInstanceCallValue>().first()
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.getClass().name
                        )
                    },
                    args = inputs.filterNot { it == instCallValue },
                    instance = instCallValue.instance
                )
                handleOutputs(outputs, method, callExpr)
            }

            opcode == "Intrinsic.callthis2" -> {
                val instCallValue = inputs.find<PandaInstanceCallValue>().first()
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.getClass().name
                        )
                    },
                    args = inputs.filterNot { it == instCallValue },
                    instance = instCallValue.instance
                )
                handleOutputs(outputs, method, callExpr)
            }

            opcode == "Intrinsic.callthis3" -> {
                val instCallValue = inputs[0] as PandaInstanceCallValue
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.getClass().name
                        )
                    },
                    args = listOf(inputs[1], inputs[2], inputs[3]),
                    instance = instCallValue.instance
                )
                handleOutputs(outputs, method, callExpr)
            }

            opcode == "Intrinsic.stglobalvar" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, TODOExpr(opcode, inputs))
                outputs.forEach { output -> addInput(method, id(), output, lv) }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.stobjbyname" -> {
                val objectName = PandaStringConstant("objectName") // stringData
                val instance = inputs[0]
                val value = inputs[1]

                val property = PandaLoadedValue(instance, objectName)
                val assign = PandaAssignInst(locationFromOp(this), property, value)
                method.insts.add(assign)
            }

            opcode == "Intrinsic.callarg0" -> {
                val instCallValue = inputs.find<PandaInstanceCallValue>().first()
                val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.getClass().name
                        )
                    },
                    args = emptyList(),
                    instance = instCallValue.instance
                )
                handleOutputs(outputs, method, callExpr)
            }

            opcode == "Intrinsic.callarg1" -> {
                val instCallValue = inputs.find<PandaInstanceCallValue>().first()
                val args = inputs.filterNot { it == instCallValue }
                val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.getClass().name
                        )
                    },
                    args = args,
                    instance = instCallValue.instance
                )
                handleOutputs(outputs, method, callExpr)
            }

            opcode == "Intrinsic.supercallthisrange" -> {
                val superClassName = method.getClass().superClass
                val thisInstance = PandaThis(PandaClassTypeImpl(method.getClass().name))

                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            "is instanceName that essential here",
                            ".$superClassName",
                            superClassName
                        )
                    },
                    args = inputs.filter { it !is PandaThis },//inputs.subList(1, inputs.size),
                    instance = thisInstance
                )

                method.insts.add(PandaCallInst(locationFromOp(this), callExpr))


                // TODO: ADD SUPER CALL INSTRUCTION
                outputs.forEach { output ->
                    addInput(method, id(), output, thisInstance)
                }
            }

            opcode == "Intrinsic.sub2" -> {
                val subExpr = PandaSubExpr(inputs[0], inputs[1])
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, subExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.mul2" -> {
                val mulExpr = PandaMulExpr(inputs[0], inputs[1])
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, mulExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.div2" -> {
                val divExpr = PandaDivExpr(inputs[0], inputs[1])
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, divExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.neg" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, PandaMulExpr(PandaNumberConstant(-1), inputs[0]))
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            opcode == "Intrinsic.exp" -> {
                val expExpr = PandaExpExpr(inputs[0], inputs[1])
                val lv = PandaLocalVar(method.currentLocalVarId++)
                val assign = PandaAssignInst(locationFromOp(this), lv, expExpr)
                outputs.forEach { output ->
                    addInput(method, id(), output, lv)
                }
                method.insts.add(assign)
            }

            else -> checkIgnoredInstructions(this)
        }

        matchBasicBlockInstructionId(bb, method.currentId)
    }

    private fun ProgramInst.handleOutputs(
        outputs: List<Int>,
        method: ProgramMethod,
        callExpr: PandaVirtualCallExpr,
    ) {
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

    private fun checkIgnoredInstructions(op: ProgramInst) = with(op) {
        when (opcode) {
            // Unuseful
            "SaveState" -> {}
            "Intrinsic.definefunc" -> {}
            "Intrinsic.ldundefined" -> {}
            "Intrinsic.copyrestargs" -> {}
            "Intrinsic.ldlexvar" -> {}
            "Intrinsic.ldhole" -> {}
            "Intrinsic.newlexenv" -> {}
            "Intrinsic.callruntime.callinit" -> {}
            else -> {
                logger.warn { "Unknown opcode: $opcode" }
            }
        }
    }

    private fun mapIfInst(op: ProgramInst, inputs: List<PandaValue>): PandaIfInst {
        val cmpOp = op.operator?.let(PandaCmpOp::valueOf) ?: error("No operator")
        val immValue = mapImm(op.immediate)
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

    private fun mapImm(imm: Int?): PandaConstant {
        return imm?.let { PandaNumberConstant(it) } ?: PandaNullConstant
    }

    private fun mapConstant(op: ProgramInst): PandaConstant = when (mapType(op.type)) {
        is PandaNumberType -> PandaNumberConstant(Integer.decode(op.value.toString()))
        else -> TODOConstant(op.value.toString())
    }
}

fun programToDot(program: IRParser.Program): String {
    val lines: MutableList<String> = mutableListOf()
    lines += "digraph {"
    lines += "  compound=true;"

    // Classes with properties:
    program.classes.forEach { clazz ->
        // CLASS
        lines += ""
        lines += "  \"${clazz.name}\" [shape=diamond]"

        // Properties inside class:
        clazz.properties.forEach { property ->
            // PROPERTY
            lines += "  \"${clazz.name}.${property.name}\" [shape=triangle,label=\"${clazz.name}::${property.name}\"];"
        }
        clazz.properties.forEach { property ->
            lines += "  \"${clazz.name}\" -> \"${clazz.name}.${property.name}\""
        }
    }

    // Basic blocks inside each class:
    program.classes.forEach { clazz ->
        clazz.properties.forEach { property ->
            // Link to the first basic block inside method:
            if (property.method.basicBlocks.isNotEmpty()) {
                lines += "  \"${clazz.name}.${property.name}\" -> \"${clazz.name}.${property.name}.bb${property.method.basicBlocks.first().id}.0\" [lhead=\"${clazz.name}.${property.name}.bb${property.method.basicBlocks.first().id}\"];"
            }

            // Basic blocks successors:
            property.method.basicBlocks.forEach { bb ->
                bb.successors.forEach { succ ->
                    lines += "  \"${clazz.name}.${property.name}.bb${bb.id}.0\" -> \"${clazz.name}.${property.name}.bb${succ}.0\"" +
                        " [ltail=\"${clazz.name}.${property.name}.bb${bb.id}\",lhead=\"${clazz.name}.${property.name}.bb${succ}\"];"
                }
            }

            // Basic blocks with instructions:
            property.method.basicBlocks.forEach { bb ->
                // BASIC BLOCK
                lines += ""
                lines += "  subgraph \"${clazz.name}.${property.name}.bb${bb.id}\" {"
                lines += "    cluster=true;"
                lines += "    label=\"BB ${bb.id}\\nsuccessors = ${bb.successors}\";"

                if (bb.insts.isEmpty()) {
                    lines += "    \"${clazz.name}.${property.name}.bb${bb.id}.0\" [shape=box,label=\"NOP\"];"
                }

                // Instructions inside basic block:
                bb.insts.forEachIndexed { i, inst ->
                    val labelLines: MutableList<String> = mutableListOf()
                    labelLines += "${inst.id}: ${inst.opcode}"
                    if (inst.index != null) {
                        labelLines += "index = ${inst.index}"
                    }
                    if (inst.imms.isNotEmpty()) {
                        labelLines += "imms = ${inst.imms}"
                    }
                    if (inst.inputs.isNotEmpty()) {
                        labelLines += "inputs = ${inst.inputs}"
                    }
                    if (inst.inputBlocks.isNotEmpty()) {
                        labelLines += "inputBlocks = ${inst.inputBlocks}"
                    }
                    if (inst.operandsType != null) {
                        labelLines += "operandsType = ${inst.operandsType}"
                    }
                    if (inst.operator != null) {
                        labelLines += "operator = ${inst.operator}"
                    }
                    if (inst.stringData != null) {
                        labelLines += "stringData = ${inst.stringData}"
                    }
                    if (inst.stringOffset != null) {
                        labelLines += "stringOffset = ${inst.stringOffset}"
                    }
                    if (inst.type != null) {
                        labelLines += "type = ${inst.type}"
                    }
                    if (inst.users.isNotEmpty()) {
                        labelLines += "users = ${inst.users}"
                    }
                    if (inst.value != null) {
                        labelLines += "value = ${inst.value}"
                    }
                    if (inst.visit != null) {
                        labelLines += "visit = ${inst.visit}"
                    }
                    if (inst.immediate != null) {
                        labelLines += "immediate = ${inst.immediate}"
                    }
                    // INSTRUCTION
                    lines += "    \"${clazz.name}.${property.name}.bb${bb.id}.${i}\" [shape=box,label=\"${
                        labelLines.joinToString("") { "${it}\\l" }
                    }\"];"
                }

                // Instructions chain:
                if (bb.insts.isNotEmpty()) {
                    lines += "    ${
                        List(bb.insts.size) { i ->
                            "\"${clazz.name}.${property.name}.bb${bb.id}.${i}\""
                        }.joinToString(" -> ")
                    };"
                }

                lines += "  }"
            }
        }
    }

    lines += "}"
    return lines.joinToString("\n")
}

fun IRParser.Program.dumpDot(file: File) {
    val s = programToDot(this)
    file.writeText(s)
}

fun IRParser.Program.dumpDot(path: String) {
    dumpDot(File(path))
}
