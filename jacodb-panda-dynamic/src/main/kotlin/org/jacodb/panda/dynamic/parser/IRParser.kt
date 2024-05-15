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
import org.jacodb.panda.dynamic.api.PandaArrayType
import org.jacodb.panda.dynamic.api.PandaAssignInst
import org.jacodb.panda.dynamic.api.PandaBasicBlock
import org.jacodb.panda.dynamic.api.PandaBoolConstant
import org.jacodb.panda.dynamic.api.PandaBuiltInError
import org.jacodb.panda.dynamic.api.PandaCallExpr
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
import org.jacodb.panda.dynamic.api.PandaExpExpr
import org.jacodb.panda.dynamic.api.PandaExpr
import org.jacodb.panda.dynamic.api.PandaGeExpr
import org.jacodb.panda.dynamic.api.PandaGotoInst
import org.jacodb.panda.dynamic.api.PandaGtExpr
import org.jacodb.panda.dynamic.api.PandaIfInst
import org.jacodb.panda.dynamic.api.PandaInst
import org.jacodb.panda.dynamic.api.PandaInstLocation
import org.jacodb.panda.dynamic.api.PandaInstRef
import org.jacodb.panda.dynamic.api.PandaInstanceVirtualCallExpr
import org.jacodb.panda.dynamic.api.PandaLeExpr
import org.jacodb.panda.dynamic.api.PandaLengthExpr
import org.jacodb.panda.dynamic.api.PandaLexVar
import org.jacodb.panda.dynamic.api.PandaLoadedValue
import org.jacodb.panda.dynamic.api.PandaLocalVar
import org.jacodb.panda.dynamic.api.PandaLtExpr
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.api.PandaMethodConstant
import org.jacodb.panda.dynamic.api.PandaModExpr
import org.jacodb.panda.dynamic.api.PandaMulExpr
import org.jacodb.panda.dynamic.api.PandaNegExpr
import org.jacodb.panda.dynamic.api.PandaNeqExpr
import org.jacodb.panda.dynamic.api.PandaNewExpr
import org.jacodb.panda.dynamic.api.PandaNewLexenvInst
import org.jacodb.panda.dynamic.api.PandaNullConstant
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaNumberType
import org.jacodb.panda.dynamic.api.PandaParameterInfo
import org.jacodb.panda.dynamic.api.PandaPhiValue
import org.jacodb.panda.dynamic.api.PandaPopLexenvInst
import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.panda.dynamic.api.PandaStrictEqExpr
import org.jacodb.panda.dynamic.api.PandaStrictNeqExpr
import org.jacodb.panda.dynamic.api.PandaStringConstant
import org.jacodb.panda.dynamic.api.PandaSubExpr
import org.jacodb.panda.dynamic.api.PandaThis
import org.jacodb.panda.dynamic.api.PandaThrowInst
import org.jacodb.panda.dynamic.api.PandaToNumericExpr
import org.jacodb.panda.dynamic.api.PandaType
import org.jacodb.panda.dynamic.api.PandaTypeofExpr
import org.jacodb.panda.dynamic.api.PandaUndefinedConstant
import org.jacodb.panda.dynamic.api.PandaValue
import org.jacodb.panda.dynamic.api.PandaValueByInstance
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

    private var program: Program? = null
    fun getProgram(): Program {
        if (this.program == null) {
            this.program = Json.decodeFromString(json)
        }
        // mapProgramIR(this.program!!)
        return this.program!!
    }

    fun getProject(): PandaProject {
        val program = getProgram()
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
    private fun setMethodTypes(method: ProgramMethod) {
        if (tsFunctions == null) return
        if (method.name == "func_main_0") return
        tsFunctions.find { tsFunc ->
            tsFunc.name == method.name &&
                    // here comes the result of comment above
                    tsFunc.containingClass?.name == method.clazz.name
        }?.let { tsFunc ->
            method.paramTypes.addAll(tsFunc.arguments)
//            method.type = tsFunc.returnType
//            method.parameterInfos = method.parameterInfos.zip(tsFunc.arguments).map { (paramInfo, type) ->
//                PandaParameterInfo(
//                    index = paramInfo.index,
//                    type
//                )
//            }
            // TODO: Add class constructor to GLOBAL
        } ?: logger.error("No method ${method.name} with superclass ${method.clazz.name} was found in parsed functions")
    }

    private fun mapMethods(program: Program): List<PandaClass> {
        return program.classes.map { clazz ->
            val pandaMethods = clazz.properties.map { property ->
                property.method.also { method ->
                    val pandaMethod = method.pandaMethod
                    pandaMethod.blocks = method.idToBB.values.toList()
                    pandaMethod.instructions = method.insts
                    pandaMethod.parameterInfos = method.parameters
                    pandaMethod.className = clazz.name
                    pandaMethod.localVarsCount = method.currentLocalVarId + 1
                }.pandaMethod
            }
            val pandaClass = PandaClass(clazz.name, clazz.superClass, pandaMethods)
            pandaMethods.forEach {
                it.enclosingClass = pandaClass
            }
            pandaClass
        }
    }

    private fun mapInstructions(program: Program) {
        val programMethods: List<ProgramMethod> = program.classes
            .flatMap { it.properties }
            .map { it.method }

        val main = programMethods.filter { it.signature == ".func_main_0" }
        val other = programMethods.filter { it.signature != ".func_main_0" }

        (main + other).forEach { currentMethod ->
            setMethodTypes(currentMethod)

            val traversalManager = IRTraversalManager(
                programMethod = currentMethod,
                irParser = this
            )

            traversalManager.run()
        }

        programMethods.forEach { it.buildInsts() }

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

            val blocks = method.idToBB.toMap().values.sortedBy { it.start }

            gotoToRemove.forEach { gotoInst ->
                method.insts.remove(gotoInst)

                // Fixing range for basic blocks that are after the removed goto
                var startIdx = blocks.indexOfFirst { it.start.index > gotoInst.location.index }
                while (startIdx < blocks.size) {
                    val b = blocks[startIdx]
                    b.updateRange(
                        PandaInstRef(b.start.index - 1),
                        PandaInstRef(b.end.index - 1)
                    )
                    startIdx++
                }

                // Fixing end of range for the enclosing basic block of the removed goto
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
        method.idToInputs.getOrPut(outputId) { MutableList(outputInst.inputs.size) { null } }[index] = input
    }

    internal fun mapOpcode(
        op: ProgramInst,
        method: ProgramMethod,
        env: IREnvironment,
        opIdx: Int,
        changeTraversalStrategy: (ProgramBasicBlock, TraversalType) -> Unit,
    ) = with(op) {
        val inputs = inputsViaOp(this)
        val outputs = outputs()

        fun handle(expr: PandaExpr) {
            val lv = PandaLocalVar(method.currentLocalVarId++, if (expr is PandaNewExpr) expr.type else PandaAnyType)
            outputs.forEach { output ->
                addInput(method, id(), output, lv)
            }
            val assignment = PandaAssignInst(locationFromOp(this), lv, expr)
            env.setLocalAssignment(method.signature, lv, assignment)
            method.pushInst(assignment)
        }

        fun handle2(callExpr: PandaCallExpr) {
            if (outputs.isEmpty()) {
                method.pushInst(PandaCallInst(locationFromOp(this), callExpr))
            } else {
                handle(callExpr)
            }
        }

        when {
            opcode == "Parameter" -> {
                val c = id() - ARG_THRESHOLD

                val out = if (id() >= ARG_THRESHOLD) {
                    val type = method.paramTypes.getOrElse(c) { _ -> PandaAnyType }
                    val arg = PandaArgument(c, type = type)
                    val argInfo = PandaParameterInfo(c, type)
                    method.parameters += argInfo
                    arg
                } else {
                    PandaThis(PandaClassTypeImpl(method.clazz.name))
                }

                outputs.forEach { output ->
                    addInput(method, id(), output, out)
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

            opcode == "Intrinsic.strictnoteq" -> {
                val neqExpr = PandaStrictNeqExpr(inputs[0], inputs[1])
                handle(neqExpr)
            }

            opcode.startsWith("Compare") -> {
                val cmpOp = operator?.let(PandaCmpOp::valueOf) ?: error("No operator")
                val cmpExpr = PandaCmpExpr(cmpOp, inputs[0], inputs[1])
                handle(cmpExpr)
            }

            opcode.startsWith("IfImm") -> {
                method.pushInst(mapIfInst(this, inputs))
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
                val input = inputs[0]
                val stringData = when {
                    input is PandaLocalVar -> {
                        input.toString()
                    }

                    input is PandaLoadedValue -> {
                        input.getLoadedValueClassName()
                    }

                    input is PandaStringConstant -> {
                        input.value
                    }

                    input is PandaValueByInstance -> {
                        input.getClassAndMethodName().toString()
                    }

                    else -> error("No string data")
                }
//                        as PandaStringConstant
                val newExpr = PandaNewExpr(stringData, inputs.drop(1))

                handle(newExpr)
            }

            opcode == "Intrinsic.createemptyarray" -> {
                val createEmptyExpr = PandaCreateEmptyArrayExpr()
                handle(createEmptyExpr)
            }

            opcode == "Intrinsic.throw" -> {
                val throwInst = PandaThrowInst(locationFromOp(this), inputs[0])
                method.pushInst(throwInst)
            }

            opcode == "Intrinsic.throw.constassignment" -> {
                val throwInst = PandaThrowInst(locationFromOp(this), PandaBuiltInError("ConstAssignmentError"))
                method.pushInst(throwInst)
            }

            opcode == "Intrinsic.return" -> {
                val returnInst = PandaReturnInst(locationFromOp(this), inputs.getOrNull(0))
                method.pushInst(returnInst)
            }

            opcode == "Intrinsic.returnundefined" -> {
                val returnInst = PandaReturnInst(locationFromOp(this), PandaUndefinedConstant)
                method.pushInst(returnInst)
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

            opcode == "Intrinsic.greatereq" -> {
                val geExpr = PandaGeExpr(inputs[0], inputs[1])
                handle(geExpr)
            }

            opcode == "Intrinsic.less" -> {
                val ltExpr = PandaLtExpr(inputs[0], inputs[1])
                handle(ltExpr)
            }

            opcode == "Intrinsic.lesseq" -> {
                val leExpr = PandaLeExpr(inputs[0], inputs[1])
                handle(leExpr)
            }

            opcode == "Intrinsic.stricteq" -> {
                val strictEqExpr = PandaStrictEqExpr(inputs[0], inputs[1])
                handle(strictEqExpr)
            }

            opcode == "Intrinsic.tryldglobalbyname" -> {
                val name = stringData ?: error("No string data")
                val out = method.nameToLocalVarId.getOrDefault(name, PandaLoadedValue(PandaStringConstant(name)))
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                }
            }

            opcode == "Intrinsic.ldobjbyname" -> {
                val name = stringData ?: error("No string data")
                val out = if (inputs[0].type is PandaArrayType && name == "length") {
                    val expr = PandaLengthExpr(inputs[0])
                    val lv = PandaLocalVar(method.currentLocalVarId++, expr.type)
                    val assignment = PandaAssignInst(locationFromOp(this), lv, expr)
                    method.pushInst(assignment)
                    env.setLocalAssignment(method.signature, lv, assignment)
                    lv
                } else PandaValueByInstance(inputs[0], name)
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                    // for call insts not to have "instance.object" and "instance, object" in inputs
                    // method.idToInputs[output]?.remove(inputs[0])
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
                val out = PandaValueByInstance(PandaThis(PandaClassTypeImpl("GLOBAL")), name)
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                }
            }

            opcode == "Intrinsic.stglobalvar" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stobjbyname" -> {
                val objectName = stringData ?: error("No string data")
                val instance = inputs[0]
                val value = inputs[1]

                val property = PandaValueByInstance(instance, objectName)
                method.pushInst(PandaAssignInst(locationFromOp(this), property, value))
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

            opcode == "Intrinsic.callruntime.definefieldbyvalue" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.newlexenv" -> {
                env.newLexenv()
                method.pushInst(PandaNewLexenvInst(locationFromOp(this)))
            }

            opcode == "Intrinsic.poplexenv" -> {
                env.popLexenv()
                method.pushInst(PandaPopLexenvInst(locationFromOp(this)))
            }

            opcode == "Intrinsic.stlexvar" -> {
                val lexvar = PandaLexVar(
                    lexenv ?: error("No lexenv"),
                    lexvar ?: error("No lexvar"),
                    PandaAnyType
                )
                val value = inputs[0]
                env.setLexvar(lexvar.lexenvIndex, lexvar.lexvarIndex, method.signature, value)
                method.pushInst(PandaAssignInst(locationFromOp(this), lexvar, value))
            }

            opcode == "Intrinsic.ldlexvar" -> {
                val lexvar = PandaLexVar(
                    lexenv ?: error("No lexenv"),
                    lexvar ?: error("No lexvar"),
                    PandaAnyType
                )
                handle(PandaLoadedValue(lexvar))
            }

            opcode == "Intrinsic.definemethod" -> {
                val name = functionName ?: error("No functionName")
                val out = PandaValueByInstance(inputs[0], name)
                outputs.forEach { output ->
                    addInput(method, id(), output, out)
                    // for call insts not to have "instance.object" and "instance, object" in inputs
                    method.idToInputs[output]?.remove(inputs[0])
                }
            }

            opcode == "Intrinsic.definefieldbyname" -> {
                val fieldName = stringData ?: error("No stringData")

                val instance = inputs[0]
                val value = inputs[1]

                val property = PandaValueByInstance(instance, fieldName)
                method.pushInst(PandaAssignInst(locationFromOp(this), property, value))
            }

            opcode == "Intrinsic.definefunc" -> {
                val methodConstant = PandaMethodConstant(functionName ?: error("No function name"))
                handle(methodConstant)
            }

            opcode == "Intrinsic.getiterator" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.createarraywithbuffer" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.ldexternalmodulevar" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.throw.undefinedifholewithname" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.createemptyobject" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stmodulevar" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stobjbyvalue" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.createobjectwithbuffer" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.ldglobal" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.starrayspread" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.supercallspread" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.throw.ifsupernotcorrectcall" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.ldlocalmodulevar" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.asyncfunctionenter" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.asyncfunctionawaituncaught" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.suspendgenerator" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.resumegenerator" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.getresumemode" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.callthisrange" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.asyncfunctionresolve" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.asyncfunctionreject" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.newobjapply" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.supercallthisrange" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.or2" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.definegettersetterbyvalue" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stownbyname" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.ldsuperbyname" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.instanceof" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.dec" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.throw.ifnotobject" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.copydataproperties" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stownbyindex" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.apply" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.callrange" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.getpropiterator" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.getnextpropname" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stownbyvalue" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.delobjprop" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stsuperbyname" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.tonumber" -> {
                val todoExpr = TODOExpr(opcode, inputs) // TODO
                handle(todoExpr)
            }

            opcode == "Intrinsic.stconsttoglobalrecord" -> {
                val variableName = stringData?.takeIf { it.isNotEmpty() }
                    ?: run {
                        logger.error("No stringData for stconsttoglobalrecord")
                        "STRINGDATANAME"
                    }
                val localVar = inputs[0]
                method.nameToLocalVarId[variableName] = localVar
            }

            opcode == "Intrinsic.callthis0" -> {
                val callExpr = getVirtualCallExprByInputs(inputs, method, env)
                handle2(callExpr)
            }

            opcode == "Intrinsic.callthis1" -> {
                val callExpr = getVirtualCallExprByInputs(inputs, method, env)
                handle2(callExpr)
            }

            opcode == "Intrinsic.callthis2" -> {
                val callExpr = getVirtualCallExprByInputs(inputs, method, env)
                handle2(callExpr)
            }

            opcode == "Intrinsic.callthis3" -> {
                val callExpr = getVirtualCallExprByInputs(inputs, method, env)
                handle2(callExpr)
            }

            opcode == "Intrinsic.callarg0" -> {
                val callExpr = getVirtualCallExprByInputs(inputs, method, env)
                handle2(callExpr)
            }

            opcode == "Intrinsic.callarg1" -> {
                val callExpr = getVirtualCallExprByInputs(inputs, method, env)
                handle2(callExpr)
            }

            opcode == "Intrinsic.callargs2" -> {
                val callExpr = getVirtualCallExprByInputs(inputs, method, env)
                handle2(callExpr)
            }

            opcode == "Intrinsic.callargs3" -> {
                val callExpr = getVirtualCallExprByInputs(inputs, method, env)
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

            opcode == "Intrinsic.mod2" -> {
                val modExpr = PandaModExpr(inputs[0], inputs[1])
                handle(modExpr)
            }

            opcode == "Intrinsic.exp" -> {
                val expExpr = PandaExpExpr(inputs[0], inputs[1])
                handle(expExpr)
            }

            opcode == "Intrinsic.neg" -> {
                val negExpr = PandaNegExpr(inputs[0])
                handle(negExpr)
            }

            opcode == "Phi" -> {
                if ((users.size == 1 && users[0] == id) || users.isEmpty()) return@with
                val phiExpr = PandaPhiValue(lazy { inputsViaOp(this) }, op.inputBlocks)
                handle(phiExpr)
            }

            opcode == "CatchPhi" -> {
                // Catch basic block contains multiple CatchPhi, but only the last one contains "error" variable.
                // This CatchPhi is the last one, so ignoring all the other ones before it.
                val nextInstOpcode = basicBlock.insts.getOrNull(opIdx + 1)?.opcode ?: ""
                if (nextInstOpcode != "CatchPhi") {
                    val throwable = PandaCaughtError()
                    val tryBBId = env.getTryBlockBBId(basicBlock.id)
                    val tryBB = method.idToBB[tryBBId] ?: error("No try basic block saved in environment for $op")
                    method.pushBuilder {
                        fun pathToCatchBlock(
                            currentBB: PandaBasicBlock,
                            acc: List<PandaInstRef>,
                            targetId: Int,
                        ): List<PandaInstRef>? {
                            val newList = acc + (currentBB.start.index..currentBB.end.index)
                                .mapNotNull { if (it == -1) null else PandaInstRef(it) }

                            for (succBBId in currentBB.successors) {
                                if (succBBId == targetId) return acc
                                idToBB[succBBId]?.let { succBB ->
                                    pathToCatchBlock(succBB, newList, targetId)?.let { return it }
                                }
                            }

                            return null
                        }

                        val path = pathToCatchBlock(tryBB, emptyList(), basicBlock.id)
                            ?: error("No path from basic block $tryBBId to ${basicBlock.id}")

                        PandaCatchInst(
                            location = locationFromOp(this@with),
                            throwable = throwable,
                            _throwers = path.sortedBy { it.index }
                        )
                    }

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
                val assignment = PandaAssignInst(locationFromOp(this), lv, inputs[0])
                method.pushInst(assignment)
                env.setLocalAssignment(method.signature, lv, assignment)
                env.setLocalVar(stringData!!, lv)
            }

            opcode == "Intrinsic.trystglobalbyname" -> {
                val lv = env.getLocalVar(stringData!!)
                    ?: error("Can't load local var from environment for literal \"$stringData\"")
                method.pushInst(PandaAssignInst(locationFromOp(this), lv, inputs[0]))
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

    private fun getVirtualCallExprByInputs(
        inputs: List<PandaValue>,
        method: ProgramMethod,
        env: IREnvironment,
    ): PandaCallExpr {
        val instCallValue = inputs.find<PandaValueByInstance>().lastOrNull()
        val loadedValue = inputs.find<PandaLoadedValue>().lastOrNull()
        val localVar = inputs.find<PandaLocalVar>().lastOrNull()
        val arrayAccess = inputs.find<PandaArrayAccess>().lastOrNull()
        val stringConstant = inputs.find<PandaStringConstant>().lastOrNull()
        instCallValue?.let { instValue ->
            return PandaInstanceVirtualCallExpr(
                lazyMethod = lazy {
                    val (instanceName, methodName) = instValue.getClassAndMethodName()
                    method.pandaMethod.project.findMethodByInstanceOrEmpty(
                        instanceName,
                        methodName,
                        instValue.className
                    )
                },
                args = inputs.filterNot { it == instValue },
                instance = instValue.instance
            )
        }
        loadedValue?.let { pandaLoadedValue ->
            return PandaInstanceVirtualCallExpr(
                lazyMethod = lazy {
                    val name = pandaLoadedValue.getLoadedValueClassName()
                    PandaMethod(name)
                },
                args = inputs.filterNot { it == pandaLoadedValue },
                instance = pandaLoadedValue
            )
        }
        stringConstant?.let { pandaString ->
            return PandaInstanceVirtualCallExpr(
                lazyMethod = lazy {
                    val name = pandaString.value
                    PandaMethod(name)
                },
                args = inputs.filterNot { it == pandaString },
                instance = PandaThis(PandaClassTypeImpl("GLOBAL"))
            )

        }
        localVar?.let { pandaLocalVar ->
            return PandaVirtualCallExpr(
                lazyMethod = lazy {
                    val value = method.getLocalVarRoot(env, method.signature, pandaLocalVar)
                    if (value is PandaMethodConstant) {
                        val methodName = value.methodName.drop(1)
                        val className = method.pandaMethod.className ?: "GLOBAL"
                        fun findIn(className: String) = method.pandaMethod.project.findMethodOrNull(methodName, className)
                        findIn(className) ?: findIn("GLOBAL")
                            ?: error("Could not find method: $methodName")
                    } else {
                        PandaMethod(value.typeName)
                    }
                },
                args = inputs.filterNot { it == pandaLocalVar },
            )
        }
        arrayAccess?.let { pandaArrayAccess ->
            return PandaInstanceVirtualCallExpr(
                lazyMethod = lazy {
                    val name = pandaArrayAccess.array.type.typeName
                    PandaMethod(name)
                },
                args = inputs.filterNot { it == pandaArrayAccess },
                instance = pandaArrayAccess.array
            )
        }

        error("No instance or loaded value found in inputs")
    }

    private fun checkIgnoredInstructions(op: ProgramInst) = with(op) {
        when (opcode) {
            // Unuseful
            "SaveState" -> {}
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
