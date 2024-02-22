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

package org.jacodb.api.common.cfg

interface InstList<INST> : Iterable<INST> {
    val instructions: List<INST>
    val size: Int
    val indices: IntRange
    val lastIndex: Int

    operator fun get(index: Int): INST
    fun getOrNull(index: Int): INST?

    fun toMutableList(): MutableInstList<INST>
}

interface MutableInstList<INST> : InstList<INST> {
    fun insertBefore(inst: INST, vararg newInstructions: INST)
    fun insertBefore(inst: INST, newInstructions: Collection<INST>)
    fun insertAfter(inst: INST, vararg newInstructions: INST)
    fun insertAfter(inst: INST, newInstructions: Collection<INST>)
    fun remove(inst: INST): Boolean
    fun removeAll(inst: Collection<INST>): Boolean
}
