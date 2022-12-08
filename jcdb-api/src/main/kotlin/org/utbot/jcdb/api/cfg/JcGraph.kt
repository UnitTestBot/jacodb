package org.utbot.jcdb.api.cfg

import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcClasspath

interface JcGraph : Iterable<JcInst> {
    val classpath: JcClasspath
    val instructions: List<JcInst>
    val entry: JcInst
    val exits: List<JcInst>

    /**
     * returns a map of possible exceptions that may be thrown from this method
     * for each instruction of in the graph in determines possible thrown exceptions using
     * #JcExceptionResolver class
     */
    val throwExits: Map<JcClassType, List<JcInst>>

    fun index(inst: JcInst): Int
    fun ref(inst: JcInst): JcInstRef
    fun inst(ref: JcInstRef): JcInst
    fun previous(inst: JcInst): JcInst
    fun next(inst: JcInst): JcInst

    /**
     * `successors` and `predecessors` represent normal control flow
     */
    fun successors(inst: JcInst): Set<JcInst>
    fun predecessors(inst: JcInst): Set<JcInst>

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JcCatchInst`
     */
    fun throwers(inst: JcInst): Set<JcInst>
    fun catchers(inst: JcInst): Set<JcCatchInst>
    fun previous(inst: JcInstRef): JcInst
    fun next(inst: JcInstRef): JcInst
    fun successors(inst: JcInstRef): Set<JcInst>
    fun predecessors(inst: JcInstRef): Set<JcInst>
    fun throwers(inst: JcInstRef): Set<JcInst>
    fun catchers(inst: JcInstRef): Set<JcCatchInst>

    /**
     * get all the exceptions types that this instruction may throw and terminate
     * current method
     */
    fun exceptionExits(inst: JcInst): Set<JcClassType>
    fun exceptionExits(ref: JcInstRef): Set<JcClassType>
    fun blockGraph(): JcBlockGraph
}