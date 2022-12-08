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

package org.utbot.jcdb.api.cfg

import org.utbot.jcdb.api.JcMethod

interface JcRawInstList {
    val instructions: List<JcRawInst>
    val size: Int
    val indices: IntRange
    val lastIndex: Int

    operator fun get(index: Int): JcRawInst
    fun getOrNull(index: Int): JcRawInst?
    fun iterator(): Iterator<JcRawInst>
    fun insertBefore(inst: JcRawInst, vararg newInstructions: JcRawInst)
    fun insertBefore(inst: JcRawInst, newInstructions: Collection<JcRawInst>)
    fun insertAfter(inst: JcRawInst, vararg newInstructions: JcRawInst)
    fun insertAfter(inst: JcRawInst, newInstructions: Collection<JcRawInst>)
    fun remove(inst: JcRawInst): Boolean
    fun removeAll(inst: Collection<JcRawInst>): Boolean
    fun graph(method: JcMethod): JcGraph
}