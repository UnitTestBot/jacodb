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

/**
 * A class representing a position of tainted data in a method.
 */
@Serializable
sealed interface Position

/**
 * Represents an argument of a method call.
 * Numeration starts from zero, `this` parameter is not included. <p>
 *
 * For instance, `obj.foo(a, b)` -> a := Argument(0), b := Argument(1)
 */
@Serializable
@SerialName("Argument")
data class Argument(@SerialName("number") val index: Int) : Position

/**
 * Represents any argument of a method call except `this` instance,
 * This is a short form for a set of [Argument]s with all indices of the method parameters.
 */
@Serializable
@SerialName("AnyArgument")
object AnyArgument : Position {
    override fun toString(): String = javaClass.simpleName
}

/**
 * Represents `this` argument of a method call.
 */
@Serializable
@SerialName("This")
object This : Position {
    override fun toString(): String = javaClass.simpleName
}

/**
 * Represents a resulting value of a method call.
 * It is for regularly returned objects only, and it is not suitable for thrown exceptions.
 */
@Serializable
@SerialName("Result")
object Result : Position {
    override fun toString(): String = javaClass.simpleName
}

/**
 * Represents any element of the collection (string, array, list, etc.),
 * returned as a result from a method.
 */
@Serializable
@SerialName("ResultAnyElement")
object ResultAnyElement : Position {
    override fun toString(): String = javaClass.simpleName
}
