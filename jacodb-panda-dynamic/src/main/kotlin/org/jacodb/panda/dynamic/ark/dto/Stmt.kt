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

package org.jacodb.panda.dynamic.ark.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface Stmt

@Serializable
@SerialName("UnknownStmt")
data class UnknownStmt(
    val stmt: JsonElement,
) : Stmt

@Serializable
@SerialName("NopStmt")
object NopStmt : Stmt {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("AssignStmt")
data class AssignStmt(
    val left: Value, // Local
    val right: Value,
) : Stmt

@Serializable
@SerialName("ArkInvokeStmt")
data class CallStmt(
    val expr: CallExpr,
) : Stmt

@Serializable
@SerialName("DeleteStmt")
data class DeleteStmt(
    val arg: FieldRef,
) : Stmt

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface TerminatingStmt : Stmt

@Serializable
@SerialName("ReturnStmt")
data class ReturnStmt(
    val arg: Value?,
) : TerminatingStmt

@Serializable
@SerialName("ThrowStmt")
data class ThrowStmt(
    val arg: Value,
) : TerminatingStmt

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("_")
sealed interface BranchingStmt : Stmt

@Serializable
@SerialName("GotoStmt")
object GotoStmt : BranchingStmt {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("IfStmt")
data class IfStmt(
    val condition: ConditionExpr,
) : BranchingStmt

@Serializable
@SerialName("SwitchStmt")
data class SwitchStmt(
    val arg: Value,
    val cases: List<Value>,
) : BranchingStmt
