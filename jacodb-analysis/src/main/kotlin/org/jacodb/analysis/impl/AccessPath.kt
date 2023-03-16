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

package org.jacodb.analysis.impl

import org.jacodb.api.JcField
import org.jacodb.api.cfg.JcLocal

/**
 * This class is used to represent an access path that is needed for problems
 * where dataflow facts could be correlated with variables/values (such as NPE, uninitialized variable, etc.)
 */
data class AccessPath private constructor(val value: JcLocal, val fieldAccesses: List<JcField>) {
    companion object {

        fun fromLocal(value: JcLocal) = AccessPath(value, listOf())

        fun fromOther(other: AccessPath, fields: List<JcField>, maxLength: Int) = AccessPath(other.value, other.fieldAccesses.plus(fields).take(maxLength))
    }

    val isOnHeap: Boolean
        get() = fieldAccesses.isNotEmpty()

    override fun toString(): String {
        var str = value.toString()
        for (field in fieldAccesses) {
            str += ".${field.name}"
        }
        return str
    }
}