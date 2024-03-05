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

val actionModule = SerializersModule {
    polymorphic(Action::class) {
        subclass(CopyAllMarks::class)
        subclass(CopyMark::class)
        subclass(AssignMark::class)
        subclass(RemoveAllMarks::class)
        subclass(RemoveMark::class)
    }
}

// TODO add marks for aliases (if you pass an object and return it from the function)

/**
 * Copies all the marks from the [from] object to the [to].
 * As a result, all marks from the initial object will be copied into the target object.
 * If some marks were present only in the [to] object, they remain unchanged. <p>
 *
 * Behaviour should be the same as if [CopyMark] is applied to every existing mark.
 */
@Serializable
@SerialName("CopyAllMarks")
data class CopyAllMarks(
    val from: Position,
    val to: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

/**
 * Adds the [mark] to the [to] object if it is present in the [from] object.
 */
@Serializable
@SerialName("CopyMark")
data class CopyMark(
    val mark: TaintMark,
    val from: Position,
    val to: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

/**
 * Assigns the [mark] to the [position].
 */
@Serializable
@SerialName("AssignMark")
data class AssignMark(
    val mark: TaintMark,
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

/**
 * Removes all the marks from the [position].
 */
@Serializable
@SerialName("RemoveAllMarks")
data class RemoveAllMarks(
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}

/**
 * Removes a particular [mark] from the [position].
 */
@Serializable
@SerialName("RemoveMark")
data class RemoveMark(
    val mark: TaintMark,
    val position: Position,
) : Action {
    override fun <R> accept(visitor: TaintActionVisitor<R>): R = visitor.visit(this)
}
