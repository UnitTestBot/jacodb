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

package org.jacodb.analysis.ifds.unused

import org.jacodb.analysis.ifds.common.JcBaseAnalyzer
import org.jacodb.analysis.ifds.domain.Edge
import org.jacodb.analysis.ifds.domain.RunnerId
import org.jacodb.analysis.ifds.messages.NewSummaryEdge
import org.jacodb.analysis.ifds.messages.RunnerMessage
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst

class UnusedVariableAnalyzer(
    selfRunnerId: RunnerId,
    graph: JcApplicationGraph,
) : JcBaseAnalyzer<UnusedVariableDomainFact>(
    selfRunnerId,
    graph,
) {
    override val flowFunctions by lazy {
        UnusedVariableFlowFunctions(graph.classpath)
    }

    override fun MutableList<RunnerMessage>.onNewEdge(newEdge: Edge<JcInst, UnusedVariableDomainFact>) {
        if (isExitPoint(newEdge.to.statement)) {
            add(NewSummaryEdge(selfRunnerId, newEdge))
        }
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }
}
