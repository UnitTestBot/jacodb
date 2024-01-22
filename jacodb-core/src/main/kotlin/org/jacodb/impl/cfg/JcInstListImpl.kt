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
import org.jacodb.api.cfg.JcMutableInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.cfg.JcRawInstVisitor
import org.jacodb.api.cfg.JcRawLabelInst

open class JcInstListImpl<INST>(
    instructions: List<INST>,
) : Iterable<INST>, JcInstList<INST> {
    protected val _instructions: MutableList<INST> = instructions.toMutableList()
    override val instructions: List<INST> get() = _instructions

    override val size: Int get() = instructions.size
    override val indices: IntRange get() = instructions.indices
    override val lastIndex: Int get() = instructions.lastIndex

    override operator fun get(index: Int): INST = instructions[index]
    override fun getOrNull(index: Int): INST? = instructions.getOrNull(index)

    override fun iterator(): Iterator<INST> = instructions.iterator()

    override fun toMutableList(): JcMutableInstList<INST> = JcMutableInstListImpl(_instructions)

    override fun toString(): String = _instructions.joinToString(separator = "\n") {
        when (it) {
            is JcRawLabelInst -> "$it"
            else -> "  $it"
        }
    }

}

class JcMutableInstListImpl<INST>(instructions: List<INST>) :
    JcInstListImpl<INST>(instructions), JcMutableInstList<INST> {

    override fun insertBefore(inst: INST, vararg newInstructions: INST) = insertBefore(inst, newInstructions.toList())
    override fun insertBefore(inst: INST, newInstructions: Collection<INST>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index, newInstructions)
    }

    // TODO: maybe call insertAfter ?
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

fun JcInstList<JcRawInst>.filter(visitor: JcRawInstVisitor<Boolean>): JcInstList<JcRawInst> =
    JcInstListImpl(instructions.filter { it.accept(visitor) })

fun JcInstList<JcRawInst>.filterNot(visitor: JcRawInstVisitor<Boolean>): JcInstList<JcRawInst> =
    JcInstListImpl(instructions.filterNot { it.accept(visitor) })

fun JcInstList<JcRawInst>.map(visitor: JcRawInstVisitor<JcRawInst>): JcInstList<JcRawInst> =
    JcInstListImpl(instructions.map { it.accept(visitor) })

fun JcInstList<JcRawInst>.mapNotNull(visitor: JcRawInstVisitor<JcRawInst?>): JcInstList<JcRawInst> =
    JcInstListImpl(instructions.mapNotNull { it.accept(visitor) })

fun JcInstList<JcRawInst>.flatMap(visitor: JcRawInstVisitor<Collection<JcRawInst>>): JcInstList<JcRawInst> =
    JcInstListImpl(instructions.flatMap { it.accept(visitor) })
