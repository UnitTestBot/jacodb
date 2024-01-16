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

package org.jacodb.analysis.paths

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcLocal

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values (such as NPE, uninitialized variable, etc.)
 */
data class AccessPath private constructor(val value: JcLocal?, val accesses: List<Accessor>) {
    companion object {

        fun fromLocal(value: JcLocal) = AccessPath(value, listOf())

        fun fromStaticField(field: JcField): AccessPath {
            if (!field.isStatic) {
                throw IllegalArgumentException("Expected static field")
            }

            return AccessPath(null, listOf(FieldAccessor(field)))
        }

        fun fromOther(other: AccessPath, accesses: List<Accessor>): AccessPath {
            if (accesses.any { it is FieldAccessor && it.field.isStatic }) {
                throw IllegalArgumentException("Unexpected static field")
            }

            return AccessPath(other.value, other.accesses.plus(accesses))
        }
    }

    fun limit(n: Int) = AccessPath(value, accesses.take(n))

    val isOnHeap: Boolean
        get() = accesses.isNotEmpty()

    val isStatic: Boolean
        get() = value == null

    override fun toString(): String {
        var str = value.toString()
        for (accessor in accesses) {
            str += ".$accessor"
        }
        return str
    }
}