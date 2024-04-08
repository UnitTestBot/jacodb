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

package org.jacodb.panda.staticvm.ets

data class Parameter(
    val td: TypeDesc,
    val name: String,
    val attributes: Attributes,
) {
    val type: Type
        get() = Type.resolve(td)!!

    fun isRest(): Boolean = attributes.isRest()
    fun isOptional(): Boolean = attributes.isOptional()

    override fun toString(): String = "$name: $type"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Parameter) return false
        if (td != other.td) return false
        if (name != other.name) return false
        if (attributes != other.attributes) return false
        return true
    }

    override fun hashCode(): Int {
        var result = td.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + attributes.hashCode()
        return result
    }
}
