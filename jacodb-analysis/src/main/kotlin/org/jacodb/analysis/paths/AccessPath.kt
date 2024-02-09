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

import org.jacodb.api.JcField
import org.jacodb.api.JcTypedField
import org.jacodb.api.cfg.JcSimpleValue

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values (such as NPE, uninitialized variable, etc.)
 */
data class AccessPath private constructor(
    val value: JcSimpleValue?, // null for static field
    val accesses: List<Accessor>,
) {
    init {
        if (value == null) {
            require(accesses.isNotEmpty())
            val a = accesses[0]
            require(a is FieldAccessor)
            require(a.field.isStatic)
        }
    }

    val isOnHeap: Boolean
        get() = accesses.isNotEmpty()

    val isStatic: Boolean
        get() = value == null

    fun limit(n: Int): AccessPath = AccessPath(value, accesses.take(n))

    operator fun div(accesses: List<Accessor>): AccessPath {
        for (accessor in accesses) {
            if (accessor is FieldAccessor && accessor.field.isStatic) {
                throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
            }
        }

        return AccessPath(value, this.accesses + accesses)
    }

    operator fun div(accessor: Accessor): AccessPath {
        if (accessor is FieldAccessor && accessor.field.isStatic) {
            throw IllegalArgumentException("Unexpected static field: ${accessor.field}")
        }

        return AccessPath(value, this.accesses + accessor)
    }

    override fun toString(): String {
        return value.toString() + accesses.joinToString("") { it.toSuffix() }
    }

    companion object {
        fun from(value: JcSimpleValue): AccessPath = AccessPath(value, emptyList())

        fun fromStaticField(field: JcField): AccessPath {
            require(field.isStatic) { "Expected static field" }
            return AccessPath(null, listOf(FieldAccessor(field)))
        }

        fun fromStaticField(field: JcTypedField): AccessPath {
            require(field.isStatic) { "Expected static field" }
            return AccessPath(null, listOf(FieldAccessor(field.field)))
        }
    }
}
