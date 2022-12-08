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

package org.utbot.jcdb.impl.cfg

import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.cfg.JcGraph
import org.utbot.jcdb.api.cfg.JcRawInst
import org.utbot.jcdb.api.cfg.JcRawInstList
import org.utbot.jcdb.api.cfg.JcRawInstVisitor
import org.utbot.jcdb.api.cfg.JcRawLabelInst

class JcRawInstListImpl(
    instructions: List<JcRawInst>
) : Iterable<JcRawInst>, JcRawInstList {
    private val _instructions = instructions.toMutableList()
    override val instructions: List<JcRawInst> get() = _instructions

    override val size get() = instructions.size
    override val indices get() = instructions.indices
    override val lastIndex get() = instructions.lastIndex

    override operator fun get(index: Int) = instructions[index]
    override fun getOrNull(index: Int) = instructions.getOrNull(index)
    fun getOrElse(index: Int, defaultValue: (Int) -> JcRawInst) = instructions.getOrElse(index, defaultValue)
    override fun iterator(): Iterator<JcRawInst> = instructions.iterator()

    override fun toString(): String = _instructions.joinToString(separator = "\n") {
        when (it) {
            is JcRawLabelInst -> "$it"
            else -> "  $it"
        }
    }

    override fun insertBefore(inst: JcRawInst, vararg newInstructions: JcRawInst) = insertBefore(inst, newInstructions.toList())
    override fun insertBefore(inst: JcRawInst, newInstructions: Collection<JcRawInst>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index, newInstructions)
    }

    override fun insertAfter(inst: JcRawInst, vararg newInstructions: JcRawInst) = insertBefore(inst, newInstructions.toList())
    override fun insertAfter(inst: JcRawInst, newInstructions: Collection<JcRawInst>) {
        val index = _instructions.indexOf(inst)
        assert(index >= 0)
        _instructions.addAll(index + 1, newInstructions)
    }

    override fun remove(inst: JcRawInst): Boolean {
        return _instructions.remove(inst)
    }

    override fun removeAll(inst: Collection<JcRawInst>): Boolean {
        return _instructions.removeAll(inst)
    }

    override fun graph(method: JcMethod): JcGraph =
        JcGraphBuilder(method.enclosingClass.classpath, this, method).build()
}


fun JcRawInstList.filter(visitor: JcRawInstVisitor<Boolean>) =
    JcRawInstListImpl(instructions.filter { it.accept(visitor) })

fun JcRawInstList.filterNot(visitor: JcRawInstVisitor<Boolean>) =
    JcRawInstListImpl(instructions.filterNot { it.accept(visitor) })

fun JcRawInstList.map(visitor: JcRawInstVisitor<JcRawInst>) =
    JcRawInstListImpl(instructions.map { it.accept(visitor) })

fun JcRawInstList.mapNotNull(visitor: JcRawInstVisitor<JcRawInst?>) =
    JcRawInstListImpl(instructions.mapNotNull { it.accept(visitor) })

fun JcRawInstList.flatMap(visitor: JcRawInstVisitor<Collection<JcRawInst>>) =
    JcRawInstListImpl(instructions.flatMap { it.accept(visitor) })
