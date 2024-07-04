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

package org.jacodb.panda.dynamic.ets.graph

import org.jacodb.panda.dynamic.ets.base.EtsStmt
import java.util.ArrayDeque
import kotlin.LazyThreadSafetyMode.PUBLICATION

class EtsLoop(
    val graph: EtsCfg,
    val head: EtsStmt,
    val instructions: List<EtsStmt>,
) {
    val exits: Collection<EtsStmt> by lazy(PUBLICATION) {
        val result = hashSetOf<EtsStmt>()
        for (s in instructions) {
            graph.successors(s).forEach {
                if (!instructions.contains(it)) {
                    result.add(s)
                }
            }
        }
        result
    }

    val backJump: EtsStmt get() = instructions.last()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EtsLoop

        if (head != other.head) return false
        if (instructions != other.instructions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = head.hashCode()
        result = 31 * result + instructions.hashCode()
        return result
    }
}

val EtsCfg.loops: Set<EtsLoop>
    get() {
        val finder = findDominators()
        val loops = HashMap<EtsStmt, MutableList<EtsStmt>>()
        instructions.forEach { inst ->
            val dominators = finder.dominators(inst)

            val headers = arrayListOf<EtsStmt>()
            successors(inst).forEach {
                if (dominators.contains(it)) {
                    headers.add(it)
                }
            }
            headers.forEach { header ->
                val loopBody = loopBodyOf(header, inst)
                loops[header] = loops[header]?.union(loopBody) ?: loopBody
            }
        }
        return loops.map { (key, value) ->
            newLoop(key, value)
        }.toSet()
    }

private fun EtsCfg.newLoop(head: EtsStmt, loopStatements: MutableList<EtsStmt>): EtsLoop {
    // put header to the top
    loopStatements.remove(head)
    loopStatements.add(0, head)

    // last statement
    val backJump = loopStatements.last()
    // must branch back to the head
    assert(successors(backJump).contains(head))
    return EtsLoop(this, head = head, instructions = loopStatements)
}

private fun EtsCfg.loopBodyOf(header: EtsStmt, inst: EtsStmt): MutableList<EtsStmt> {
    val loopBody = arrayListOf(header)
    val stack = ArrayDeque<EtsStmt>().also {
        it.push(inst)
    }
    while (!stack.isEmpty()) {
        val next = stack.pop()
        if (!loopBody.contains(next)) {
            loopBody.add(0, next)
            predecessors(next).forEach { stack.push(it) }
        }
    }
    assert(inst === header && loopBody.size == 1 || loopBody[loopBody.size - 2] === inst)
    assert(loopBody[loopBody.size - 1] === header)
    return loopBody
}

private fun MutableList<EtsStmt>.union(another: List<EtsStmt>): MutableList<EtsStmt> = apply {
    addAll(another.filter { !contains(it) })
}
