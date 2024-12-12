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

package org.jacodb.taint.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

interface TaintActionVisitor<out R> {
    fun visit(action: CopyAllMarks): R
    fun visit(action: CopyMark): R
    fun visit(action: AssignMark): R
    fun visit(action: RemoveAllMarks): R
    fun visit(action: RemoveMark): R
}

interface Action {
    fun <R> accept(visitor: TaintActionVisitor<R>): R
}

// TODO add marks for aliases (if you pass an object and return it from the function)

data class CopyAllMarks(
    val from: Position,
    val to: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

data class CopyMark(
    val mark: TaintMark,
    val from: Position,
    val to: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

data class AssignMark(
    val mark: TaintMark,
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

data class RemoveAllMarks(
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

data class RemoveMark(
    val mark: TaintMark,
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

@Serializable
sealed interface SerializedAction

val actionModule = SerializersModule {
    polymorphic(SerializedAction::class) {
        subclass(SerializedCopyAllMarks::class)
        subclass(SerializedCopyMark::class)
        subclass(SerializedAssignMark::class)
        subclass(SerializedRemoveAllMarks::class)
        subclass(SerializedRemoveMark::class)
    }
}

@Serializable
@SerialName("CopyAllMarks")
data class SerializedCopyAllMarks(
    val from: SerializedPosition,
    val to: SerializedPosition,
) : SerializedAction

@Serializable
@SerialName("CopyMark")
data class SerializedCopyMark(
    val mark: TaintMark,
    val from: SerializedPosition,
    val to: SerializedPosition,
) : SerializedAction

@Serializable
@SerialName("AssignMark")
data class SerializedAssignMark(
    val mark: TaintMark,
    val position: SerializedPosition,
) : SerializedAction

@Serializable
@SerialName("RemoveAllMarks")
data class SerializedRemoveAllMarks(
    val position: SerializedPosition,
) : SerializedAction

@Serializable
@SerialName("RemoveMark")
data class SerializedRemoveMark(
    val mark: TaintMark,
    val position: SerializedPosition,
) : SerializedAction
