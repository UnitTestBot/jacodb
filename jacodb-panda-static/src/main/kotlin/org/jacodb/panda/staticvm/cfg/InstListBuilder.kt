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

@file:Suppress("MemberVisibilityCanBePrivate")

package org.jacodb.panda.staticvm.cfg

import org.jacodb.panda.staticvm.classpath.PandaArrayType
import org.jacodb.panda.staticvm.classpath.PandaMethod
import org.jacodb.panda.staticvm.classpath.PandaPrimitiveType
import org.jacodb.panda.staticvm.classpath.PandaPrimitives
import org.jacodb.panda.staticvm.classpath.PandaType
import org.jacodb.panda.staticvm.ir.PandaAShlInstIr
import org.jacodb.panda.staticvm.ir.PandaAShrInstIr
import org.jacodb.panda.staticvm.ir.PandaAddInstIr
import org.jacodb.panda.staticvm.ir.PandaAndInstIr
import org.jacodb.panda.staticvm.ir.PandaBasicBlockIr
import org.jacodb.panda.staticvm.ir.PandaBoundsCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaCallLaunchStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaCallLaunchVirtualInstIr
import org.jacodb.panda.staticvm.ir.PandaCallStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaCallVirtualInstIr
import org.jacodb.panda.staticvm.ir.PandaCastInstIr
import org.jacodb.panda.staticvm.ir.PandaCatchPhiInstIr
import org.jacodb.panda.staticvm.ir.PandaCheckCastInstIr
import org.jacodb.panda.staticvm.ir.PandaCmpInstIr
import org.jacodb.panda.staticvm.ir.PandaCompareInstIr
import org.jacodb.panda.staticvm.ir.PandaConstantInstIr
import org.jacodb.panda.staticvm.ir.PandaDivInstIr
import org.jacodb.panda.staticvm.ir.PandaIfImmInstIr
import org.jacodb.panda.staticvm.ir.PandaInitClassInstIr
import org.jacodb.panda.staticvm.ir.PandaInstIr
import org.jacodb.panda.staticvm.ir.PandaInstIrVisitor
import org.jacodb.panda.staticvm.ir.PandaIntrinsicInstIr
import org.jacodb.panda.staticvm.ir.PandaIsInstanceInstIr
import org.jacodb.panda.staticvm.ir.PandaLenArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadAndInitClassInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadClassInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadObjectInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadRuntimeClassInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadStringInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadTypeInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadUndefinedInstIr
import org.jacodb.panda.staticvm.ir.PandaModInstIr
import org.jacodb.panda.staticvm.ir.PandaMulInstIr
import org.jacodb.panda.staticvm.ir.PandaNegInstIr
import org.jacodb.panda.staticvm.ir.PandaNegativeCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaNewArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaNewObjectInstIr
import org.jacodb.panda.staticvm.ir.PandaNotInstIr
import org.jacodb.panda.staticvm.ir.PandaNullCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaNullPtrInstIr
import org.jacodb.panda.staticvm.ir.PandaOrInstIr
import org.jacodb.panda.staticvm.ir.PandaParameterInstIr
import org.jacodb.panda.staticvm.ir.PandaPhiInstIr
import org.jacodb.panda.staticvm.ir.PandaRefTypeCheckInstIr
import org.jacodb.panda.staticvm.ir.PandaReturnInstIr
import org.jacodb.panda.staticvm.ir.PandaReturnVoidInstIr
import org.jacodb.panda.staticvm.ir.PandaSafePointInstIr
import org.jacodb.panda.staticvm.ir.PandaSaveStateDeoptimizeInstIr
import org.jacodb.panda.staticvm.ir.PandaSaveStateInstIr
import org.jacodb.panda.staticvm.ir.PandaShlInstIr
import org.jacodb.panda.staticvm.ir.PandaShrInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreObjectInstIr
import org.jacodb.panda.staticvm.ir.PandaStoreStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaSubInstIr
import org.jacodb.panda.staticvm.ir.PandaTerminatingInstIr
import org.jacodb.panda.staticvm.ir.PandaThrowInstIr
import org.jacodb.panda.staticvm.ir.PandaTryInstIr
import org.jacodb.panda.staticvm.ir.PandaXorInstIr
import org.jacodb.panda.staticvm.ir.PandaZeroCheckInstIr
import org.jacodb.panda.staticvm.utils.OneDirectionGraph
import org.jacodb.panda.staticvm.utils.SCCs
import org.jacodb.panda.staticvm.utils.inTopsortOrder
import org.jacodb.panda.staticvm.utils.runDP
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.typeOf

fun interface InstBuilder {
    fun build(): PandaInst
}

fun interface ThrowInstBuilder : InstBuilder {
    override fun build(): PandaThrowInst
}

data class IrInstLocation(val block: Int, val index: Int)

class InstListBuilder(
    val method: PandaMethod,
    val blocks: List<PandaBasicBlockIr>,
) {
    val project = method.enclosingClass.project

    private val localVars = buildLocalVariables(method, blocks)
    private val locationMap = hashMapOf<IrInstLocation, Int>()
    private val blockIdMap = blocks.mapIndexed { index, block -> block.id to index }.toMap()

    private fun getBlock(id: Int): PandaBasicBlockIr = blocks[requireNotNull(blockIdMap[id])]

    private fun linearRef(location: IrInstLocation): PandaInstRef {
        val loc = locationMap[location]
            ?: error("No location $location for method: $method")
        return PandaInstRef(loc)
    }

    fun local(name: String): PandaLocalVar = localVars[name]
        ?: error("No local $name for method: $method")

    fun result(inst: PandaInstIr): PandaLocalVar = local(inst.id)

    private val instBuilders: MutableList<InstBuilder> = mutableListOf()

    private inline fun <reified T : PandaInst> push(noinline build: (PandaInstLocation) -> T) {
        when (typeOf<T>()) {
            typeOf<PandaThrowInst>() -> {
                @Suppress("UNCHECKED_CAST", "NAME_SHADOWING")
                val build = build as (PandaInstLocation) -> PandaThrowInst
                instBuilders += ThrowInstBuilder { build(PandaInstLocation(method, instBuilders.size)) }
            }

            else -> {
                instBuilders += InstBuilder { build(PandaInstLocation(method, instBuilders.size)) }
            }
        }
    }

    internal fun pushAssign(lhv: PandaValue, rhv: PandaExpr) {
        push { location ->
            PandaAssignInst(location, lhv, rhv)
        }
    }

    internal fun pushParameter(lhv: PandaValue, index: Int) {
        push { location ->
            PandaParameterInst(location, lhv, index)
        }
    }

    internal fun pushReturn(value: PandaValue?) {
        push { location ->
            PandaReturnInst(location, value)
        }
    }

    internal fun pushIf(
        conditionExpr: PandaConditionExpr,
        trueBranch: IrInstLocation,
        falseBranch: IrInstLocation,
    ) {
        push { location ->
            PandaIfInst(
                location = location,
                condition = conditionExpr,
                trueBranch = linearRef(trueBranch),
                falseBranch = linearRef(falseBranch)
            )
        }
    }

    internal fun pushGoto(target: IrInstLocation) {
        push { location ->
            PandaGotoInst(
                location = location,
                target = linearRef(target)
            )
        }
    }

    internal fun pushDoNothing() {
        push { location ->
            PandaDoNothingInst(location)
        }
    }

    internal fun pushCatchPhi(
        lhv: PandaValue,
        inputs: List<PandaValue>,
        throwers: List<String>,
    ) {
        push { location ->
            val throwerIndices = throwers.map { idMap[it] }.requireNoNulls()
            val (throwInputs, throwPredecessors) = (inputs zip throwerIndices).filter { (_, thrower) ->
                instBuilders[thrower] is ThrowInstBuilder
            }.unzip()
            val predecessors = throwPredecessors.map { PandaInstRef(it) }
            val phiExpr = PandaPhiExpr(lhv.type, throwInputs, predecessors)
            PandaAssignInst(
                location = location,
                lhv = lhv,
                rhv = phiExpr
            )
        }
    }

    internal fun pushPhi(
        lhv: PandaValue,
        inputs: List<PandaValue>,
        blocks: List<Int>,
    ) {
        push { location ->
            val phiExpr = PandaPhiExpr(lhv.type, inputs, blocks.map {
                linearRef(IrInstLocation(it, maxOf(0, getBlock(it).insts.lastIndex)))
            })
            PandaAssignInst(
                location = location,
                lhv = lhv,
                rhv = phiExpr
            )
        }
    }

    /*internal fun pushCatch(lhv: PandaValue, throwerIds: List<String>) = push { location ->
        val throwers = throwerIds.map(idMap::get).requireNoNulls().map(::PandaInstRef)
        DefaultInstBuilder(PandaAssignInst(location, lhv, PandaPhiExpr(lhv.type,  throwers))
    }*/

    internal fun pushThrow(error: PandaValue, catchers: List<Int>) {
        push { location ->
            PandaThrowInst(
                location = location,
                error = error,
                catchers = catchers.map { linearRef(IrInstLocation(it, 0)) }
            )
        }
    }

    private val idMap: MutableMap<String, Int> = hashMapOf()

    private val throwEdgeBuilders: MutableList<Pair<IrInstLocation, IrInstLocation>> = mutableListOf()

    init {
        val visitor = InstListBuilderVisitor()
        blocks.sortedBy { it.predecessors.size }.forEach { block ->
            block.insts.forEachIndexed { instIndex, inst ->
                visitor.location = IrInstLocation(block.id, instIndex)
                locationMap[visitor.location] = instBuilders.size
                idMap[inst.id] = instBuilders.size

                inst.accept(visitor)

                if (inst is PandaThrowInstIr) {
                    throwEdgeBuilders.addAll(inst.catchers.map { visitor.location to IrInstLocation(it, 0) })
                }
            }

            if (block.isTryBegin || block.isTryEnd) {
                pushGoto(IrInstLocation(block.successors.first(), 0))
            }

            block.successors.singleOrNull()?.let {
                if (block.insts.lastOrNull() !is PandaTerminatingInstIr) {
                    pushGoto(IrInstLocation(it, 0))
                }
            }

            if (block.insts.isEmpty()) {
                val loc = IrInstLocation(block.id, 0)
                locationMap[loc] = instBuilders.size
                pushDoNothing()
            }
        }
    }

    val instList: List<PandaInst> = instBuilders.map { it.build() }

    val throwEdges: List<Pair<PandaInstRef, PandaInstRef>> = throwEdgeBuilders.map { (from, to) ->
        linearRef(from) to linearRef(to)
    }
}

internal fun buildLocalVariables(
    pandaMethod: PandaMethod,
    blocks: List<PandaBasicBlockIr>,
): Map<String, PandaLocalVar> {
    val project = pandaMethod.enclosingClass.project

    val localVarsIndex = hashMapOf<String, PandaLocalVar>()

    val outputVarBuilder = OutputVarBuilder(pandaMethod)

    val varNodes = blocks.flatMap { block ->
        block.insts.mapNotNull { it.accept(outputVarBuilder) }
    }.associateBy { it.name }

    val graph = OneDirectionGraph(varNodes.values) { node ->
        when (node) {
            is LeafVarNode -> emptySet()
            is DependentVarNode -> node.bounds.mapTo(hashSetOf()) { requireNotNull(varNodes[it]) }
            is LoadArrayNode -> setOf(requireNotNull(varNodes[node.array]))
        }
    }

    val sccs = graph.SCCs()
    check(sccs.inTopsortOrder() != null)

    graph.SCCs().runDP { vars, inputTypes ->
        vars.map { lv ->
            when (lv) {
                is LeafVarNode -> lv.type

                is DependentVarNode -> project.commonType(inputTypes.values.flatten())
                    ?: error("No common type for ${inputTypes.values}")

                is LoadArrayNode -> {
                    val arrayTypes = inputTypes.values.flatten<PandaType>()
                    require(arrayTypes.all { it is PandaArrayType || it == project.objectClass.type }) {
                        println()
                    }
                    val elementTypes = arrayTypes.filterIsInstance<PandaArrayType>().map { it.elementType }
                    requireNotNull(project.commonType(elementTypes))
                }
            }.also {
                if (lv is ThisNode) {
                    localVarsIndex[lv.name] = PandaThis(lv.name, it)
                } else {
                    localVarsIndex[lv.name] = PandaLocalVarImpl(lv.name, it)
                }
            }
        }
    }

    return localVarsIndex
}

context(InstListBuilder)
class InstListBuilderVisitor : PandaInstIrVisitor<Unit> {
    lateinit var location: IrInstLocation

    private inline fun <reified T> convert(value: ULong, getter: ByteBuffer.() -> T) = ByteBuffer
        .allocate(16)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(value.toLong())
        .rewind()
        .let { it as ByteBuffer }
        .let(getter)

    private fun getConstant(value: ULong, type: PandaPrimitiveType) = when (type) {
        PandaPrimitiveType.VOID -> throw IllegalArgumentException("cannot create void constant")
        PandaPrimitiveType.BOOL -> PandaBoolean(value != 0UL)
        PandaPrimitiveType.BYTE -> PandaByte(value.toByte())
        PandaPrimitiveType.UBYTE -> PandaUByte(value.toUByte())
        PandaPrimitiveType.SHORT -> PandaShort(value.toShort())
        PandaPrimitiveType.USHORT -> PandaUShort(value.toUShort())
        PandaPrimitiveType.INT -> PandaInt(value.toInt())
        PandaPrimitiveType.UINT -> PandaUInt(value.toUInt())
        PandaPrimitiveType.LONG -> PandaLong(value.toLong())
        PandaPrimitiveType.ULONG -> PandaULong(value)
        PandaPrimitiveType.FLOAT -> PandaFloat(convert(value, ByteBuffer::getFloat))
        PandaPrimitiveType.DOUBLE -> PandaDouble(convert(value, ByteBuffer::getDouble))
    }

    private fun getConditionType(operator: String) = when (operator) {
        "LE" -> ::PandaLeExpr
        "LT" -> ::PandaLtExpr
        "GE" -> ::PandaGeExpr
        "GT" -> ::PandaGtExpr
        "EQ" -> ::PandaEqExpr
        "NE" -> ::PandaNeExpr
        else -> throw AssertionError("Unknown operator: $operator")
    }

    private fun skip() {}

    private fun pushUnary(
        inst: PandaInstIr,
        exprConstructor: (PandaType, PandaValue) -> PandaUnaryExpr,
    ) {
        val type = project.findType(inst.type)
        val value = local(inst.inputs[0])
        val expr = exprConstructor(type, value)
        pushAssign(result(inst), expr)
    }

    private fun pushBinary(
        inst: PandaInstIr,
        exprConstructor: (PandaType, PandaValue, PandaValue) -> PandaBinaryExpr,
    ) {
        val type = project.findType(inst.type)
        val (lhv, rhv) = inst.inputs.map { local(it) }
        val expr = exprConstructor(type, lhv, rhv)
        pushAssign(result(inst), expr)
    }

    override fun visitPandaConstantInstIr(inst: PandaConstantInstIr) {
        val constant = getConstant(inst.value, PandaPrimitives.find(inst.type))
        pushAssign(result(inst), constant)
    }

    override fun visitPandaSafePointInstIr(inst: PandaSafePointInstIr) {
        skip()
    }

    override fun visitPandaSaveStateInstIr(inst: PandaSaveStateInstIr) {
        skip()
    }

    override fun visitPandaNewObjectInstIr(inst: PandaNewObjectInstIr) {
        val type = project.findClass(inst.objectClass).type
        val newExpr = PandaNewExpr(type)
        pushAssign(result(inst), newExpr)
    }

    override fun visitPandaNewArrayInstIr(inst: PandaNewArrayInstIr) {
        val type = project.getElementType(inst.arrayType)
        val length = local(inst.inputs[1])
        val newArrayExpr = PandaNewArrayExpr(type, length)
        pushAssign(result(inst), newArrayExpr)
    }

    override fun visitPandaCallStaticInstIr(inst: PandaCallStaticInstIr) {
        val callee = project.findMethod(inst.method)
        val args = inst.inputs.take(callee.parameterTypes.size).map { local(it) }
        val callExpr = PandaStaticCallExpr(callee, args)
        pushAssign(result(inst), callExpr)
    }

    override fun visitPandaCallLaunchStaticInstIr(inst: PandaCallLaunchStaticInstIr) {
        val callee = project.findMethod(inst.method)
        val args = inst.inputs.take(callee.parameterTypes.size).map { local(it) }
        val callExpr = PandaStaticCallExpr(callee, args)
        pushAssign(result(inst), callExpr)
    }

    override fun visitPandaNullCheckInstIr(inst: PandaNullCheckInstIr) {
        pushAssign(result(inst), local(inst.inputs.first()))
    }

    override fun visitPandaZeroCheckInstIr(inst: PandaZeroCheckInstIr) {
        pushAssign(result(inst), local(inst.inputs.first()))
    }

    override fun visitPandaLoadStringInstIr(inst: PandaLoadStringInstIr) {
        pushAssign(result(inst), PandaString(inst.string, project.stringClass.type))
    }

    override fun visitPandaCallVirtualInstIr(inst: PandaCallVirtualInstIr) {
        val callee = project.findMethod(inst.method)
        val instance = local(inst.inputs[0])
        val args = inst.inputs.drop(1).take(callee.parameterTypes.size - 1).map { local(it) }
        val callExpr = PandaVirtualCallExpr(callee, instance, args)
        pushAssign(result(inst), callExpr)
    }

    override fun visitPandaCallLaunchVirtualInstIr(inst: PandaCallLaunchVirtualInstIr) {
        val callee = project.findMethod(inst.method)
        val instance = local(inst.inputs[0])
        val args = inst.inputs.drop(1).take(callee.parameterTypes.size - 1).map { local(it) }
        val callExpr = PandaVirtualCallExpr(callee, instance, args)
        pushAssign(result(inst), callExpr)
    }

    override fun visitPandaLoadAndInitClassInstIr(inst: PandaLoadAndInitClassInstIr) {
        skip()
    }

    override fun visitPandaLoadClassInstIr(inst: PandaLoadClassInstIr) {
        skip()
    }

    override fun visitPandaInitClassInstIr(inst: PandaInitClassInstIr) {
        skip()
    }

    override fun visitPandaReturnVoidInstIr(inst: PandaReturnVoidInstIr) {
        pushReturn(null)
    }

    override fun visitPandaReturnInstIr(inst: PandaReturnInstIr) {
        pushReturn(local(inst.inputs[0]))
    }

    override fun visitPandaParameterInstIr(inst: PandaParameterInstIr) {
        pushParameter(result(inst), inst.index)
    }

    override fun visitPandaLoadStaticInstIr(inst: PandaLoadStaticInstIr) {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        pushAssign(result(inst), PandaFieldRef(null, field))
    }

    override fun visitPandaLoadObjectInstIr(inst: PandaLoadObjectInstIr) {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        pushAssign(result(inst), PandaFieldRef(local(inst.inputs[0]), field))
    }

    override fun visitPandaStoreStaticInstIr(inst: PandaStoreStaticInstIr) {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        val fieldRef = PandaFieldRef(null, field)
        val value = local(inst.inputs[1])
        pushAssign(fieldRef, value)
    }

    override fun visitPandaStoreObjectInstIr(inst: PandaStoreObjectInstIr) {
        val (instance, value) = inst.inputs.map { local(it) }
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        val fieldRef = PandaFieldRef(instance, field)
        pushAssign(fieldRef, value)
    }

    override fun visitPandaLoadArrayInstIr(inst: PandaLoadArrayInstIr) {
        val (array, index) = inst.inputs.map { local(it) }
        val arrayType = array.type
        val type = if (arrayType is PandaArrayType) arrayType.elementType else project.objectClass.type
        val arrayAccess = PandaArrayAccess(array, index, type)
        pushAssign(result(inst), arrayAccess)
    }

    override fun visitPandaStoreArrayInstIr(inst: PandaStoreArrayInstIr) {
        val (array, index, value) = inst.inputs.map { local(it) }
        val arrayType = array.type
        val type = if (arrayType is PandaArrayType) arrayType.elementType else project.objectClass.type
        val arrayAccess = PandaArrayAccess(array, index, type)
        pushAssign(arrayAccess, value)
    }

    override fun visitPandaCastInstIr(inst: PandaCastInstIr) {
        val type = project.findType(inst.type)
        val value = local(inst.inputs[0])
        val castExpr = PandaCastExpr(type, value)
        pushAssign(result(inst), castExpr)
    }

    override fun visitPandaIsInstanceInstIr(inst: PandaIsInstanceInstIr) {
        val type = project.findType(inst.type)
        val value = local(inst.inputs[0])
        val candidateType = project.findClassOrInterface(inst.candidateType).type
        val isInstanceExpr = PandaIsInstanceExpr(type, value, candidateType)
        pushAssign(result(inst), isInstanceExpr)
    }

    override fun visitPandaCheckCastInstIr(inst: PandaCheckCastInstIr) {
        val type = project.findClassOrInterface(inst.candidateType).type
        val value = local(inst.inputs[0])
        val castExpr = PandaCastExpr(type, value)
        pushAssign(result(inst), castExpr)
    }

    override fun visitPandaIfImmInstIr(inst: PandaIfImmInstIr) {
        val type = project.findType(inst.type)
        val lhv = local(inst.inputs[0])
        val rhv = getConstant(inst.immediate, PandaPrimitives.find(inst.operandsType))
        val conditionExpr = getConditionType(inst.operator).invoke(type, lhv, rhv)
        val (trueBranch, falseBranch) = blocks
            .single { it.id == location.block }
            .successors
            .map { IrInstLocation(it, 0) }
        pushIf(conditionExpr, trueBranch, falseBranch)
    }

    override fun visitPandaCompareInstIr(inst: PandaCompareInstIr) {
        val (lhv, rhv) = inst.inputs.map { local(it) }
        val type = project.findType(inst.type)
        val conditionExpr = getConditionType(inst.operator).invoke(type, lhv, rhv)
        pushAssign(result(inst), conditionExpr)
    }

    override fun visitPandaPhiInstIr(inst: PandaPhiInstIr) {
        if (inst.users.isNotEmpty()) {
            val inputs = inst.inputs.map { local(it) }
            val blocks = inst.inputBlocks
            pushPhi(result(inst), inputs, blocks)
        }
    }

    override fun visitPandaAddInstIr(inst: PandaAddInstIr) {
        pushBinary(inst, ::PandaAddExpr)
    }

    override fun visitPandaSubInstIr(inst: PandaSubInstIr) {
        pushBinary(inst, ::PandaSubExpr)
    }

    override fun visitPandaMulInstIr(inst: PandaMulInstIr) {
        pushBinary(inst, ::PandaMulExpr)
    }

    override fun visitPandaDivInstIr(inst: PandaDivInstIr) {
        pushBinary(inst, ::PandaDivExpr)
    }

    override fun visitPandaModInstIr(inst: PandaModInstIr) {
        pushBinary(inst, ::PandaModExpr)
    }

    override fun visitPandaAndInstIr(inst: PandaAndInstIr) {
        pushBinary(inst, ::PandaAndExpr)
    }

    override fun visitPandaOrInstIr(inst: PandaOrInstIr) {
        pushBinary(inst, ::PandaOrExpr)
    }

    override fun visitPandaXorInstIr(inst: PandaXorInstIr) {
        pushBinary(inst, ::PandaXorExpr)
    }

    override fun visitPandaShlInstIr(inst: PandaShlInstIr) {
        pushBinary(inst, ::PandaShlExpr)
    }

    override fun visitPandaShrInstIr(inst: PandaShrInstIr) {
        pushBinary(inst, ::PandaShrExpr)
    }

    override fun visitPandaAShlInstIr(inst: PandaAShlInstIr) {
        pushBinary(inst, ::PandaAshlExpr)
    }

    override fun visitPandaAShrInstIr(inst: PandaAShrInstIr) {
        pushBinary(inst, ::PandaAshrExpr)
    }

    override fun visitPandaCmpInstIr(inst: PandaCmpInstIr) {
        pushBinary(inst, ::PandaCmpExpr)
    }

    override fun visitPandaThrowInstIr(inst: PandaThrowInstIr) {
        val error = local(inst.inputs[0])
        val catchers = inst.catchers
        pushThrow(error, catchers)
    }

    override fun visitPandaNegativeCheckInstIr(inst: PandaNegativeCheckInstIr) {
        skip()
    }

    override fun visitPandaSaveStateDeoptimizeInstIr(inst: PandaSaveStateDeoptimizeInstIr) {
        skip()
    }

    override fun visitPandaNegInstIr(inst: PandaNegInstIr) {
        pushUnary(inst, ::PandaNegExpr)
    }

    override fun visitPandaNotInstIr(inst: PandaNotInstIr) {
        pushUnary(inst, ::PandaNotExpr)
    }

    override fun visitPandaLenArrayInstIr(inst: PandaLenArrayInstIr) {
        pushUnary(inst, ::PandaLenArrayExpr)
    }

    override fun visitPandaBoundsCheckInstIr(inst: PandaBoundsCheckInstIr) {
        skip()
    }

    override fun visitPandaNullPtrInstIr(inst: PandaNullPtrInstIr) {
        val nullPtr = PandaNullPtr(project.findType("std.core.Object"))
        pushAssign(result(inst), nullPtr)
    }

    override fun visitPandaLoadUndefinedInstIr(inst: PandaLoadUndefinedInstIr) {
        val undefined = PandaUndefined(project.findType("std.core.UndefinedType"))
        pushAssign(result(inst), undefined)
    }

    override fun visitPandaRefTypeCheckInstIr(inst: PandaRefTypeCheckInstIr) {
        skip()
    }

    override fun visitPandaTryInstIr(inst: PandaTryInstIr) {
        skip()
    }

    override fun visitPandaCatchPhiInstIr(inst: PandaCatchPhiInstIr) {
        val inputs = inst.inputs.map { local(it) }
        val throwers = inst.throwers
        pushCatchPhi(result(inst), inputs, throwers)
    }

    override fun visitPandaIntrinsicInstIr(inst: PandaIntrinsicInstIr) {
        val method = project.resolveIntrinsic(inst.intrinsicId)
        val args = inst.inputs.dropLast(1).map { local(it) }
        val callExpr = if (method != null) {
            PandaStaticCallExpr(method, args)
        } else {
            PandaIntrinsicCallExpr(inst.intrinsicId, result(inst).type, args)
        }
        pushAssign(result(inst), callExpr)
    }

    override fun visitPandaLoadRuntimeClassInstIr(inst: PandaLoadRuntimeClassInstIr) {
        skip()
    }

    override fun visitPandaLoadTypeInstIr(inst: PandaLoadTypeInstIr) {
        val value = project.findType(inst.loadedType)
        val type = project.typeClass.type
        val typeConstant = PandaTypeConstant(value, type)
        pushAssign(result(inst), typeConstant)
    }
}
