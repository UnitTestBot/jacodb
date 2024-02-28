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

package org.jacodb.analysis.unused

import org.jacodb.analysis.ifds.Analyzer
import org.jacodb.analysis.ifds.Edge
import org.jacodb.analysis.ifds.Vertex
import org.jacodb.analysis.util.Traits
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.common.cfg.CommonInst

context(Traits<Method, Statement>)
class UnusedVariableAnalyzer<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : Analyzer<UnusedVariableDomainFact, UnusedVariableEvent<Method, Statement>, Method, Statement>
    where Method : CommonMethod<Method, Statement>,
          Statement : CommonInst<Method, Statement> {

    override val flowFunctions: UnusedVariableFlowFunctions<Method, Statement> by lazy {
        UnusedVariableFlowFunctions(graph)
    }

    private fun isExitPoint(statement: Statement): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(
        edge: Edge<UnusedVariableDomainFact, Method, Statement>,
    ): List<UnusedVariableEvent<Method, Statement>> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }
    }

    override fun handleCrossUnitCall(
        caller: Vertex<UnusedVariableDomainFact, Method, Statement>,
        callee: Vertex<UnusedVariableDomainFact, Method, Statement>,
    ): List<UnusedVariableEvent<Method, Statement>> {
        return emptyList()
    }
}
