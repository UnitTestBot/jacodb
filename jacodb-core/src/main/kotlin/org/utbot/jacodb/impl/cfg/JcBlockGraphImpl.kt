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

package org.utbot.jacodb.impl.cfg

import org.utbot.jacodb.api.cfg.JcBasicBlock
import org.utbot.jacodb.api.cfg.JcBlockGraph
import org.utbot.jacodb.api.cfg.JcBranchingInst
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.cfg.JcInstRef
import org.utbot.jacodb.api.cfg.JcTerminatingInst

class JcBlockGraphImpl(
    override val jcGraph: JcGraphImpl
) : Iterable<JcBasicBlock>, JcBlockGraph {
    private val _basicBlocks = mutableListOf<JcBasicBlock>()
    private val predecessorMap = mutableMapOf<JcBasicBlock, MutableSet<JcBasicBlock>>()
    private val successorMap = mutableMapOf<JcBasicBlock, MutableSet<JcBasicBlock>>()
    private val catchersMap = mutableMapOf<JcBasicBlock, MutableSet<JcBasicBlock>>()
    private val throwersMap = mutableMapOf<JcBasicBlock, MutableSet<JcBasicBlock>>()

    override val basicBlocks: List<JcBasicBlock> get() = _basicBlocks
    override val entry: JcBasicBlock
        get() = basicBlocks.single {
            predecessors(it).isEmpty() && jcGraph.throwers(it.start).isEmpty()
        }
    override val exits: List<JcBasicBlock> get() = basicBlocks.filter { successors(it).isEmpty() }

    init {
        val inst2Block = mutableMapOf<JcInst, JcBasicBlock>()

        val currentRefs = mutableListOf<JcInstRef>()

        val createBlock = {
            val block = JcBasicBlock(currentRefs.first(), currentRefs.last())
            for (ref in currentRefs) {
                inst2Block[jcGraph.inst(ref)] = block
            }
            currentRefs.clear()
            _basicBlocks.add(block)
        }
        for (inst in jcGraph.instructions) {
            val currentRef = jcGraph.ref(inst)
            val shouldBeAddedBefore = jcGraph.predecessors(inst).size <= 1 || currentRefs.isEmpty()
            val shouldTerminate = when {
                currentRefs.isEmpty() -> false
                else -> jcGraph.catchers(currentRefs.first()) != jcGraph.catchers(currentRef)
            }
            if (shouldTerminate) {
                createBlock()
            }
            when {
                inst is JcBranchingInst
                        || inst is JcTerminatingInst
                        || jcGraph.predecessors(inst).size > 1 -> {
                    if (shouldBeAddedBefore) currentRefs += currentRef
                    createBlock()
                    if (!shouldBeAddedBefore) {
                        currentRefs += currentRef
                        createBlock()
                    }
                }

                else -> {
                    currentRefs += currentRef
                }
            }
        }
        if (currentRefs.isNotEmpty()) {
            val block = JcBasicBlock(currentRefs.first(), currentRefs.last())
            for (ref in currentRefs) {
                inst2Block[jcGraph.inst(ref)] = block
            }
            currentRefs.clear()
            _basicBlocks.add(block)
        }
        for (block in _basicBlocks) {
            predecessorMap.getOrPut(block, ::mutableSetOf) += jcGraph.predecessors(block.start).map { inst2Block[it]!! }
            successorMap.getOrPut(block, ::mutableSetOf) += jcGraph.successors(block.end).map { inst2Block[it]!! }
            catchersMap.getOrPut(block, ::mutableSetOf) += jcGraph.catchers(block.start).map { inst2Block[it]!! }.also {
                for (catcher in it) {
                    throwersMap.getOrPut(catcher, ::mutableSetOf) += block
                }
            }
        }
    }

    override fun instructions(block: JcBasicBlock): List<JcInst> =
        (block.start.index..block.end.index).map { jcGraph.instructions[it] }

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun predecessors(block: JcBasicBlock): Set<JcBasicBlock> = predecessorMap.getOrDefault(block, emptySet())
    override fun successors(block: JcBasicBlock): Set<JcBasicBlock> = successorMap.getOrDefault(block, emptySet())

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     */
    override fun catchers(block: JcBasicBlock): Set<JcBasicBlock> = catchersMap.getOrDefault(block, emptySet())
    override fun throwers(block: JcBasicBlock): Set<JcBasicBlock> = throwersMap.getOrDefault(block, emptySet())

    override fun iterator(): Iterator<JcBasicBlock> = basicBlocks.iterator()
}