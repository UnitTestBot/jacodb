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

package org.jacodb.ets.utils

import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCaughtExceptionRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsTrap

object TrapUtils {
    /** True when [block] is listed among trap's try blocks. */
    fun isBlockInTry(trap: EtsTrap, block: BasicBlock): Boolean =
        trap.tryBlocks.any { it.id == block.id }

    /** First handler block of the trap, or null. */
    fun handlerEntry(trap: EtsTrap): BasicBlock? = trap.catchBlocks.firstOrNull()

    /** Traps that cover [block], innermost first (smaller try-block set = inner). */
    fun trapsForBlock(traps: List<EtsTrap>, block: BasicBlock): List<EtsTrap> =
        traps.filter { isBlockInTry(it, block) }.sortedBy { it.tryBlocks.size }

    /** Innermost trap covering [block], or null if none. */
    fun findInnermostTrap(traps: List<EtsTrap>, block: BasicBlock): EtsTrap? =
        trapsForBlock(traps, block).firstOrNull()

    /** For each trap, count how many other traps strictly enclose it. */
    fun nestingDepth(traps: List<EtsTrap>): Map<EtsTrap, Int> {
        fun containsAll(a: EtsTrap, b: EtsTrap): Boolean {
            val aIds = a.tryBlocks.map { it.id }.toSet()
            val bIds = b.tryBlocks.map { it.id }.toSet()
            return aIds.containsAll(bIds)
        }

        return traps.associateWith { t -> traps.count { other -> other !== t && containsAll(other, t) } }
    }

    /** Map block id -> list of traps covering it (inner-first). */
    fun mapBlockToTraps(traps: List<EtsTrap>): Map<Int, List<EtsTrap>> {
        val byBlock = mutableMapOf<Int, MutableList<EtsTrap>>()
        for (t in traps) {
            for (b in t.tryBlocks) {
                byBlock.computeIfAbsent(b.id) { mutableListOf() }.add(t)
            }
        }
        return byBlock.mapValues { (_, list) -> list.sortedBy { it.tryBlocks.size } }
    }

    private fun sameBlockSet(
        a: List<BasicBlock>,
        b: List<BasicBlock>,
    ): Boolean {
        val aIds = a.mapTo(HashSet()) { it.id }
        val bIds = b.mapTo(HashSet()) { it.id }
        return aIds == bIds
    }

    enum class HandlerKind { CATCH, COPIED_FINALLY, UNKNOWN }

    /**
     * Decide whether [trap]'s handler is a copied finally or a regular catch.
     *
     * @param allTraps list of all traps in the method (used to detect "handler is try-region of another trap" -> catch).
     * @param hasInjectedExceptionAssign predicate to detect the builder-inserted "caught exception" assignment inside a block.
     * Default implementation checks if first statement is an assignment from EtsCaughtExceptionRef.
     */
    fun classifyHandler(
        trap: EtsTrap,
        allTraps: List<EtsTrap>,
        hasInjectedExceptionAssign: (BasicBlock) -> Boolean = { block ->
            val first = block.statements.firstOrNull()
            first is EtsAssignStmt && first.lhv is EtsLocal && first.rhv is EtsCaughtExceptionRef
        },
    ): HandlerKind {
        // 1) if handler blocks are the try-blocks of some trap -> it's a catch region
        if (allTraps.any { other -> sameBlockSet(other.tryBlocks, trap.catchBlocks) }) {
            return HandlerKind.CATCH
        }

        // 2) injected assignment heuristic (best signal for copied-finally)
        if (trap.catchBlocks.any { hasInjectedExceptionAssign(it) }) return HandlerKind.COPIED_FINALLY

        // 3) try to detect "rethrow of the injected-local" pattern:
        //    find left local name of injected-assign if any, then search for throws of that name in handler blocks.
        val injectedLeftNames = trap.catchBlocks.mapNotNull { block ->
            val first = block.statements.firstOrNull()
            if (first is EtsAssignStmt && first.lhv is EtsLocal && first.rhv is EtsCaughtExceptionRef) {
                first.lhv.name
            } else {
                null
            }
        }.toSet()

        if (injectedLeftNames.isNotEmpty()) {
            val anyRethrow = trap.catchBlocks.any { block ->
                block.statements.any { stmt ->
                    if (stmt is EtsThrowStmt) {
                        injectedLeftNames.contains(stmt.exception.name)
                    } else {
                        false
                    }
                }
            }
            if (anyRethrow) return HandlerKind.COPIED_FINALLY
        }

        // 4) fallback: unknown (prefer interpreting as catch for safety)
        return HandlerKind.UNKNOWN
    }
}
