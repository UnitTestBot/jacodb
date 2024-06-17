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

package org.jacodb.panda.dynamic.parser

import org.jacodb.panda.dynamic.api.PandaBasicBlock
import org.jacodb.panda.dynamic.api.PandaEmptyBBPlaceholderInst
import org.jacodb.panda.dynamic.api.PandaGotoInst
import org.jacodb.panda.dynamic.api.PandaInstRef
import org.jacodb.panda.dynamic.api.PandaNopInst

enum class TraversalType {
    DEFAULT,
    TRY_BLOCK,
}

class IRTraversalManager(
    private val programMethod: ProgramMethod,
    private val irParser: IRParser,
) {
    private val strategyStack = ArrayDeque<TraversalStrategy>()

    // bb id -> ProgramBasicBlock
    private val idToProgramBB = programMethod.basicBlocks.associateBy { it.id }
    // .zip(programMethod.basicBlocks).toMap()

    private val unprocessedBB = programMethod.basicBlocks.toMutableSet()

    abstract inner class TraversalStrategy(val env: IREnvironment) {

        val pendingBB = ArrayDeque<ProgramBasicBlock>()

        abstract fun chooseBB(): ProgramBasicBlock?

        open fun checkCondition(bb: ProgramBasicBlock): Boolean {
            return false
        }

        fun step() {
            val programBB = chooseBB() ?: run {
                strategyStack.removeFirst()
                return
            }

            if (programBB !in unprocessedBB) return

            parseBB(programBB, env.copy())

            if (checkCondition(programBB)) strategyStack.removeFirst()

            val naturalOrderSuccessors = programBB.successors.sortedBy { bbId ->
                programMethod.basicBlocks.indexOfFirst { pbb -> pbb.id == bbId }
            }


            if (strategyStack.first() == this) {
                for (succBBId in naturalOrderSuccessors) {
                    val succBB = idToProgramBB[succBBId]!!
                    if (succBB in unprocessedBB) pendingBB.addLast(idToProgramBB[succBBId]!!)
                }
            }
        }

    }

    inner class DefaultTraversalStrategy(env: IREnvironment) : TraversalStrategy(env) {

        override fun chooseBB(): ProgramBasicBlock? {
            return pendingBB.removeFirstOrNull()
        }
    }

    inner class TryBlockTraversalStrategy(
        env: IREnvironment,
    ) : TraversalStrategy(env) {

        override fun chooseBB(): ProgramBasicBlock? {
            return pendingBB.removeFirstOrNull()
        }

        override fun checkCondition(bb: ProgramBasicBlock): Boolean {
            return bb.insts.isEmpty()
        }
    }

    inner class CatchBlockTraversalStrategy(
        env: IREnvironment,
    ) : TraversalStrategy(env) {

        override fun chooseBB(): ProgramBasicBlock? {

            return pendingBB.removeFirstOrNull()
        }

        override fun checkCondition(bb: ProgramBasicBlock): Boolean {
            return bb.insts.isEmpty()
        }
    }

    init {
        val defStrategy = DefaultTraversalStrategy(IREnvironment.emptyEnv).apply {
            for (bb in programMethod.basicBlocks) {
                pendingBB.addLast(bb)
            }
        }
        strategyStack.add(defStrategy)
    }

    fun run() {
        while (unprocessedBB.isNotEmpty()) {
            step()
        }
    }

    private fun step() {
        val currentStrategy = strategyStack.first()
        currentStrategy.step()
    }

    private fun setTraversalStrategy(progBB: ProgramBasicBlock, ts: TraversalType) {
        val currentStrategy = strategyStack.first()
        when (ts) {
            TraversalType.TRY_BLOCK -> {

                val catchStrategy = CatchBlockTraversalStrategy(currentStrategy.env.copy()).apply {
                    pendingBB.addLast(idToProgramBB[progBB.successors[1]]!!)
                }

                val tryStrategy = TryBlockTraversalStrategy(currentStrategy.env.copy()).apply {
                    pendingBB.addLast(idToProgramBB[progBB.successors[0]]!!)
                }

                strategyStack.addFirst(catchStrategy)
                strategyStack.addFirst(tryStrategy)
            }

            TraversalType.DEFAULT -> {
                error("Setting DEFAULT traversal strategy is not allowed!")
            }
        }
    }

    private fun parseBB(currentBB: ProgramBasicBlock, env: IREnvironment) {
        unprocessedBB.remove(currentBB)
        val startId = if (programMethod.currentId == -1) -1 else programMethod.currentId + 1
        currentBB.insts.forEachIndexed { idx, programInst ->
            irParser.mapOpcode(programInst, programMethod, env, idx, ::setTraversalStrategy)
        }
        val endId = programMethod.currentId

        if (startId > endId || endId == -1) {
            currentBB.start = if (startId == -1) 0 else startId
            if (currentBB.successors.isNotEmpty()) {
                addEmptyBlockPlaceholder(programMethod, currentBB.id)
            }
            addEmptyJump(programMethod)
            currentBB.end = programMethod.currentId
        } else if (endId >= 0) {
            currentBB.start = if (startId == -1) 0 else startId
            addEmptyJump(programMethod)
            currentBB.end = programMethod.currentId
        }

        programMethod.idToBB[currentBB.id] = mapBasicBlock(currentBB)
    }

    private fun mapBasicBlock(bb: ProgramBasicBlock): PandaBasicBlock {
        return PandaBasicBlock(
            id = bb.id,
            successors = bb.successors.toSet(),
            predecessors = bb.predecessors.toSet(),
            _start = PandaInstRef(bb.start),
            _end = PandaInstRef(bb.end)
        )
    }

    private fun addEmptyJump(method: ProgramMethod) {
        val location = IRParser.locationFromOp(method = method)
        method.pushInst(PandaGotoInst(location).apply {
            this.setTarget(PandaInstRef(location.index + 1))
        })
    }

    private fun addEmptyBlockPlaceholder(method: ProgramMethod, bbId: Int) {
        val location = IRParser.locationFromOp(method = method)
        method.pushInst(PandaEmptyBBPlaceholderInst(location, bbId))
    }
}
