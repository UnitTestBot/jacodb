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

package org.jacodb.panda.staticvm.ir

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface PandaInstIr {
    val id: String
    val inputs: List<String>
    val users: List<String>
    val opcode: String
    val type: String
    val catchers: List<Int>
    val visit: String

    fun <T> accept(visitor: PandaInstIrVisitor<T>): T
}

@Serializable
sealed interface PandaComparisonInstIr : PandaInstIr {
    val operator: String
    val operandsType: String
}

@Serializable
sealed interface PandaWithPropertyInstIr : PandaInstIr {
    val enclosingClass: String
    val field: String
}

@Serializable
sealed interface PandaTerminatingInstIr : PandaInstIr {
}

@Serializable
@SerialName("Constant")
data class PandaConstantInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val value: ULong,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaConstantInstIr(this)
}

@Serializable
@SerialName("SafePoint")
data class PandaSafePointInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaSafePointInstIr(this)
}

@Serializable
@SerialName("SaveState")
data class PandaSaveStateInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaSaveStateInstIr(this)
}

@Serializable
@SerialName("NewObject")
data class PandaNewObjectInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val objectClass: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNewObjectInstIr(this)
}

@Serializable
@SerialName("NewArray")
data class PandaNewArrayInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val arrayType: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNewArrayInstIr(this)
}

@Serializable
@SerialName("CallStatic")
data class PandaCallStaticInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val method: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCallStaticInstIr(this)
}

@Serializable
@SerialName("NullCheck")
data class PandaNullCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNullCheckInstIr(this)
}

@Serializable
@SerialName("ZeroCheck")
data class PandaZeroCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaZeroCheckInstIr(this)
}

@Serializable
@SerialName("LoadString")
data class PandaLoadStringInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val string: String,
    val string_offset: Long,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadStringInstIr(this)
}

@Serializable
@SerialName("LoadType")
data class PandaLoadTypeInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val loadedType: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadTypeInstIr(this)
}

@Serializable
@SerialName("LoadRuntimeClass")
data class PandaLoadRuntimeClassInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val loadedClass: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadRuntimeClassInstIr(this)
}

@Serializable
@SerialName("CallVirtual")
data class PandaCallVirtualInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val method: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCallVirtualInstIr(this)
}

@Serializable
@SerialName("CallLaunchVirtual")
data class PandaCallLaunchVirtualInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val method: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCallLaunchVirtualInstIr(this)
}

@Serializable
@SerialName("CallLaunchStatic")
data class PandaCallLaunchStaticInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val method: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCallLaunchStaticInstIr(this)
}

@Serializable
@SerialName("LoadAndInitClass")
data class PandaLoadAndInitClassInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val loadedClass: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadAndInitClassInstIr(this)
}

@Serializable
@SerialName("LoadClass")
data class PandaLoadClassInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadClassInstIr(this)
}

@Serializable
@SerialName("InitClass")
data class PandaInitClassInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaInitClassInstIr(this)
}

@Serializable
@SerialName("ReturnVoid")
data class PandaReturnVoidInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaTerminatingInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaReturnVoidInstIr(this)
}

@Serializable
@SerialName("Return")
data class PandaReturnInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaTerminatingInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaReturnInstIr(this)
}

@Serializable
@SerialName("Parameter")
data class PandaParameterInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val index: Int,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaParameterInstIr(this)
}

@Serializable
@SerialName("LoadStatic")
data class PandaLoadStaticInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    override val enclosingClass: String,
    override val field: String,
) : PandaWithPropertyInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadStaticInstIr(this)
}

@Serializable
@SerialName("LoadObject")
data class PandaLoadObjectInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    override val enclosingClass: String,
    override val field: String,
) : PandaWithPropertyInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadObjectInstIr(this)
}

@Serializable
@SerialName("StoreStatic")
data class PandaStoreStaticInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    override val enclosingClass: String,
    override val field: String,
) : PandaWithPropertyInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaStoreStaticInstIr(this)
}

@Serializable
@SerialName("StoreObject")
data class PandaStoreObjectInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    override val enclosingClass: String,
    override val field: String,
) : PandaWithPropertyInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaStoreObjectInstIr(this)
}

@Serializable
@SerialName("LoadArray")
data class PandaLoadArrayInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadArrayInstIr(this)
}

@Serializable
@SerialName("StoreArray")
data class PandaStoreArrayInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaStoreArrayInstIr(this)
}

@Serializable
@SerialName("IsInstance")
data class PandaIsInstanceInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val candidateType: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaIsInstanceInstIr(this)
}

@Serializable
@SerialName("CheckCast")
data class PandaCheckCastInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val candidateType: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCheckCastInstIr(this)
}

@Serializable
@SerialName("Cast")
data class PandaCastInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val candidateType: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCastInstIr(this)
}

@Serializable
@SerialName("IfImm")
data class PandaIfImmInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    override val operator: String,
    override val operandsType: String,
    val immediate: ULong,
) : PandaComparisonInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaIfImmInstIr(this)
}

@Serializable
@SerialName("Compare")
data class PandaCompareInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    override val operator: String,
    override val operandsType: String,
) : PandaComparisonInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCompareInstIr(this)
}

@Serializable
@SerialName("Phi")
data class PandaPhiInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val inputBlocks: List<Int>,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaPhiInstIr(this)
}

@Serializable
@SerialName("Add")
data class PandaAddInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaAddInstIr(this)
}

@Serializable
@SerialName("Sub")
data class PandaSubInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaSubInstIr(this)
}

@Serializable
@SerialName("Mul")
data class PandaMulInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaMulInstIr(this)
}

@Serializable
@SerialName("Div")
data class PandaDivInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaDivInstIr(this)
}

@Serializable
@SerialName("Mod")
data class PandaModInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaModInstIr(this)
}

@Serializable
@SerialName("And")
data class PandaAndInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaAndInstIr(this)
}

@Serializable
@SerialName("Or")
data class PandaOrInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaOrInstIr(this)
}

@Serializable
@SerialName("Xor")
data class PandaXorInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaXorInstIr(this)
}

@Serializable
@SerialName("Shl")
data class PandaShlInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaShlInstIr(this)
}

@Serializable
@SerialName("Shr")
data class PandaShrInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaShrInstIr(this)
}

@Serializable
@SerialName("AShl")
data class PandaAShlInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaAShlInstIr(this)
}

@Serializable
@SerialName("AShr")
data class PandaAShrInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaAShrInstIr(this)
}

@Serializable
@SerialName("Cmp")
data class PandaCmpInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    override val operator: String,
    override val operandsType: String,
) : PandaComparisonInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCmpInstIr(this)
}

@Serializable
@SerialName("Throw")
data class PandaThrowInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val method: String,
) : PandaTerminatingInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaThrowInstIr(this)
}

@Serializable
@SerialName("NegativeCheck")
data class PandaNegativeCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNegativeCheckInstIr(this)
}

@Serializable
@SerialName("SaveStateDeoptimize")
data class PandaSaveStateDeoptimizeInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaSaveStateDeoptimizeInstIr(this)
}

@Serializable
@SerialName("Neg")
data class PandaNegInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNegInstIr(this)
}

@Serializable
@SerialName("Not")
data class PandaNotInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNotInstIr(this)
}

@Serializable
@SerialName("LenArray")
data class PandaLenArrayInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLenArrayInstIr(this)
}

@Serializable
@SerialName("BoundsCheck")
data class PandaBoundsCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaBoundsCheckInstIr(this)
}

@Serializable
@SerialName("NullPtr")
data class PandaNullPtrInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNullPtrInstIr(this)
}

@Serializable
@SerialName("LoadUndefined")
data class PandaLoadUndefinedInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadUndefinedInstIr(this)
}

@Serializable
@SerialName("RefTypeCheck")
data class PandaRefTypeCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaRefTypeCheckInstIr(this)
}

@Serializable
@SerialName("Try")
data class PandaTryInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val end_bb: Int,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaTryInstIr(this)
}

@Serializable
@SerialName("CatchPhi")
data class PandaCatchPhiInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val throwers: List<String> = emptyList(),
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCatchPhiInstIr(this)
}

@Serializable
@SerialName("Intrinsic")
data class PandaIntrinsicInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val catchers: List<Int> = emptyList(),
    override val visit: String,
    val intrinsicId: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaIntrinsicInstIr(this)
}
