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

import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.cfg.JcBranchingInst
import org.utbot.jacodb.api.cfg.JcCatchInst
import org.utbot.jacodb.api.cfg.JcGraph
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.cfg.JcInstRef
import org.utbot.jacodb.api.cfg.JcInstVisitor
import org.utbot.jacodb.api.cfg.JcTerminatingInst
import org.utbot.jacodb.api.ext.isSubtypeOf

class JcGraphImpl(
    override val classpath: JcClasspath,
    override val instructions: List<JcInst>,
) : Iterable<JcInst>, JcGraph {
    private val indexMap = instructions.mapIndexed { index, jcInst -> jcInst to index }.toMap()

    private val predecessorMap = mutableMapOf<JcInst, MutableSet<JcInst>>()
    private val successorMap = mutableMapOf<JcInst, MutableSet<JcInst>>()

    private val throwPredecessors = mutableMapOf<JcCatchInst, MutableSet<JcInst>>()
    private val throwSuccessors = mutableMapOf<JcInst, MutableSet<JcCatchInst>>()
    private val _throwExits = mutableMapOf<JcClassType, MutableSet<JcInstRef>>()

    override val entry: JcInst get() = instructions.first()
    override val exits: List<JcInst> get() = instructions.filterIsInstance<JcTerminatingInst>()

    /**
     * returns a map of possible exceptions that may be thrown from this method
     * for each instruction of in the graph in determines possible thrown exceptions using
     * #JcExceptionResolver class
     */
    override val throwExits: Map<JcClassType, List<JcInst>> get() = _throwExits.mapValues { (_, refs) -> refs.map { inst(it) } }

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is JcTerminatingInst -> mutableSetOf()
                is JcBranchingInst -> inst.successors.map { inst(it) }.toMutableSet()
                else -> mutableSetOf(next(inst))
            }
            successorMap[inst] = successors

            for (successor in successors) {
                predecessorMap.getOrPut(successor, ::mutableSetOf) += inst
            }

            if (inst is JcCatchInst) {
                throwPredecessors[inst] = inst.throwers.map { inst(it) }.toMutableSet()
                inst.throwers.forEach {
                    throwSuccessors.getOrPut(inst(it), ::mutableSetOf).add(inst)
                }
            }
        }

        for (inst in instructions) {
            for (throwableType in inst.accept(JcExceptionResolver(classpath))) {
                if (!catchers(inst).any { throwableType.jcClass isSubtypeOf (it.throwable.type as JcClassType).jcClass }) {
                    _throwExits.getOrPut(throwableType, ::mutableSetOf) += ref(inst)
                }
            }
        }
    }

    override fun index(inst: JcInst) = indexMap.getOrDefault(inst, -1)

    override fun ref(inst: JcInst): JcInstRef = JcInstRef(index(inst))
    override fun inst(ref: JcInstRef): JcInst = instructions[ref.index]

    override fun previous(inst: JcInst): JcInst = instructions[ref(inst).index - 1]
    override fun next(inst: JcInst): JcInst = instructions[ref(inst).index + 1]

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun successors(inst: JcInst): Set<JcInst> = successorMap.getOrDefault(inst, emptySet())
    override fun predecessors(inst: JcInst): Set<JcInst> = predecessorMap.getOrDefault(inst, emptySet())

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JcCatchInst`
     */
    override fun throwers(inst: JcInst): Set<JcInst> = throwPredecessors.getOrDefault(inst, emptySet())
    override fun catchers(inst: JcInst): Set<JcCatchInst> = throwSuccessors.getOrDefault(inst, emptySet())

    override fun previous(inst: JcInstRef): JcInst = previous(inst(inst))
    override fun next(inst: JcInstRef): JcInst = next(inst(inst))

    override fun successors(inst: JcInstRef): Set<JcInst> = successors(inst(inst))
    override fun predecessors(inst: JcInstRef): Set<JcInst> = predecessors(inst(inst))

    override fun throwers(inst: JcInstRef): Set<JcInst> = throwers(inst(inst))
    override fun catchers(inst: JcInstRef): Set<JcCatchInst> = catchers(inst(inst))

    /**
     * get all the exceptions types that this instruction may throw and terminate
     * current method
     */
    override fun exceptionExits(inst: JcInst): Set<JcClassType> =
        inst.accept(JcExceptionResolver(classpath)).filter { it in _throwExits }.toSet()

    override fun exceptionExits(ref: JcInstRef): Set<JcClassType> = exceptionExits(inst(ref))

    override fun blockGraph(): JcBlockGraphImpl = JcBlockGraphImpl(this)

    override fun toString(): String = instructions.joinToString("\n")

    override fun iterator(): Iterator<JcInst> = instructions.iterator()
}


fun JcGraph.filter(visitor: JcInstVisitor<Boolean>) =
    JcGraphImpl(classpath, instructions.filter { it.accept(visitor) })

fun JcGraph.filterNot(visitor: JcInstVisitor<Boolean>) =
    JcGraphImpl(classpath, instructions.filterNot { it.accept(visitor) })

fun JcGraph.map(visitor: JcInstVisitor<JcInst>) =
    JcGraphImpl(classpath, instructions.map { it.accept(visitor) })

fun JcGraph.mapNotNull(visitor: JcInstVisitor<JcInst?>) =
    JcGraphImpl(classpath, instructions.mapNotNull { it.accept(visitor) })

fun JcGraph.flatMap(visitor: JcInstVisitor<Collection<JcInst>>) =
    JcGraphImpl(classpath, instructions.flatMap { it.accept(visitor) })
