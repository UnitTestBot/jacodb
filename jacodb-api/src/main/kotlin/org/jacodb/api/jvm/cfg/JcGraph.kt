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

package org.jacodb.api.jvm.cfg

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcProject

interface JcGraph : JcBytecodeGraph<JcInst> {

    val method: JcMethod
    val classpath: JcProject
    val instructions: List<JcInst>
    val entry: JcInst
    override val exits: List<JcInst>
    override val entries: List<JcInst>
        get() = if (instructions.isEmpty()) listOf() else listOf(entry)

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
    override fun successors(node: JcInst): Set<JcInst>
    override fun predecessors(node: JcInst): Set<JcInst>

    /**
     * `throwers` and `catchers` represent control flow when an exception occurs
     * `throwers` returns an empty set for every instruction except `JcCatchInst`
     */
    override fun throwers(node: JcInst): Set<JcInst>
    override fun catchers(node: JcInst): Set<JcCatchInst>
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