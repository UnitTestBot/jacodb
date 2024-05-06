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

import org.jacodb.analysis.ifds.Analyzer
import org.jacodb.api.cfg.JcInst
import org.jacodb.ifds.domain.Edge
import org.jacodb.ifds.domain.FlowFunctions
import org.jacodb.ifds.domain.FlowScope
import org.jacodb.ifds.domain.Reason
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.domain.Vertex
import org.jacodb.ifds.messages.NewEdge
import org.jacodb.ifds.messages.SubscriptionOnStart

typealias JcEventProcessor<Fact, Event> = FlowScope<JcInst, Fact>.(Event) -> Unit

class JcFlowFunctionsAdapter<Fact, Event>(
    private val runnerId: RunnerId,
    private val jcAnalyzer: Analyzer<Fact, Event>,
    private val jcEventProcessor: JcEventProcessor<Fact, Event>,
) : FlowFunctions<JcInst, Fact> {
    private val jcFlowFunctions = jcAnalyzer.flowFunctions

    override fun FlowScope<JcInst, Fact>.sequent(next: JcInst) =
        jcFlowFunctions
            .obtainSequentFlowFunction(edge.to.stmt, next)
            .compute(edge.to.fact)
            .forEach { newFact ->
                val newEdge = Edge(edge.from, Vertex(next, newFact))
                processNewEdge(runnerId, newEdge, Reason.Sequent(edge))
            }

    override fun FlowScope<JcInst, Fact>.callToReturn(returnSite: JcInst) =
        jcFlowFunctions
            .obtainCallToReturnSiteFlowFunction(edge.to.stmt, returnSite)
            .compute(edge.to.fact)
            .forEach { newFact ->
                val newEdge = Edge(edge.from, Vertex(returnSite, newFact))
                processNewEdge(runnerId, newEdge, Reason.CallToReturn(edge))
            }

    override fun FlowScope<JcInst, Fact>.callToStart(calleeStart: JcInst) =
        jcFlowFunctions
            .obtainCallToStartFlowFunction(edge.to.stmt, calleeStart)
            .compute(edge.to.fact)
            .forEach { newFact ->
                val vertex = Vertex(calleeStart, newFact)

                val subscription = SubscriptionOnStart(runnerId, vertex, runnerId, edge)
                add(subscription)

                val newEdge = Edge(vertex, vertex)
                processNewEdge(runnerId, newEdge, Reason.CallToStart(edge))
            }

    override fun FlowScope<JcInst, Fact>.exitToReturnSite(
        callerEdge: Edge<JcInst, Fact>,
        returnSite: JcInst,
    ) = jcFlowFunctions
        .obtainExitToReturnSiteFlowFunction(callerEdge.to.stmt, returnSite, edge.to.stmt)
        .compute(edge.to.fact)
        .forEach { newFact ->
            val newEdge = Edge(callerEdge.from, Vertex(returnSite, newFact))
            processNewEdge(runnerId, newEdge, Reason.ExitToReturnSite(callerEdge, edge))
        }

    private fun FlowScope<JcInst, Fact>.processNewEdge(
        runnerId: RunnerId,
        newEdge: Edge<JcInst, Fact>,
        reason: Reason<JcInst, Fact>,
    ) {
        val edge = NewEdge(runnerId, newEdge, reason)
        add(edge)

        val jcEvents = jcAnalyzer.handleNewEdge(newEdge.toJcEdge())
        for (event in jcEvents) {
            jcEventProcessor(event)
        }
    }
}

private fun <Fact> Vertex<JcInst, Fact>.toJcVertex() = org.jacodb.analysis.ifds.Vertex(stmt, fact)
private fun <Fact> Edge<JcInst, Fact>.toJcEdge() = org.jacodb.analysis.ifds.Edge(from.toJcVertex(), to.toJcVertex())

fun <Fact> org.jacodb.analysis.ifds.Vertex<Fact>.toVertex() = Vertex(statement, fact)
fun <Fact> org.jacodb.analysis.ifds.Edge<Fact>.toEdge() = Edge(from.toVertex(), to.toVertex())
