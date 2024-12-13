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

fun interface PositionResolver<out R> {
    fun resolve(position: Position): R
}

@Serializable
sealed interface SerializedPosition

@Serializable
sealed interface Position

@Serializable
@SerialName("Argument")
data class Argument(@SerialName("number") val index: Int) : Position, SerializedPosition

@Serializable
@SerialName("AllAnnotatedArguments")
data class AllAnnotatedArguments(
    @SerialName("type") val typeMatcher: TypeMatcher
) : SerializedPosition

@Serializable
@SerialName("AnyArgument")
object AnyArgument : SerializedPosition {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("This")
object This : Position, SerializedPosition {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("Result")
object Result : Position, SerializedPosition {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
@SerialName("ResultAnyElement")
object ResultAnyElement : Position, SerializedPosition {
    override fun toString(): String = javaClass.simpleName
}

@Serializable
sealed interface PositionAccessor {
    @Serializable
    @SerialName("ElementAccessor")
    object ElementAccessor : PositionAccessor {
        override fun toString(): String = javaClass.simpleName
    }

    @Serializable
    @SerialName("AllPositions")
    object AllPositions : PositionAccessor {
        override fun toString(): String = javaClass.simpleName
    }

    @Serializable
    data class FieldAccessor(
        val className: String,
        val fieldName: String,
        val fieldType: String
    ) : PositionAccessor
}

@Serializable
@SerialName("PositionWithAccess")
data class SerializedPositionWithAccess(
    val base: SerializedPosition,
    val access: PositionAccessor
): SerializedPosition

data class PositionWithAccess(
    val base: Position,
    val access: PositionAccessor
): Position
