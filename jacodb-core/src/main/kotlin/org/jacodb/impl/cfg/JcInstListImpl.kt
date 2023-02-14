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

package org.jacodb.impl.cfg

import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.cfg.JcRawInstVisitor
import org.jacodb.api.cfg.JcRawLabelInst

class JcInstListImpl<INST>(
    instructions: List<INST>
) : Iterable<INST>, JcInstList<INST> {
    private val _instructions = instructions.toMutableList()
    override val instructions: List<INST> get() = _instructions

    override val size get() = instructions.size
    override val indices get() = instructions.indices
    override val lastIndex get() = instructions.lastIndex

    override operator fun get(index: Int) = instructions[index]
    override fun getOrNull(index: Int) = instructions.getOrNull(index)
    fun getOrElse(index: Int, defaultValue: (Int) -> INST) = instructions.getOrElse(index, defaultValue)
    override fun iterator(): Iterator<INST> = instructions.iterator()

    override fun toString(): String = _instructions.joinToString(separator = "\n") {
        when (it) {
            is JcRawLabelInst -> "$it"
            else -> "  $it"
        }
    }

    override fun insertBefore(inst: INST, vararg newInstructions: INST) = insertBefore(inst, newInstructions.toList())
    override fun insertBefore(inst: INST, newInstructions: Collection<INST>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index, newInstructions)
    }

    override fun insertAfter(inst: INST, vararg newInstructions: INST) = insertBefore(inst, newInstructions.toList())
    override fun insertAfter(inst: INST, newInstructions: Collection<INST>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index + 1, newInstructions)
    }

    override fun remove(inst: INST): Boolean {
        return _instructions.remove(inst)
    }

    override fun removeAll(inst: Collection<INST>): Boolean {
        return _instructions.removeAll(inst)
    }
}


fun JcInstList<JcRawInst>.filter(visitor: JcRawInstVisitor<Boolean>) =
    JcInstListImpl(instructions.filter { it.accept(visitor) })

fun JcInstList<JcRawInst>.filterNot(visitor: JcRawInstVisitor<Boolean>) =
    JcInstListImpl(instructions.filterNot { it.accept(visitor) })

fun JcInstList<JcRawInst>.map(visitor: JcRawInstVisitor<JcRawInst>) =
    JcInstListImpl(instructions.map { it.accept(visitor) })

fun JcInstList<JcRawInst>.mapNotNull(visitor: JcRawInstVisitor<JcRawInst?>) =
    JcInstListImpl(instructions.mapNotNull { it.accept(visitor) })

fun JcInstList<JcRawInst>.flatMap(visitor: JcRawInstVisitor<Collection<JcRawInst>>) =
    JcInstListImpl(instructions.flatMap { it.accept(visitor) })
