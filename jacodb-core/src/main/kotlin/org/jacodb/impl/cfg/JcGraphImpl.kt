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

import kotlinx.collections.immutable.toPersistentSet
import org.jacodb.api.JcClassType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcBranchingInst
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstRef
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.api.cfg.JcTerminatingInst
import org.jacodb.api.ext.isSubClassOf
import java.util.Collections.singleton

class JcGraphImpl(
    override val method: JcMethod,
    override val instructions: List<JcInst>,
) : Iterable<JcInst>, JcGraph {

    override val classpath: JcClasspath get() = method.enclosingClass.classpath

    private val predecessorMap = hashMapOf<JcInst, Set<JcInst>>()
    private val successorMap = hashMapOf<JcInst, Set<JcInst>>()

    private val throwPredecessors = hashMapOf<JcCatchInst, Set<JcInst>>()
    private val throwSuccessors = hashMapOf<JcInst, Set<JcCatchInst>>()
    private val _throwExits = hashMapOf<JcClassType, Set<JcInstRef>>()

    private val exceptionResolver = JcExceptionResolver(classpath)

    override val entry: JcInst get() = instructions.first()
    override val exits: List<JcInst> get() = instructions.filterIsInstance<JcTerminatingInst>()

    /**
     * returns a map of possible exceptions that may be thrown from this method
     * for each instruction of in the graph in determines possible thrown exceptions using
     * #JcExceptionResolver class
     */
    override val throwExits: Map<JcClassType, List<JcInst>>
        get() = _throwExits.mapValues { (_, refs) ->
            refs.map { instructions[it.index] }
        }

    init {
        for (inst in instructions) {
            val successors = when (inst) {
                is JcTerminatingInst -> emptySet()
                is JcBranchingInst -> inst.successors.map { instructions[it.index] }.toSet()
                else -> setOf(next(inst))
            }
            successorMap[inst] = successors

            for (successor in successors) {
                predecessorMap.add(successor, inst)
            }

            if (inst is JcCatchInst) {
                throwPredecessors[inst] = inst.throwers.map { instructions[it.index] }.toPersistentSet()
                inst.throwers.forEach {
                    throwSuccessors.add(inst(it), inst)
                }
            }
        }

        for (inst in instructions) {
            for (throwableType in inst.accept(exceptionResolver)) {
                if (!catchers(inst).any { throwableType.jcClass isSubClassOf (it.throwable.type as JcClassType).jcClass }) {
                    _throwExits.add(throwableType, ref(inst))
                }
            }
        }
    }

    override fun index(inst: JcInst): Int {
        if (instructions.contains(inst)) {
            return inst.location.index
        }
        return -1
    }

    override fun ref(inst: JcInst): JcInstRef = JcInstRef(index(inst))
    override fun inst(ref: JcInstRef): JcInst = instructions[ref.index]

    override fun previous(inst: JcInst): JcInst = instructions[ref(inst).index - 1]
    override fun next(inst: JcInst): JcInst = instructions[ref(inst).index + 1]

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    override fun successors(node: JcInst): Set<JcInst> = successorMap.getOrDefault(node, emptySet())
    override fun predecessors(node: JcInst): Set<JcInst> = predecessorMap.getOrDefault(node, emptySet())

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JcCatchInst`
     */
    override fun throwers(node: JcInst): Set<JcInst> = throwPredecessors.getOrDefault(node, emptySet())
    override fun catchers(node: JcInst): Set<JcCatchInst> = throwSuccessors.getOrDefault(node, emptySet())

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
        inst.accept(exceptionResolver).filter { it in _throwExits }.toSet()

    override fun exceptionExits(ref: JcInstRef): Set<JcClassType> = exceptionExits(inst(ref))

    override fun blockGraph(): JcBlockGraphImpl = JcBlockGraphImpl(this)

    override fun toString(): String = instructions.joinToString("\n")

    override fun iterator(): Iterator<JcInst> = instructions.iterator()

    private fun <KEY, VALUE> MutableMap<KEY, Set<VALUE>>.add(key: KEY, value: VALUE) {
        val current = this[key]
        if (current == null) {
            this[key] = singleton(value)
        } else {
            this[key] = current + value
        }
    }
}

fun JcGraph.filter(visitor: JcInstVisitor<Boolean>) =
    JcGraphImpl(method, instructions.filter { it.accept(visitor) })

fun JcGraph.filterNot(visitor: JcInstVisitor<Boolean>) =
    JcGraphImpl(method, instructions.filterNot { it.accept(visitor) })

fun JcGraph.map(visitor: JcInstVisitor<JcInst>) =
    JcGraphImpl(method, instructions.map { it.accept(visitor) })

fun JcGraph.mapNotNull(visitor: JcInstVisitor<JcInst?>) =
    JcGraphImpl(method, instructions.mapNotNull { it.accept(visitor) })

fun JcGraph.flatMap(visitor: JcInstVisitor<Collection<JcInst>>) =
    JcGraphImpl(method, instructions.flatMap { it.accept(visitor) })
