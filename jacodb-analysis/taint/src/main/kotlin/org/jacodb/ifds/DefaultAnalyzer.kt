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

package org.jacodb.ifds

import org.jacodb.ifds.domain.Analyzer
import org.jacodb.ifds.domain.Edge
import org.jacodb.ifds.domain.FlowFunctions
import org.jacodb.ifds.domain.FlowScope
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.messages.AnalyzerMessage
import org.jacodb.ifds.messages.RunnerMessage
import org.jacodb.ifds.messages.EdgeMessage
import org.jacodb.ifds.messages.NotificationOnStart
import org.jacodb.ifds.messages.ResolvedCall
import org.jacodb.ifds.messages.UnresolvedCall
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr

typealias TaintFlowScope<Fact> = FlowScope<JcInst, Fact>

class DefaultAnalyzer<Fact>(
    private val applicationGraph: JcApplicationGraph,
    private val flowFunctions: FlowFunctions<JcInst, Fact>,
    private val runnerId: RunnerId,
) : Analyzer<JcInst, Fact> {
    override fun step(message: AnalyzerMessage<JcInst, Fact>): Collection<RunnerMessage> = buildList {
        when (message) {
            is EdgeMessage<JcInst, Fact> -> {
                val scope = TaintFlowScope(message.edge, this)
                scope.processEdge(message.edge)
            }

            is ResolvedCall<JcInst, Fact, *> -> {
                val scope = TaintFlowScope(message.edge, this)
                @Suppress("UNCHECKED_CAST")
                scope.processResolvedCall(message as ResolvedCall<JcInst, Fact, JcMethod>)
            }

            is NotificationOnStart<JcInst, Fact> -> {
                val scope = TaintFlowScope(message.edge, this)
                scope.processNotificationOnStart(message)
            }

            else -> {
                error("Unexpected message: $message")
            }
        }
    }

    private fun TaintFlowScope<Fact>.processEdge(edge: Edge<JcInst, Fact>) {
        val toStmt = edge.to.statement
        val method = applicationGraph.methodOf(toStmt)

        val callExpr = toStmt.callExpr
        val isExit = toStmt in applicationGraph.exitPoints(method)

        when {
            callExpr != null -> processCall(edge)
            !isExit -> processSequent(edge)
        }
    }

    private fun TaintFlowScope<Fact>.processCall(edge: Edge<JcInst, Fact>) {
        val callMessage = UnresolvedCall(runnerId, edge)
        add(callMessage)

        val successors = applicationGraph.successors(edge.to.statement)

        flowFunctions.run {
            for (successor in successors) {
                callToReturn(successor)
            }
        }
    }

    private fun TaintFlowScope<Fact>.processSequent(edge: Edge<JcInst, Fact>) {
        val successors = applicationGraph.successors(edge.to.statement)

        flowFunctions.run {
            for (successor in successors) {
                sequent(successor)
            }
        }
    }


    private fun TaintFlowScope<Fact>.processResolvedCall(
        resolvedCall: ResolvedCall<JcInst, Fact, JcMethod>,
    ) {
        val entryPoints = applicationGraph.entryPoints(resolvedCall.method)

        flowFunctions.run {
            for (entryPoint in entryPoints) {
                callToStart(entryPoint)
            }
        }
    }


    private fun TaintFlowScope<Fact>.processNotificationOnStart(message: NotificationOnStart<JcInst, Fact>) {
        val successors = applicationGraph.successors(message.data.to.statement)

        flowFunctions.run {
            for (successor in successors) {
                exitToReturnSite(message.data, successor)
            }
        }
    }
}
