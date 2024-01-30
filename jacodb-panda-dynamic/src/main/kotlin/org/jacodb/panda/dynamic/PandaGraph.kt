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

package org.jacodb.panda.dynamic

import org.jacodb.api.jvm.cfg.JcBytecodeGraph

data class PandaBasicBlock(
    val id: Int,
    val successors: Set<Int>,
    val predecessors: Set<Int>,
    val start: PandaInstRef,
    val end: PandaInstRef
) {

    fun contains(inst: PandaInst): Boolean {
        return inst.location.index <= end.index && inst.location.index >= start.index
    }

    fun contains(inst: PandaInstRef): Boolean {
        return inst.index <= end.index && inst.index >= start.index
    }
}

class PandaGraph : JcBytecodeGraph<PandaInst> {

    override fun successors(node: PandaInst): Set<PandaInst> {
        TODO("Not yet implemented")
    }

    override fun predecessors(node: PandaInst): Set<PandaInst> {
        TODO("Not yet implemented")
    }

    override fun throwers(node: PandaInst): Set<PandaInst> {
        TODO("Not yet implemented")
    }

    override fun catchers(node: PandaInst): Set<PandaInst> {
        TODO("Not yet implemented")
    }

    override val entries: List<PandaInst>
        get() = TODO("Not yet implemented")
    override val exits: List<PandaInst>
        get() = TODO("Not yet implemented")
    override val instructions: List<PandaInst>
        get() = TODO("Not yet implemented")

    override fun iterator(): Iterator<PandaInst> {
        TODO("Not yet implemented")
    }

}

class PandaBlockGraph(override val instructions: List<PandaBasicBlock>) : JcBytecodeGraph<PandaBasicBlock> {

    fun graph(): PandaGraph {
        // TODO
        return PandaGraph()
    }

    override fun successors(node: PandaBasicBlock): Set<PandaBasicBlock> {
        return node.successors.map { instructions[it] }.toSet()
    }

    override fun predecessors(node: PandaBasicBlock): Set<PandaBasicBlock> {
        return node.predecessors.map { instructions[it] }.toSet()
    }

    override fun throwers(node: PandaBasicBlock): Set<PandaBasicBlock> {
        TODO("Not yet implemented")
    }

    override fun catchers(node: PandaBasicBlock): Set<PandaBasicBlock> {
        TODO("Not yet implemented")
    }

    override val entries: List<PandaBasicBlock>
        get() = listOf(instructions.first())
    override val exits: List<PandaBasicBlock>
        get() = instructions.filter { it.successors.isEmpty() }


    override fun iterator(): Iterator<PandaBasicBlock> {
        return instructions.iterator()
    }

}
