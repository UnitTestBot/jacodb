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

package org.jacodb.api.common.cfg

interface Graph<out Statement> : Iterable<Statement> {
    fun successors(node: @UnsafeVariance Statement): Set<Statement>
    fun predecessors(node: @UnsafeVariance Statement): Set<Statement>
}

interface ControlFlowGraph<out Statement> : Graph<Statement> {
    val instructions: List<Statement>
    val entries: List<Statement>
    val exits: List<Statement>

    override fun iterator(): Iterator<Statement> = instructions.iterator()
}

interface BytecodeGraph<out Statement> : ControlFlowGraph<Statement> {
    fun throwers(node: @UnsafeVariance Statement): Set<Statement>
    fun catchers(node: @UnsafeVariance Statement): Set<Statement>
}
