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
    val value: ULong,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaConstantInstInfo(this)
}

@Serializable
@SerialName("SafePoint")
data class PandaSafePointInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaSafePointInstInfo(this)
}

@Serializable
@SerialName("SaveState")
data class PandaSaveStateInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaSaveStateInstInfo(this)
}

@Serializable
@SerialName("NewObject")
data class PandaNewObjectInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val objectClass: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNewObjectInstInfo(this)
}

@Serializable
@SerialName("NewArray")
data class PandaNewArrayInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val arrayType: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNewArrayInstInfo(this)
}

@Serializable
@SerialName("CallStatic")
data class PandaCallStaticInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val method: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCallStaticInstInfo(this)
}

@Serializable
@SerialName("NullCheck")
data class PandaNullCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNullCheckInstInfo(this)
}

@Serializable
@SerialName("ZeroCheck")
data class PandaZeroCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaZeroCheckInstInfo(this)
}

@Serializable
@SerialName("LoadString")
data class PandaLoadStringInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadStringInstInfo(this)
}

@Serializable
@SerialName("CallVirtual")
data class PandaCallVirtualInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val method: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCallVirtualInstInfo(this)
}

@Serializable
@SerialName("LoadAndInitClass")
data class PandaLoadAndInitClassInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val loadedClass: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadAndInitClassInstInfo(this)
}

@Serializable
@SerialName("LoadClass")
data class PandaLoadClassInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadClassInstInfo(this)
}

@Serializable
@SerialName("InitClass")
data class PandaInitClassInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaInitClassInstInfo(this)
}

@Serializable
@SerialName("ReturnVoid")
data class PandaReturnVoidInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaTerminatingInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaReturnVoidInstInfo(this)
}

@Serializable
@SerialName("Return")
data class PandaReturnInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaTerminatingInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaReturnInstInfo(this)
}

@Serializable
@SerialName("Parameter")
data class PandaParameterInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val index: Int,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaParameterInstInfo(this)
}

@Serializable
@SerialName("LoadStatic")
data class PandaLoadStaticInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val enclosingClass: String,
    override val field: String,
) : PandaWithPropertyInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadStaticInstInfo(this)
}

@Serializable
@SerialName("LoadObject")
data class PandaLoadObjectInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val enclosingClass: String,
    override val field: String,
) : PandaWithPropertyInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadObjectInstInfo(this)
}

@Serializable
@SerialName("StoreStatic")
data class PandaStoreStaticInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val enclosingClass: String,
    override val field: String,
) : PandaWithPropertyInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaStoreStaticInstInfo(this)
}

@Serializable
@SerialName("StoreObject")
data class PandaStoreObjectInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val enclosingClass: String,
    override val field: String,
) : PandaWithPropertyInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaStoreObjectInstInfo(this)
}

@Serializable
@SerialName("LoadArray")
data class PandaLoadArrayInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadArrayInstInfo(this)
}

@Serializable
@SerialName("StoreArray")
data class PandaStoreArrayInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaStoreArrayInstInfo(this)
}

@Serializable
@SerialName("IsInstance")
data class PandaIsInstanceInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val candidateType: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaIsInstanceInstInfo(this)
}

@Serializable
@SerialName("CheckCast")
data class PandaCheckCastInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val candidateType: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCheckCastInstInfo(this)
}

@Serializable
@SerialName("Cast")
data class PandaCastInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCastInstInfo(this)
}

@Serializable
@SerialName("IfImm")
data class PandaIfImmInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val operator: String,
    override val operandsType: String,
    val immediate: ULong,
) : PandaComparisonInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaIfImmInstInfo(this)
}

@Serializable
@SerialName("Compare")
data class PandaCompareInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val operator: String,
    override val operandsType: String,
) : PandaComparisonInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCompareInstInfo(this)
}

@Serializable
@SerialName("Phi")
data class PandaPhiInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    val inputBlocks: List<Int>,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaPhiInstInfo(this)
}

@Serializable
@SerialName("Add")
data class PandaAddInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaAddInstInfo(this)
}

@Serializable
@SerialName("Sub")
data class PandaSubInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaSubInstInfo(this)
}

@Serializable
@SerialName("Mul")
data class PandaMulInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaMulInstInfo(this)
}

@Serializable
@SerialName("Div")
data class PandaDivInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaDivInstInfo(this)
}

@Serializable
@SerialName("Mod")
data class PandaModInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaModInstInfo(this)
}

@Serializable
@SerialName("And")
data class PandaAndInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaAndInstInfo(this)
}

@Serializable
@SerialName("Or")
data class PandaOrInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaOrInstInfo(this)
}

@Serializable
@SerialName("Xor")
data class PandaXorInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaXorInstInfo(this)
}

@Serializable
@SerialName("Shl")
data class PandaShlInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaShlInstInfo(this)
}

@Serializable
@SerialName("Shr")
data class PandaShrInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaShrInstInfo(this)
}

@Serializable
@SerialName("AShl")
data class PandaAShlInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaAShlInstInfo(this)
}

@Serializable
@SerialName("AShr")
data class PandaAShrInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaAShrInstInfo(this)
}

@Serializable
@SerialName("Cmp")
data class PandaCmpInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
    override val operator: String,
    override val operandsType: String,
) : PandaComparisonInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCmpInstInfo(this)
}

@Serializable
@SerialName("Throw")
data class PandaThrowInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaTerminatingInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaThrowInstInfo(this)
}

@Serializable
@SerialName("NegativeCheck")
data class PandaNegativeCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNegativeCheckInstInfo(this)
}

@Serializable
@SerialName("SaveStateDeoptimize")
data class PandaSaveStateDeoptimizeInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaSaveStateDeoptimizeInstInfo(this)
}

@Serializable
@SerialName("Neg")
data class PandaNegInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNegInstInfo(this)
}

@Serializable
@SerialName("Not")
data class PandaNotInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNotInstInfo(this)
}

@Serializable
@SerialName("LenArray")
data class PandaLenArrayInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLenArrayInstInfo(this)
}

@Serializable
@SerialName("BoundsCheck")
data class PandaBoundsCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaBoundsCheckInstInfo(this)
}

@Serializable
@SerialName("NullPtr")
data class PandaNullPtrInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaNullPtrInstInfo(this)
}

@Serializable
@SerialName("LoadUndefined")
data class PandaLoadUndefinedInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaLoadUndefinedInstInfo(this)
}

@Serializable
@SerialName("RefTypeCheck")
data class PandaRefTypeCheckInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaRefTypeCheckInstInfo(this)
}

@Serializable
@SerialName("Try")
data class PandaTryInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaTryInstInfo(this)
}

@Serializable
@SerialName("CatchPhi")
data class PandaCatchPhiInstIr(
    override val id: String,
    override val inputs: List<String> = emptyList(),
    override val users: List<String> = emptyList(),
    override val type: String,
    override val opcode: String,
) : PandaInstIr {
    override fun <T> accept(visitor: PandaInstIrVisitor<T>): T =
        visitor.visitPandaCatchPhiInstInfo(this)
}
