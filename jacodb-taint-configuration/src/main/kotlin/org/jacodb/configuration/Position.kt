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

package org.jacodb.configuration

interface PositionResolver<R> {
    fun resolve(position: Position): R
}

sealed interface Position

data class Argument(val number: Int) : Position

object AnyArgument : Position {
    override fun toString(): String = "AnyArgument"
}

object ThisArgument : Position {
    override fun toString(): String = "This"
}

object Result : Position {
    override fun toString(): String = "Result"
}
