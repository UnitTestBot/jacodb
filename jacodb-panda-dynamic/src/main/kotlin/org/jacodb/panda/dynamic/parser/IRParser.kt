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

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.jacodb.panda.dynamic.api.PandaAddExpr
import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaArrayAccess
import org.jacodb.panda.dynamic.api.PandaAssignInst
import org.jacodb.panda.dynamic.api.PandaBasicBlock
import org.jacodb.panda.dynamic.api.PandaBoolConstant
import org.jacodb.panda.dynamic.api.PandaCallInst
import org.jacodb.panda.dynamic.api.PandaCatchInst
import org.jacodb.panda.dynamic.api.PandaCaughtError
import org.jacodb.panda.dynamic.api.PandaClass
import org.jacodb.panda.dynamic.api.PandaClassTypeImpl
import org.jacodb.panda.dynamic.api.PandaCmpExpr
import org.jacodb.panda.dynamic.api.PandaCmpOp
import org.jacodb.panda.dynamic.api.PandaConditionExpr
import org.jacodb.panda.dynamic.api.PandaConstant
import org.jacodb.panda.dynamic.api.PandaCreateEmptyArrayExpr
import org.jacodb.panda.dynamic.api.PandaDivExpr
import org.jacodb.panda.dynamic.api.PandaEqExpr
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaGeExpr
import org.jacodb.panda.dynamic.api.PandaGotoInst
import org.jacodb.panda.dynamic.api.PandaGtExpr
import org.jacodb.panda.dynamic.api.PandaIfInst
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaInstLocation
import org.jacodb.panda.dynamic.api.PandaInstRef
import org.jacodb.panda.dynamic.api.PandaInstanceCallValue
import org.jacodb.panda.dynamic.api.PandaInstanceCallValueImpl
import org.jacodb.panda.dynamic.api.PandaLeExpr
import org.jacodb.panda.dynamic.api.PandaLoadedValue
import org.jacodb.panda.dynamic.api.PandaLocalVar
import org.jacodb.panda.dynamic.api.PandaLtExpr
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaMulExpr
import org.jacodb.panda.dynamic.api.PandaNegExpr
import org.jacodb.panda.dynamic.api.PandaNeqExpr
import org.jacodb.panda.dynamic.api.PandaNewExpr
import org.jacodb.panda.dynamic.api.PandaNullConstant
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaParameterInfo
import org.jacodb.panda.dynamic.api.PandaPhiValue
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.panda.dynamic.api.PandaStrictEqExpr
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaSubExpr
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaThrowInst
import org.jacodb.panda.dynamic.api.PandaToNumericExpr
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaTypeofExpr
import org.jacodb.panda.dynamic.api.PandaUndefinedConstant
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.panda.dynamic.api.PandaVirtualCallExpr
import org.jacodb.panda.dynamic.api.TODOConstant
import org.jacodb.panda.dynamic.api.TODOExpr
import java.io.File

private val logger = mu.KotlinLogging.logger {}

class IRParser(
    jsonPath: String,
    private val tsFunctions: List<TSFunction>? = null,
) {

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

        fun locationFromOp(op: ProgramInst? = null, method: ProgramMethod? = null): PandaInstLocation {
            val currentMethod = method ?: op!!.currentMethod()
            return PandaInstLocation(
                currentMethod.pandaMethod,
                ++currentMethod.currentId,
                0
            )
        }

        fun ProgramInst.currentBB() = this.basicBlock

        fun ProgramInst.currentMethod() = currentBB().method
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

    /*
        containingClass in [TSClass] IS NOT super class!
        It is used for scoping, since in different scopes classes with the same name are allowed.

        However, it was possible to easily resolve in ts parser, but in Program[...] IR classes it is
        more complex to instantiate class SCOPE, not SUPER CLASS.

        Currently, only first class in chain of class scopes is analyzed.

        TODO: Expand for more complex samples.
     */
    private fun setMethodTypes(method: PandaMethod) {
        if (tsFunctions == null) return
        if (method.name == "func_main_0") return
        tsFunctions.find { tsFunc ->
            tsFunc.name == method.name &&
                tsFunc.arguments.size == method.parameterInfos.size &&
                // here comes the result of comment above
                tsFunc.containingClass?.name == method.className
        }?.let { tsFunc ->
            method.type = tsFunc.returnType
            method.parameterInfos = method.parameterInfos.zip(tsFunc.arguments).map { (paramInfo, type) ->
                PandaParameterInfo(
                    index = paramInfo.index,
                    type
                )
            }
            // TODO: Add class constructor to GLOBAL
        } ?: logger.error("No method ${method.name} with superclass ${method.className} was found in parsed functions")
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
                    setMethodTypes(panda)
                }.pandaMethod
            }
            PandaClass(clazz.name, clazz.superClass, pandaMethods)
        }
    }

    private fun mapInstructions(program: Program) {
        val programMethods: List<ProgramMethod> = program.classes
            .flatMap { it.properties }
            .map { it.method }

        programMethods.forEach { currentMethod ->
            val traversalManager = IRTraversalManager(
                programMethod = currentMethod,
                mapOpcode = ::mapOpcode,
            )

            traversalManager.run()
        }

        val gotoToBB = preprocessGoto(programMethods)
        postprocessGoto(programMethods, gotoToBB)
    }

    private fun preprocessGoto(programMethods: List<ProgramMethod>): Map<PandaInst, PandaBasicBlock> {
        val gotoToBB = mutableMapOf<PandaInst, PandaBasicBlock>()
        programMethods.forEach { method ->
            val instructions = method.insts
            val basicBlocks = method.idToBB.toMap()

            tailrec fun setTargetRec(gotoInst: PandaGotoInst, succBB: PandaBasicBlock) {
                if (succBB.start.index == -1) {
                    val newSuccBBidx = succBB.successors.first().takeIf { succBB.successors.size == 1 }
                        ?: error("Can't resolve goto for multiple successors of basic block $succBB")

                    setTargetRec(gotoInst, basicBlocks[newSuccBBidx]!!)
                } else {
                    gotoInst.setTarget(succBB.start)
                }
            }

            /*
                0 -- unvisited
                1 -- pending
                2 -- visited
             */
            val vertexState = mutableMapOf<Int, Int>().apply {
                for (bbId in basicBlocks.keys) {
                    this[bbId] = 0
                }
            }

            fun detectJump(bb: PandaBasicBlock) {
                vertexState[bb.id] = 1

                val gotoInst =
                    (instructions.find { it.location.index == bb.end.index } as? PandaGotoInst)
                gotoInst?.let {
                    gotoToBB[it] = bb
                }

                for (succBBId in bb.successors) {
                    val succBB = basicBlocks[succBBId]!!
                    if (vertexState[succBBId] == 1) {
                        setTargetRec(gotoInst!!, succBB)
                    } else if (vertexState[succBBId] == 0) {
                        detectJump(succBB)
                    }
                }

                vertexState[bb.id] = 2
            }

            for (bbId in basicBlocks.keys) {
                if (vertexState[bbId] == 0) detectJump(basicBlocks[bbId]!!)
            }
        }

        return gotoToBB
    }

    private fun postprocessGoto(programMethods: List<ProgramMethod>, gotoToBB: Map<PandaInst, PandaBasicBlock>) {
        programMethods.forEach { method ->
            val gotoToRemove = method.insts.filter { inst ->
                inst is PandaGotoInst && inst.target.index == inst.location.index + 1
            }
            val gotoIndices = gotoToRemove.map { it.location.index }

            method.insts.forEach { inst ->
                inst.decLocationIndex(gotoIndices)
            }

            gotoToRemove.forEach { gotoInst ->
                method.insts.remove(gotoInst)
                val enclosingBB = gotoToBB[gotoInst] ?: error("No basic block for $gotoInst")
                enclosingBB.updateRange(
                    enclosingBB.start,
                    PandaInstRef(enclosingBB.end.index - 1)
                )
            }
        }
    }

    private fun addInput(method: ProgramMethod, inputId: Int, outputId: Int, input: PandaValue) {
        val outputInst = method.getInstViaId(outputId)
        val index = outputInst.inputs().indexOf(inputId)
        method.idToInputs.getOrPut(outputId) { MutableList(outputInst.inputs.size) { null } }.add(index, input)
    }

    private fun mapOpcode(
        op: ProgramInst,
        method: ProgramMethod,
        env: IREnvironment,
        opIdx: Int,
        changeTraversalStrategy: (ProgramBasicBlock, TraversalType) -> Unit,
    ) = with(op) {
        val inputs = inputsViaOp(this)
        val outputs = outputs()

        fun handle(expr: PandaExpr) {
            val lv = PandaLocalVar(method.currentLocalVarId++, PandaAnyType)
            outputs.forEach { output ->
                addInput(method, id(), output, lv)
            }
            method.insts += PandaAssignInst(locationFromOp(this), lv, expr)
        }

        fun handle2(callExpr: PandaVirtualCallExpr) {
            if (outputs.isEmpty()) {
                method.insts += PandaCallInst(locationFromOp(this), callExpr)
            } else {
                handle(callExpr)
            }
        }

        when {
            opcode == "Parameter" -> {
                val arg = PandaArgument(id())
                outputs.forEach { output ->
                    addInput(method, id(), output, arg)
                }
                if (id() >= ARG_THRESHOLD) {
                    val argInfo = PandaParameterInfo(id() - ARG_THRESHOLD, mapType(type))
                    method.parameters += argInfo
                }
            }

            opcode == "Constant" -> {
                val c = mapConstant(this)
                outputs.forEach { output ->
                    addInput(method, id(), output, c)
                }
            }

            opcode == "Intrinsic.typeof" -> {
                val typeofExpr = PandaTypeofExpr(inputs[0])
                handle(typeofExpr)
            }

            opcode == "Intrinsic.tonumeric" -> {
                val toNumericExpr = PandaToNumericExpr(inputs[0])
                handle(toNumericExpr)
            }

            opcode == "Intrinsic.eq" -> {
                val eqExpr = PandaEqExpr(inputs[0], inputs[1])
                handle(eqExpr)
            }

            opcode == "Intrinsic.noteq" -> {
                val neqExpr = PandaNeqExpr(inputs[0], inputs[1])
                handle(neqExpr)
            }

            opcode.startsWith("Compare") -> {
                val cmpOp = operator?.let(PandaCmpOp::valueOf) ?: error("No operator")
                val cmpExpr = PandaCmpExpr(cmpOp, inputs[0], inputs[1])
                handle(cmpExpr)
            }

            opcode.startsWith("IfImm") -> {
                method.insts += mapIfInst(this, inputs)
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
                val stringData = inputs[0] as PandaStringConstant
                val newExpr = PandaNewExpr(stringData.value, inputs.drop(1))
                handle(newExpr)
            }

            opcode == "Intrinsic.createemptyarray" -> {
                val createEmptyExpr = PandaCreateEmptyArrayExpr()
                handle(createEmptyExpr)
            }

            opcode == "Intrinsic.throw" -> {
                val throwInst = PandaThrowInst(locationFromOp(this), inputs[0])
                method.insts += throwInst
            }

            opcode == "Intrinsic.return" -> {
                val returnInst = PandaReturnInst(locationFromOp(this), inputs.getOrNull(0))
                method.insts += returnInst
            }

            opcode == "Intrinsic.returnundefined" -> {
                val returnInst = PandaReturnInst(locationFromOp(this), PandaUndefinedConstant)
                method.insts += returnInst
            }

            opcode == "Intrinsic.istrue" -> {
                val eqExpr = PandaEqExpr(inputs[0], PandaNumberConstant(1))
                handle(eqExpr)
            }

            opcode == "Intrinsic.isfalse" -> {
                val eqExpr = PandaEqExpr(inputs[0], PandaNumberConstant(0))
                handle(eqExpr)
            }

            opcode == "Intrinsic.ldfalse" -> {
                val falseConstant = PandaBoolConstant(false)
                handle(falseConstant)
            }

            opcode == "Intrinsic.ldtrue" -> {
                val trueConstant = PandaBoolConstant(true)
                handle(trueConstant)
            }

            opcode == "Intrinsic.ldnull" -> {
                outputs.forEach { output ->
                    addInput(method, id(), output, PandaNullConstant)
                }
            }

            opcode == "Intrinsic.greater" -> {
                val gtExpr = PandaGtExpr(inputs[0], inputs[1])
                handle(gtExpr)
            }

            opcode == "Intrinsic.less" -> {
                val ltExpr = PandaLtExpr(inputs[0], inputs[1])
                handle(ltExpr)
            }

            opcode == "Intrinsic.stricteq" -> {
                val strictEqExpr = PandaStrictEqExpr(inputs[0], inputs[1])
                handle(strictEqExpr)
            }

            opcode == "Intrinsic.tryldglobalbyname" -> {
                val name = stringData ?: error("No string data")
                val out = PandaStringConstant(name)
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                }
            }

            opcode == "Intrinsic.ldobjbyname" -> {
                val name = stringData ?: error("No string data")
                val out = PandaLoadedValue(inputs[0], PandaStringConstant(name))
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                    // for call insts not to have "instance.object" and "instance, object" in inputs
                    method.idToInputs[output]?.remove(inputs[0])
                }
            }

            opcode == "Intrinsic.ldobjbyvalue" -> {
                val out = PandaArrayAccess(
                    array = inputs[0],
                    index = inputs[1],
                    type = PandaAnyType
                )
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                }
            }

            opcode == "Intrinsic.ldglobalvar" -> {
                val name = stringData ?: error("No string data")
                val out = PandaInstanceCallValueImpl(
                    PandaThis(PandaClassTypeImpl(method.clazz.name)),
                    PandaStringConstant(name)
                )
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                }
            }

            opcode == "Intrinsic.stglobalvar" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stobjbyname" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.ldhole" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.ldundefined" -> {
                handle(PandaUndefinedConstant)
            }

            opcode == "Intrinsic.defineclasswithbuffer" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.getiterator" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.createarraywithbuffer" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stconsttoglobalrecord" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.callthis0" -> {
                val instCallValue = inputs[0] as PandaInstanceCallValue
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.clazz.name
                        )
                    },
                    args = emptyList(),
                    instance = instCallValue.instance
                )
                handle2(callExpr)
            }

            opcode == "Intrinsic.callthis1" -> {
                val instCallValue = inputs.find<PandaInstanceCallValue>().first()
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.clazz.name
                        )
                    },
                    args = inputs.filterNot { it == instCallValue },
                    instance = instCallValue.instance
                )
                handle2(callExpr)
            }

            opcode == "Intrinsic.callthis2" -> {
                val instCallValue = inputs[0] as PandaInstanceCallValue
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.clazz.name
                        )
                    },
                    args = listOf(inputs[1], inputs[2]),
                    instance = instCallValue.instance
                )
                handle2(callExpr)
            }

            opcode == "Intrinsic.callthis3" -> {
                val instCallValue = inputs[0] as PandaInstanceCallValue
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.clazz.name
                        )
                    },
                    args = listOf(inputs[1], inputs[2], inputs[3]),
                    instance = instCallValue.instance
                )
                handle2(callExpr)
            }

            opcode == "Intrinsic.callarg0" -> {
                val instCallValue = inputs.find<PandaInstanceCallValue>().first()
                val (instanceName, methodName) = instCallValue.getClassAndMethodName()
                val callExpr = PandaVirtualCallExpr(
                    lazyMethod = lazy {
                        method.pandaMethod.project.findMethodByInstanceOrEmpty(
                            instanceName,
                            methodName,
                            method.clazz.name
                        )
                    },
                    args = emptyList(),
                    instance = instCallValue.instance
                )
                handle2(callExpr)
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
                            method.clazz.name
                        )
                    },
                    args = args,
                    instance = instCallValue.instance
                )
                handle2(callExpr)
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
                            method.clazz.name
                        )
                    },
                    args = args,
                    instance = instCallValue.instance
                )
                handle2(callExpr)
            }

            opcode == "Intrinsic.inc" -> {
                val addExpr = PandaAddExpr(inputs[0], PandaNumberConstant(1))
                handle(addExpr)
            }

            opcode == "Intrinsic.add2" -> {
                val addExpr = PandaAddExpr(inputs[0], inputs[1])
                handle(addExpr)
            }

            opcode == "Intrinsic.sub2" -> {
                val subExpr = PandaSubExpr(inputs[0], inputs[1])
                handle(subExpr)
            }

            opcode == "Intrinsic.mul2" -> {
                val mulExpr = PandaMulExpr(inputs[0], inputs[1])
                handle(mulExpr)
            }

            opcode == "Intrinsic.div2" -> {
                val divExpr = PandaDivExpr(inputs[0], inputs[1])
                handle(divExpr)
            }

            opcode == "Intrinsic.neg" -> {
                val negExpr = PandaNegExpr(inputs[0])
                handle(negExpr)
            }

            opcode == "Phi" -> {
                if ((users.size == 1 && users[0] == id) || users.isEmpty()) return@with

                val phiExpr = PandaPhiValue(lazy { inputsViaOp(this) })
                handle(phiExpr)
            }

            opcode == "CatchPhi" -> {
                fun pathToCatchBlock(
                    currentBB: PandaBasicBlock,
                    acc: List<PandaInstRef>,
                    targetId: Int,
                ): List<PandaInstRef>? {
                    val newList = acc + (currentBB.start.index..currentBB.end.index)
                        .mapNotNull { if (it == -1) null else PandaInstRef(it) }

                    for (succBBId in currentBB.successors) {
                        if (succBBId == targetId) return acc
                        method.idToBB[succBBId]?.let { succBB ->
                            pathToCatchBlock(succBB, newList, targetId)?.let { return it }
                        }
                    }

                    return null
                }

                // Catch basic block contains multiple CatchPhi, but only the last one contains "error" variable.
                // This CatchPhi is the last one, so ignoring all the other ones before it.
                val nextInstOpcode = basicBlock.insts.getOrNull(opIdx + 1)?.opcode ?: ""
                if (nextInstOpcode != "CatchPhi") {
                    val tryBBId = env.getTryBlockBBId(basicBlock.id)
                    val tryBB = method.idToBB[tryBBId] ?: error("No try basic block saved in environment for $op")
                    val path = pathToCatchBlock(tryBB, emptyList(), basicBlock.id)
                        ?: error("No path from basic block $tryBBId to ${basicBlock.id}")

                    val throwable = PandaCaughtError()

                    method.insts += PandaCatchInst(
                        location = locationFromOp(this),
                        throwable = throwable,
                        _throwers = path.sortedBy { it.index }
                    )

                    outputs.forEach { output ->
                        addInput(method, id(), output, throwable)
                    }
                }
            }

            opcode == "Try" -> {
                assert(basicBlock.successors.size == 2)
                val tryBBid = basicBlock.successors[0]
                val catchBBid = basicBlock.successors[1]
                // Order is crucial for CatchPhi processor
                assert(tryBBid < catchBBid)

                changeTraversalStrategy(basicBlock, TraversalType.TRY_BLOCK)

                env.setTryBlockBBId(catchBBid, tryBBid)
            }

            opcode == "Intrinsic.sttoglobalrecord" -> {
                val lv = PandaLocalVar(method.currentLocalVarId++, PandaAnyType)
                method.insts += PandaAssignInst(locationFromOp(this), lv, inputs[0])
                env.setLocalVar(stringData!!, lv)
            }

            opcode == "Intrinsic.trystglobalbyname" -> {
                val lv = env.getLocalVar(stringData!!)
                    ?: error("Can't load local var from environment for literal \"$stringData\"")
                method.insts += PandaAssignInst(locationFromOp(this), lv, inputs[0])
            }

            else -> checkIgnoredInstructions(this)
        }
    }

    // private fun ProgramInst.handleOutputs(
    //     outputs: List<Int>,
    //     method: ProgramMethod,
    //     callExpr: PandaVirtualCallExpr,
    // ) {
    //     if (outputs.isEmpty()) {
    //         method.insts.add(PandaCallInst(locationFromOp(this), callExpr))
    //     } else {
    //         val lv = PandaLocalVar(method.currentLocalVarId++)
    //         val assign = PandaAssignInst(locationFromOp(this), lv, callExpr)
    //         outputs.forEach { output ->
    //             addInput(method, id(), output, lv)
    //         }
    //         method.insts.add(assign)
    //     }
    // }

    private fun checkIgnoredInstructions(op: ProgramInst) = with(op) {
        when (opcode) {
            // Unuseful
            "SaveState" -> {}
            "Intrinsic.definefunc" -> {}
            "Intrinsic.copyrestargs" -> {}
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

        val basicBlocks = op.currentMethod().basicBlocks.sortedBy { it.id }

        tailrec fun setTargetRec(bb: ProgramBasicBlock): PandaInstRef {
            return if (bb.start == -1) {
                val newBBidx = bb.successors.first().takeIf { bb.successors.size == 1 }
                    ?: error("Can't resolve next instruction for conditional jump of basic block id ${bb.id}")
                setTargetRec(basicBlocks[newBBidx])
            } else {
                op.currentMethod().idToBB[bb.id]!!.start
            }
        }

        val trueBranch = lazy {
            setTargetRec(basicBlocks[op.basicBlock.successors[0]])
        }

        val falseBranch = lazy {
            setTargetRec(basicBlocks[op.basicBlock.successors[1]])
        }

        return PandaIfInst(locationFromOp(op), condExpr, trueBranch, falseBranch)
    }

    private fun mapImm(imm: Int?): PandaConstant {
        return imm?.let { PandaNumberConstant(it) } ?: PandaNullConstant
    }

    private fun mapConstant(op: ProgramInst): PandaConstant = when (mapType(op.type)) {
        is PandaNumberType -> PandaNumberConstant(Integer.decode(op.value.toString()))
        else -> TODOConstant(op.value.toString())
    }
}
