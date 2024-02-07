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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.reflect.KFunction3

class PandaInstListBuilder(
    val method: PandaMethod
) {
    val project = method.project
    val methods = project.classes.flatMap { it.declaredMethods }.associateBy { it.name }
    
    val methodBody = method.body
        ?: throw IllegalArgumentException("Cannot build InstListt for method without body")

    val blocks = methodBody.nodes.associateBy { it.id }

    val beginOfBlock = mutableMapOf<Int, Int>()
    var instsPlaced = 0
    val irInstructions = methodBody.topsort().flatMap {
        beginOfBlock[it.id] = instsPlaced
        instsPlaced += it.insts.size
        it.insts
    }

    val locals = hashMapOf<String, PandaLocalVar>()

    private fun getLocal(name: String) = locals[name]
        ?: throw AssertionError("Local variable $name not found")

    private fun getType(name: String) = project.findTypeOrNull(name)
        ?: throw AssertionError("Type $name not found")

    private fun getMethod(name: String) = methods[name]
        ?: throw AssertionError("Method $name not found")

    private fun getField(enclosingClass: String, name: String): PandaField = project.classes
        .find { it.name == enclosingClass }?.fields?.find { it.name == name }
        ?: throw AssertionError("Field $enclosingClass.$name not found")

    val instList = mutableListOf<PandaInst>()

    val index: Int
        get() = instList.size

    private val blockStartingAt = beginOfBlock.asSequence().associate { (block, index) -> index to blocks[block] }

    private val PandaBasicBlockInfo.endsWithExplicitJump
        get() = insts.lastOrNull()?.let {
            it is PandaIfImmInstInfo || it is PandaReturnInstInfo || it is PandaReturnVoidInstInfo
        } ?: false

    private fun buildImpl() {
        if (irInstructions.isEmpty())
            return

        var currentBlock = blockStartingAt[0]
            ?: throw AssertionError("Some basic block should be first")

        val instListIndexOfJump = mutableListOf<Int>()

        irInstructions.forEachIndexed { index, it ->
            if (blockStartingAt.contains(index)) {
                currentBlock = blockStartingAt[index]
                    ?: throw AssertionError("This error should not happen")
            }
            instListIndexOfJump.add(instList.size)

            when(it) {
                is PandaAddInstInfo -> buildBinary(it, ::PandaAddExpr)
                is PandaCallStaticInstInfo -> buildCallStatic(it)
                is PandaCallVirtualInstInfo -> buildCallVirtual(it)
                is PandaCastInstInfo -> buildCast(it)
                is PandaConstantInstInfo -> buildConstant(it)
                is PandaLoadAndInitClassInstInfo -> buildLoadAndInitClassInst(it)
                is PandaLoadObjectInstInfo -> buildLoadObjectInst(it)
                is PandaLoadStaticInstInfo -> buildLoadStaticInst(it)
                is PandaNewObjectInstInfo -> buildNewObjectInst(it)
                is PandaNullCheckInstInfo -> buildNullCheckInst(it)
                is PandaParameterInstInfo -> buildParameterInst(it)
                is PandaReturnInstInfo -> buildReturnInst(it)
                is PandaReturnVoidInstInfo -> buildReturnVoidInst()
                is PandaSafePointInstInfo -> {}
                is PandaSaveStateInstInfo -> {}
                is PandaStoreObjectInstInfo -> buildStoreObjectInst(it)
                is PandaStoreStaticInstInfo -> buildStoreStaticInst(it)
                is PandaIfImmInstInfo -> buildIfImmInst(it,
                    trueBranch = beginOfBlock[currentBlock.successors.component1()]
                        ?: throw AssertionError(
                            "Begin point of block ${currentBlock.successors.component1()} is not set"),
                    falseBranch = beginOfBlock[currentBlock.successors.component2()]
                        ?: throw AssertionError(
                            "Begin point of block ${currentBlock.successors.component2()} is not set")
                )
                is PandaCompareInstInfo -> buildCompareInst(it)
                is PandaPhiInstInfo -> buildPhiInst(it)
            }

            if (blockStartingAt.contains(index + 1)) {
                if (!currentBlock.endsWithExplicitJump)
                    buildGoto(beginOfBlock[currentBlock.successors.single()]
                        ?: throw AssertionError("Begin of block ${currentBlock.successors.single()} is not set"))
            }
        }

        fun correctJump(jump: PandaInstRef) = PandaInstRef(instListIndexOfJump[jump.index])

        instList.replaceAll {
            when (it) {
                is PandaIfInst -> PandaIfInst(
                    it.location, it.condition, correctJump(it.trueBranch), correctJump(it.falseBranch))
                is PandaGotoInst -> PandaGotoInst(it.location, correctJump(it.target))
                else -> it
            }
        }
    }

    fun build() = buildImpl().let { PandaInstList(instList) }

    private fun newLocal(name: String, type: PandaType) = PandaLocalVar(name, type).also { locals[it.name] = it }

    private fun result(inst: PandaInstInfo) = newLocal(inst.id, getType(inst.type))

    private fun addAssignInst(result: PandaValue, expr: PandaExpr) {
        instList.add(PandaAssignInst(PandaInstLocation(method, index), result, expr))
    }

    private fun addReturnInst(returnValue: PandaValue?) {
        instList.add(PandaReturnInst(PandaInstLocation(method, index), returnValue))
    }

    private fun addIfInst(conditionExpr: PandaConditionExpr, trueBranch: Int, falseBranch: Int) {
        instList.add(PandaIfInst(
            PandaInstLocation(method, index),
            conditionExpr,
            PandaInstRef(trueBranch),
            PandaInstRef(falseBranch)
        ))
    }

    private fun buildBinary(inst: PandaInstInfo, binaryExprConstructor: KFunction3<PandaType, PandaValue, PandaValue, PandaBinaryExpr>) {
        val (lhs, rhs) = inst.inputs.map(this::getLocal)
        val result = result(inst)
        val expr = binaryExprConstructor.invoke(result.type, lhs, rhs)
        addAssignInst(result, expr)
    }

    private fun buildCallStatic(inst: PandaCallStaticInstInfo) {
        val method = getMethod(inst.method)
        val result = newLocal(inst.id, getType(getMethod(inst.method).returnType.typeName))
        val args = inst.inputs.take(method.args.size).map(this::getLocal)
        addAssignInst(result, PandaStaticCallExpr(method, args))
    }

    private fun buildCallVirtual(inst: PandaCallVirtualInstInfo) {
        val method = getMethod(inst.method)
        val result = newLocal(inst.id, getType(getMethod(inst.method).returnType.typeName))
        val instance = getLocal(inst.inputs.first())
        val args = inst.inputs.drop(1).take(method.args.size - 1).map(this::getLocal)
        addAssignInst(result, PandaVirtualCallExpr(method, instance, args))
    }

    private fun buildCast(inst: PandaCastInstInfo) {
        val result = result(inst)
        addAssignInst(result, PandaCastExpr(result.type, getLocal(inst.inputs.single())))
    }

    private inline fun <reified T> convert(value: ULong, getter: ByteBuffer.() -> T) = ByteBuffer
        .allocate(8)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putLong(value.toLong())
        .let(getter)

    private fun getConstant(value: ULong, type: PandaTypeName) = when(type) {
        PandaVMType.BYTE -> PandaByte(value.toByte(), project.char)
        PandaVMType.VOID -> TODO()
        PandaVMType.BOOL -> PandaBoolean(value != 0UL, project.boolean)
        PandaVMType.UBYTE -> TODO()
        PandaVMType.SHORT -> PandaShort(value.toShort(), project.short)
        PandaVMType.USHORT -> TODO()
        PandaVMType.B32 -> TODO()
        PandaVMType.INT -> PandaInt(value.toInt(), project.int)
        PandaVMType.UINT -> TODO()
        PandaVMType.LONG -> PandaLong(value.toLong(), project.long)
        PandaVMType.ULONG -> TODO()
        PandaVMType.B64 -> TODO()
        PandaVMType.FLOAT -> PandaFloat(convert(value, ByteBuffer::getFloat), project.float)
        PandaVMType.DOUBLE -> PandaDouble(convert(value, ByteBuffer::getDouble), project.double)
        PandaVMType.REF -> TODO()
        PandaVMType.ANY -> TODO()
        is PandaArrayName -> TODO()
        is PandaClassName -> TODO()
    }

    private fun buildConstant(inst: PandaConstantInstInfo) {
        val result = result(inst)
        addAssignInst(result, getConstant(inst.value, inst.type.pandaTypeName))
    }

    private fun buildLoadAndInitClassInst(inst: PandaLoadAndInitClassInstInfo) {
        val result = result(inst)
        val method = getMethod(inst.method)
        val args = inst.inputs.take(method.args.size).map(this::getLocal)
        addAssignInst(result, PandaStaticCallExpr(method, args))
    }

    private fun buildLoadObjectInst(inst: PandaLoadObjectInstInfo) {
        val field = getField(inst.enclosingClass, inst.field)
        val result = newLocal(inst.id, getType(field.type.typeName))
        val instance = getLocal(inst.inputs.first())
        addAssignInst(result, PandaFieldRef(instance, field, getType(field.type.typeName)))
    }

    private fun buildLoadStaticInst(inst: PandaLoadStaticInstInfo) {
        val field = getField(inst.enclosingClass, inst.field)
        val result = newLocal(inst.id, getType(field.type.typeName))
        addAssignInst(result, PandaFieldRef(instance = null, field, getType(field.type.typeName)))
    }

    private fun buildNullCheckInst(inst: PandaNullCheckInstInfo) {
        val obj = getLocal(inst.inputs.first())
        val result = newLocal(inst.id, getType(obj.type.typeName))
        addAssignInst(result, obj)
    }

    private fun buildStoreObjectInst(inst: PandaStoreObjectInstInfo) {
        val field = getField(inst.enclosingClass, inst.field)
        val instance = getLocal(inst.inputs.component1())
        val value = getLocal(inst.inputs.component2())
        addAssignInst(PandaFieldRef(instance, field, getType(field.type.typeName)), value)
    }

    private fun buildStoreStaticInst(inst: PandaStoreStaticInstInfo) {
        val field = getField(inst.enclosingClass, inst.field)
        val value = getLocal(inst.inputs.component1())
        addAssignInst(PandaFieldRef(instance = null, field, getType(field.type.typeName)), value)
    }

    private fun buildNewObjectInst(inst: PandaNewObjectInstInfo) {
        val objectClass = getType(inst.objectClass)
        val result = newLocal(inst.id, objectClass)
        addAssignInst(result, PandaNewExpr(objectClass))
    }

    private fun buildParameterInst(inst: PandaParameterInstInfo) {
        val argumentType = getType(method.args[inst.index].typeName)
        val result = newLocal(inst.id, argumentType)
        addAssignInst(result, PandaArgument(inst.index, "a${inst.index}", argumentType))
    }

    private fun buildReturnInst(inst: PandaReturnInstInfo) {
        addReturnInst(getLocal(inst.inputs.first()))
    }

    private fun buildReturnVoidInst() {
        addReturnInst(null)
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

    private fun buildCompareInst(inst: PandaCompareInstInfo) {
        val conditionExpr = getConditionType(inst.operator).invoke(
            getType(inst.type),
            getLocal(inst.inputs.component1()),
            getLocal(inst.inputs.component2())
        )
        addAssignInst(result(inst), conditionExpr)
    }

    private fun buildIfImmInst(inst: PandaIfImmInstInfo, trueBranch: Int, falseBranch: Int) {
        val conditionExpr = getConditionType(inst.operator).invoke(
            getType(inst.type),
            getLocal(inst.inputs.first()),
            getConstant(inst.immediate, inst.operandsType.pandaTypeName)
        )
        addIfInst(conditionExpr, trueBranch, falseBranch)
    }

    private fun buildGoto(jump: Int) {
        instList.add(PandaGotoInst(
            PandaInstLocation(method, index),
            PandaInstRef(jump)
        ))
    }

    private fun buildPhiInst(inst: PandaPhiInstInfo) {
        val inputs = inst.inputs.map(this::getLocal)
        val result = newLocal(inst.id, project.typeHierarchy.typeInBounds(
            lowerBounds = inputs.map { it.type.arkName },
            upperBounds = emptyList()
        ))
        addAssignInst(result, PandaPhiExpr(result.type, inputs))
    }
}
