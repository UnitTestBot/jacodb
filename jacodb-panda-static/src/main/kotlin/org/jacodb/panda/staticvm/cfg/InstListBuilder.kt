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

package org.jacodb.panda.staticvm.cfg

import org.jacodb.panda.staticvm.classpath.PandaArrayType
import org.jacodb.panda.staticvm.classpath.PandaMethod
import org.jacodb.panda.staticvm.classpath.PandaPrimitivePandaType
import org.jacodb.panda.staticvm.classpath.PandaPrimitives
import org.jacodb.panda.staticvm.classpath.PandaType
import org.jacodb.panda.staticvm.ir.PandaAShlInstIr
import org.jacodb.panda.staticvm.ir.PandaAShrInstIr
import org.jacodb.panda.staticvm.ir.PandaAddInstIr
import org.jacodb.panda.staticvm.ir.PandaAndInstIr
import org.jacodb.panda.staticvm.ir.PandaBasicBlockIr
import org.jacodb.panda.staticvm.ir.PandaBoundsCheckInstIr
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
import org.jacodb.panda.staticvm.ir.PandaIsInstanceInstIr
import org.jacodb.panda.staticvm.ir.PandaLenArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadAndInitClassInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadArrayInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadClassInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadObjectInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadStaticInstIr
import org.jacodb.panda.staticvm.ir.PandaLoadStringInstIr
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
import org.jacodb.panda.staticvm.ir.PandaThrowInstIr
import org.jacodb.panda.staticvm.ir.PandaTryInstIr
import org.jacodb.panda.staticvm.ir.PandaXorInstIr
import org.jacodb.panda.staticvm.ir.PandaZeroCheckInstIr
import org.jacodb.panda.staticvm.utils.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed interface InstBuilder {
    val build: InstListBuilder.() -> PandaInst
}

class DefaultInstBuilder<T : PandaInst>(private val inst: T) : InstBuilder {
    override val build: InstListBuilder.() -> PandaInst
        get() = { inst }
}

class BranchingInstBuilder<T : PandaBranchingInst>(override val build: InstListBuilder.() -> T) : InstBuilder

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
            is DependentVarNode -> node.bounds.map(varNodes::get).also {
                if (it.contains(null))
                    require(false)
            }.requireNoNulls().toSet()

            is LoadArrayNode -> setOf(requireNotNull(varNodes[node.array]))
        }
    }

    val sccs = graph.SCCs()
    check(sccs.inTopsortOrder() != null)

    graph.SCCs().runDP { vars, inputTypes ->
        vars.map { lvar ->
            when (lvar) {
                is LeafVarNode -> lvar.type
                is DependentVarNode -> requireNotNull(project.commonType(inputTypes.values.flatten())) {
                    "No common type for ${inputTypes.values}"
                }

                is LoadArrayNode -> {
                    val arrayTypes = inputTypes.values.toList().flatten<PandaType>()
                    require(arrayTypes.all { it is PandaArrayType })
                    val elementTypes = arrayTypes.filterIsInstance<PandaArrayType>()
                        .map(PandaArrayType::elementType)
                    requireNotNull(project.commonType(elementTypes))
                }
            }.also {
                if (lvar is ThisNode)
                    localVarsIndex[lvar.name] = PandaThis(lvar.name, it)
                else
                    localVarsIndex[lvar.name] = PandaLocalVarImpl(lvar.name, it)
            }
        }
    }

    return localVarsIndex
}

data class IrInstLocation(val block: Int, val index: Int)

open class InstListBuilder(
    val method: PandaMethod,
    val blocks: List<PandaBasicBlockIr>,
) {
    val project = method.enclosingClass.project

    val localVars = buildLocalVariables(method, blocks)

    val locationMap = hashMapOf<IrInstLocation, Int>()

    private val blockIdMap = blocks.mapIndexed { index, block -> block.id to index }.toMap()

    fun getBlock(id: Int) = blocks[requireNotNull(blockIdMap[id])]

    fun linearRef(location: IrInstLocation) = PandaInstRef(requireNotNull(locationMap[location]) {
        "Not found location $location (method=${method.signature})"
    })

    fun local(name: String) = requireNotNull(localVars[name]) {
        "Not found local var $name (method=${method.signature})"
    }

    fun result(inst: PandaInstIr) = local(inst.id)

    val instBuildersList = mutableListOf<InstBuilder>()

    private fun push(value: InstListBuilder.(PandaInstLocation) -> InstBuilder) {
        instBuildersList.add(value(PandaInstLocation(method, instBuildersList.size)))
    }

    internal fun pushAssign(lhv: PandaValue, rhv: PandaExpr) = push { location ->
        DefaultInstBuilder(PandaAssignInst(location, lhv, rhv))
    }

    internal fun pushParameter(lhv: PandaValue, index: Int) = push { location ->
        DefaultInstBuilder(PandaParameterInst(location, lhv, index))
    }

    internal fun pushReturn(value: PandaValue?) = push { location ->
        DefaultInstBuilder(PandaReturnInst(location, value))
    }

    internal fun pushIf(conditionExpr: PandaConditionExpr, trueBranch: IrInstLocation, falseBranch: IrInstLocation) =
        push { location ->
            BranchingInstBuilder { PandaIfInst(location, conditionExpr, linearRef(trueBranch), linearRef(falseBranch)) }
        }

    internal fun pushGoto(target: IrInstLocation) = push { location ->
        BranchingInstBuilder { PandaGotoInst(location, linearRef(target)) }
    }

    private fun pushDoNothing(target: IrInstLocation) = push { location ->
        DefaultInstBuilder(PandaDoNothingInst(location))
    }

    internal fun pushCatch(throwerIds: List<String>) = push { location ->
        val throwers = throwerIds.map(idMap::get).requireNoNulls().map(::PandaInstRef)
        DefaultInstBuilder(PandaCatchInst(location, throwers))
    }

    internal fun pushThrow(error: PandaValue, catchers: List<Int>) = push { location ->
        BranchingInstBuilder {
            PandaThrowInst(location, error, catchers.map { linearRef(IrInstLocation(it, 0)) })
        }
    }

    private val idMap = mutableMapOf<String, Int>()

    private val throwEdgeBuilders = mutableListOf<Pair<IrInstLocation, IrInstLocation>>()

    init {
        val visitor = InstListBuilderVisitor()
        blocks.sortedBy { it.predecessors.size }.forEach { block ->
            block.insts.forEachIndexed { instIndex, inst ->
                visitor.location = IrInstLocation(block.id, instIndex)
                locationMap[visitor.location] = instBuildersList.size
                idMap[inst.id] = instBuildersList.size
                inst.accept(visitor)(this)

                throwEdgeBuilders.addAll(inst.catchers.map { visitor.location to IrInstLocation(it, 0) })
            }

            if (block.isTryBegin || block.isTryEnd)
                pushGoto(IrInstLocation(block.successors.first(), 0))

            block.successors.singleOrNull()?.let {
                if (block.insts.lastOrNull() !is org.jacodb.panda.staticvm.ir.PandaTerminatingInstIr)
                    pushGoto(IrInstLocation(it, 0))
            }

            if (block.insts.isEmpty()) {
                locationMap[IrInstLocation(block.id, 0)] = instBuildersList.size
                pushDoNothing(IrInstLocation(block.id, 0))
            }
        }
    }

    val instList = instBuildersList.map { it.build(this) }

    val throwEdges = throwEdgeBuilders.map { (from, to) -> linearRef(from) to linearRef(to) }
}

class InstListBuilderVisitor() : PandaInstIrVisitor<InstListBuilder.() -> Unit> {
    lateinit var location: IrInstLocation

    private inline fun <reified T> convert(value: ULong, getter: ByteBuffer.() -> T) = ByteBuffer
        .allocate(16)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(value.toLong())
        .rewind()
        .let { it as ByteBuffer }
        .let(getter)

    private fun getConstant(value: ULong, type: PandaPrimitivePandaType) = when (type) {
        PandaPrimitivePandaType.VOID -> throw IllegalArgumentException("cannot create void constant")
        PandaPrimitivePandaType.BOOL -> PandaBoolean(value != 0UL)
        PandaPrimitivePandaType.BYTE -> PandaByte(value.toByte())
        PandaPrimitivePandaType.UBYTE -> TODO()
        PandaPrimitivePandaType.SHORT -> PandaShort(value.toShort())
        PandaPrimitivePandaType.USHORT -> TODO()
        PandaPrimitivePandaType.INT -> PandaInt(value.toInt())
        PandaPrimitivePandaType.UINT -> TODO()
        PandaPrimitivePandaType.LONG -> PandaLong(value.toLong())
        PandaPrimitivePandaType.ULONG -> TODO()
        PandaPrimitivePandaType.FLOAT -> PandaFloat(convert(value, ByteBuffer::getFloat))
        PandaPrimitivePandaType.DOUBLE -> PandaDouble(convert(value, ByteBuffer::getDouble))
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

    private val skip: InstListBuilder.() -> Unit = {}

    private fun pushUnary(
        inst: PandaInstIr,
        exprConstructor: (PandaType, PandaValue) -> PandaUnaryExpr,
    ): InstListBuilder.() -> Unit = {
        val value = local(inst.inputs.first())
        pushAssign(result(inst), exprConstructor(project.findType(inst.type), value))
    }

    private fun pushBinary(
        inst: PandaInstIr,
        exprConstructor: (PandaType, PandaValue, PandaValue) -> PandaBinaryExpr,
    ): InstListBuilder.() -> Unit = {
        val (lhv, rhv) = inst.inputs.map(this::local)
        pushAssign(result(inst), exprConstructor(project.findType(inst.type), lhv, rhv))
    }

    override fun visitPandaConstantInstIr(inst: PandaConstantInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), getConstant(inst.value, PandaPrimitives.find(inst.type)))
    }

    override fun visitPandaSafePointInstIr(inst: PandaSafePointInstIr) = skip

    override fun visitPandaSaveStateInstIr(inst: PandaSaveStateInstIr) = skip

    override fun visitPandaNewObjectInstIr(inst: PandaNewObjectInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), PandaNewExpr(project.findClass(inst.objectClass).type))
    }

    override fun visitPandaNewArrayInstIr(inst: PandaNewArrayInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), PandaNewArrayExpr(project.getElementType(inst.arrayType), local(inst.inputs[1])))
    }

    override fun visitPandaCallStaticInstIr(inst: PandaCallStaticInstIr): InstListBuilder.() -> Unit = {
        val callee = project.findMethod(inst.method)
        pushAssign(result(inst), PandaStaticCallExpr(
            callee,
            inst.inputs.take(callee.parameterTypes.size).map { local(it) }
        ))
    }

    override fun visitPandaNullCheckInstIr(inst: PandaNullCheckInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), local(inst.inputs.first()))
    }

    override fun visitPandaZeroCheckInstIr(inst: PandaZeroCheckInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), local(inst.inputs.first()))
    }

    override fun visitPandaLoadStringInstIr(inst: PandaLoadStringInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), PandaString(inst.string, project.stringClass.type))
    }

    override fun visitPandaCallVirtualInstIr(inst: PandaCallVirtualInstIr): InstListBuilder.() -> Unit = {
        val callee = project.findMethod(inst.method)
        val instance = local(inst.inputs.first())
        val args = inst.inputs.drop(1).take(callee.parameterTypes.size - 1).map(this::local)
        pushAssign(result(inst), PandaVirtualCallExpr(callee, instance, args))
    }

    override fun visitPandaLoadAndInitClassInstIr(inst: PandaLoadAndInitClassInstIr) = skip

    override fun visitPandaLoadClassInstIr(inst: PandaLoadClassInstIr) = skip

    override fun visitPandaInitClassInstIr(inst: PandaInitClassInstIr) = skip

    override fun visitPandaReturnVoidInstIr(inst: PandaReturnVoidInstIr): InstListBuilder.() -> Unit = {
        pushReturn(null)
    }

    override fun visitPandaReturnInstIr(inst: PandaReturnInstIr): InstListBuilder.() -> Unit = {
        pushReturn(local(inst.inputs.first()))
    }

    override fun visitPandaParameterInstIr(inst: PandaParameterInstIr): InstListBuilder.() -> Unit = {
        pushParameter(result(inst), inst.index)
    }

    override fun visitPandaLoadStaticInstIr(inst: PandaLoadStaticInstIr): InstListBuilder.() -> Unit = {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        pushAssign(result(inst), PandaFieldAccess(null, field))
    }

    override fun visitPandaLoadObjectInstIr(inst: PandaLoadObjectInstIr): InstListBuilder.() -> Unit = {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        pushAssign(result(inst), PandaFieldAccess(local(inst.inputs.first()), field))
    }

    override fun visitPandaStoreStaticInstIr(inst: PandaStoreStaticInstIr): InstListBuilder.() -> Unit = {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        pushAssign(PandaFieldAccess(null, field), local(inst.inputs[1]))
    }

    override fun visitPandaStoreObjectInstIr(inst: PandaStoreObjectInstIr): InstListBuilder.() -> Unit = {
        val enclosingClass = project.findClass(inst.enclosingClass)
        val field = enclosingClass.findField(inst.field)
        pushAssign(PandaFieldAccess(local(inst.inputs[0]), field), local(inst.inputs[1]))
    }

    override fun visitPandaLoadArrayInstIr(inst: PandaLoadArrayInstIr): InstListBuilder.() -> Unit = {
        val (array, index) = inst.inputs.map(this::local)
        val arrayType = array.type
        require(arrayType is PandaArrayType)
        pushAssign(result(inst), PandaArrayAccess(array, index, arrayType.elementType))
    }

    override fun visitPandaStoreArrayInstIr(inst: PandaStoreArrayInstIr): InstListBuilder.() -> Unit = {
        val (array, index, value) = inst.inputs.map(this::local)
        val arrayType = array.type
        require(arrayType is PandaArrayType)
        pushAssign(PandaArrayAccess(array, index, arrayType.elementType), value)
    }

    override fun visitPandaCastInstIr(inst: PandaCastInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), PandaCastExpr(project.findType(inst.type), local(inst.inputs.first())))
    }

    override fun visitPandaIsInstanceInstIr(inst: PandaIsInstanceInstIr): InstListBuilder.() -> Unit = {
        pushAssign(
            result(inst), PandaIsInstanceExpr(
                project.findType(inst.type),
                local(inst.inputs.first()),
                project.findClassOrInterface(inst.candidateType).type
            )
        )
    }

    override fun visitPandaCheckCastInstIr(inst: PandaCheckCastInstIr): InstListBuilder.() -> Unit = {
        pushAssign(
            result(inst), PandaCastExpr(
                project.findClassOrInterface(inst.candidateType).type,
                local(inst.inputs.first())
            )
        )
    }

    override fun visitPandaIfImmInstIr(inst: PandaIfImmInstIr): InstListBuilder.() -> Unit = {
        val conditionExpr = getConditionType(inst.operator).invoke(
            project.findType(inst.type),
            local(inst.inputs.first()),
            getConstant(inst.immediate, PandaPrimitives.find(inst.operandsType))
        )
        val (trueBranch, falseBranch) = blocks.single { it.id == location.block }.successors
            .map { IrInstLocation(it, 0) }
        pushIf(conditionExpr, trueBranch, falseBranch)
    }

    override fun visitPandaCompareInstIr(inst: PandaCompareInstIr): InstListBuilder.() -> Unit = {
        val conditionExpr = getConditionType(inst.operator).invoke(
            project.findType(inst.type),
            local(inst.inputs.component1()),
            local(inst.inputs.component2())
        )
        pushAssign(result(inst), conditionExpr)
    }

    override fun visitPandaPhiInstIr(inst: PandaPhiInstIr): InstListBuilder.() -> Unit = {
        if (inst.users.isNotEmpty())
            pushAssign(result(inst), PandaPhiExpr(result(inst).type, inst.inputs.map(this::local)))
    }

    override fun visitPandaAddInstIr(inst: PandaAddInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaAddExpr)

    override fun visitPandaSubInstIr(inst: PandaSubInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaSubExpr)

    override fun visitPandaMulInstIr(inst: PandaMulInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaMulExpr)

    override fun visitPandaDivInstIr(inst: PandaDivInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaDivExpr)

    override fun visitPandaModInstIr(inst: PandaModInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaModExpr)

    override fun visitPandaAndInstIr(inst: PandaAndInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaAndExpr)

    override fun visitPandaOrInstIr(inst: PandaOrInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaOrExpr)

    override fun visitPandaXorInstIr(inst: PandaXorInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaXorExpr)

    override fun visitPandaShlInstIr(inst: PandaShlInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaShlExpr)

    override fun visitPandaShrInstIr(inst: PandaShrInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaShrExpr)

    override fun visitPandaAShlInstIr(inst: PandaAShlInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaAshlExpr)

    override fun visitPandaAShrInstIr(inst: PandaAShrInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaAshrExpr)

    override fun visitPandaCmpInstIr(inst: PandaCmpInstIr): InstListBuilder.() -> Unit =
        pushBinary(inst, ::PandaCmpExpr)

    override fun visitPandaThrowInstIr(inst: PandaThrowInstIr): InstListBuilder.() -> Unit = {
        pushThrow(local(inst.inputs.first()), inst.catchers)
    }

    override fun visitPandaNegativeCheckInstIr(inst: PandaNegativeCheckInstIr): InstListBuilder.() -> Unit = skip

    override fun visitPandaSaveStateDeoptimizeInstIr(inst: PandaSaveStateDeoptimizeInstIr): InstListBuilder.() -> Unit =
        skip

    override fun visitPandaNegInstIr(inst: PandaNegInstIr): InstListBuilder.() -> Unit =
        pushUnary(inst, ::PandaNegExpr)

    override fun visitPandaNotInstIr(inst: PandaNotInstIr): InstListBuilder.() -> Unit =
        pushUnary(inst, ::PandaNotExpr)

    override fun visitPandaLenArrayInstIr(inst: PandaLenArrayInstIr): InstListBuilder.() -> Unit =
        pushUnary(inst, ::PandaLenArrayExpr)

    override fun visitPandaBoundsCheckInstIr(inst: PandaBoundsCheckInstIr): InstListBuilder.() -> Unit = skip

    override fun visitPandaNullPtrInstIr(inst: PandaNullPtrInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), PandaNullPtr(project.findType("std.core.Object")))
    }

    override fun visitPandaLoadUndefinedInstIr(inst: PandaLoadUndefinedInstIr): InstListBuilder.() -> Unit = {
        pushAssign(result(inst), PandaUndefined(project.findType("std.core.UndefinedType")))
    }

    override fun visitPandaRefTypeCheckInstIr(inst: PandaRefTypeCheckInstIr): InstListBuilder.() -> Unit = skip

    override fun visitPandaTryInstIr(inst: PandaTryInstIr): InstListBuilder.() -> Unit = skip

    override fun visitPandaCatchPhiInstIr(inst: PandaCatchPhiInstIr): InstListBuilder.() -> Unit = {
        /*check(getBlock(location.block).isCatchBegin)
        if (location.index == 0) {
            pushCatch(inst.throwers)
        }*/
        if (inst.users.isNotEmpty()) {
            pushAssign(result(inst), PandaPhiExpr(result(inst).type, inst.inputs.map(this::local)))
        }
    }

}
