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

import org.jacodb.api.core.cfg.InstList
import org.jacodb.api.core.cfg.MutableInstList
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawInstVisitor
import org.jacodb.api.jvm.cfg.JcRawLabelInst

open class InstListImpl<INST>(
    instructions: List<INST>
) : Iterable<INST>, InstList<INST> {
    protected val _instructions = instructions.toMutableList()

    override val instructions: List<INST> get() = _instructions

    override val size get() = instructions.size
    override val indices get() = instructions.indices
    override val lastIndex get() = instructions.lastIndex

    override operator fun get(index: Int) = instructions[index]
    override fun getOrNull(index: Int) = instructions.getOrNull(index)
    fun getOrElse(index: Int, defaultValue: (Int) -> INST) = instructions.getOrElse(index, defaultValue)
    override fun iterator(): Iterator<INST> = instructions.iterator()

    override fun toMutableList() = MutableInstListImpl(_instructions)

    override fun toString(): String = _instructions.joinToString(separator = "\n") {
        when (it) {
            is JcRawLabelInst -> "$it"
            else -> "  $it"
        }
    }

}

class MutableInstListImpl<INST>(instructions: List<INST>) : InstListImpl<INST>(instructions),
    MutableInstList<INST> {

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


fun InstList<JcRawInst>.filter(visitor: JcRawInstVisitor<Boolean>) =
    InstListImpl(instructions.filter { it.accept(visitor) })

fun InstList<JcRawInst>.filterNot(visitor: JcRawInstVisitor<Boolean>) =
    InstListImpl(instructions.filterNot { it.accept(visitor) })

fun InstList<JcRawInst>.map(visitor: JcRawInstVisitor<JcRawInst>) =
    InstListImpl(instructions.map { it.accept(visitor) })

fun InstList<JcRawInst>.mapNotNull(visitor: JcRawInstVisitor<JcRawInst?>) =
    InstListImpl(instructions.mapNotNull { it.accept(visitor) })

fun InstList<JcRawInst>.flatMap(visitor: JcRawInstVisitor<Collection<JcRawInst>>) =
    InstListImpl(instructions.flatMap { it.accept(visitor) })