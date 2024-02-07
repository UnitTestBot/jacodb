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

package org.jacodb.panda.staticvm

import org.jacodb.api.core.analysis.ApplicationGraph

class PandaApplicationGraph(
    private val project: PandaProject
) : ApplicationGraph<PandaMethod, PandaInst> {
    private val callersMap = project.methods
        .flatMap { it.flowGraph().instructions }
        .flatMap { inst -> callees(inst).map { it to inst } }
        .groupBy(Pair<PandaMethod, PandaInst>::first, Pair<PandaMethod, PandaInst>::second)

    override fun predecessors(node: PandaInst): Sequence<PandaInst> =
        node.location.method.flowGraph().predecessors(node).asSequence()
    override fun successors(node: PandaInst): Sequence<PandaInst> =
        node.location.method.flowGraph().predecessors(node).asSequence()

    override fun callees(node: PandaInst): Sequence<PandaMethod> = when (node) {
        is PandaAssignInst -> when (val expr = node.rhv) {
            is PandaStaticCallExpr -> sequenceOf(expr.method)
            is PandaVirtualCallExpr -> sequenceOf(expr.method)
            else -> emptySequence()
        }
        else -> emptySequence()
    }

    override fun callers(method: PandaMethod): Sequence<PandaInst>
        = callersMap[method]?.asSequence() ?: emptySequence()

    override fun entryPoint(method: PandaMethod): Sequence<PandaInst> =
        method.flowGraph().entries.asSequence()

    override fun exitPoints(method: PandaMethod): Sequence<PandaInst> =
        method.flowGraph().exits.asSequence()

    override fun methodOf(node: PandaInst): PandaMethod =
        node.location.method
}