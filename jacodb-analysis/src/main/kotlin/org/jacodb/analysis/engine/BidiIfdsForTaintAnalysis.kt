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

package org.jacodb.analysis.engine

import org.jacodb.analysis.analyzers.TaintNode
import org.jacodb.analysis.graph.reversed
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.ext.cfg.callExpr

class BidiIfdsForTaintAnalysis(
    val forward: IfdsUnitInstance,
    val backward: IfdsUnitInstance,
    graph: ApplicationGraph<JcMethod, JcInst>
): IfdsInstance {

    init {
        // In forward and backward analysis same function will have different entryPoints, so we have to change
        // `from` vertex of pathEdges properly at handover
        fun IfdsEdge.handoverPathEdgeTo(
            instance: IfdsUnitInstance,
            pred: JcInst?,
            updateActivation: Boolean,
            propZero: Boolean
        ) {
            val (u, v) = this
            val fact = (v.domainFact as? TaintNode) ?: return
            val newFact = if (updateActivation && fact.activation == null) fact.updateActivation(pred) else fact // TODO: think between pred and v.statement
            val newStatement = pred ?: v.statement
            graph.entryPoint(u.statement.location.method).forEach {
                instance.addNewPathEdge(
                    IfdsEdge(
                        IfdsVertex(it, u.domainFact),
                        IfdsVertex(newStatement, newFact)
                    )
                )
                if (propZero) {
                    // Propagating zero fact
                    instance.addNewPathEdge(
                        IfdsEdge(
                            IfdsVertex(it, u.domainFact),
                            IfdsVertex(newStatement, ZEROFact)
                        )
                    )
                }
            }
        }

        // Forward initiates backward analysis and waits until it finishes
        // Backward analysis does not initiate forward one, because it will run with updated queue after the backward finishes
        forward.addListener(object: IfdsInstanceListener {
            override fun onPropagate(e: IfdsEdge, pred: JcInst?, factIsNew: Boolean) {
                val fact = e.v.domainFact as? TaintNode ?: return
                if (fact.variable.isOnHeap && factIsNew) {
                    e.handoverPathEdgeTo(backward, pred, updateActivation = true, propZero = true)
                    backward.run()
                }
            }
        })

        backward.addListener(object: IfdsInstanceListener {
            override fun onPropagate(e: IfdsEdge, pred: JcInst?, factIsNew: Boolean) {
                val v = e.v
                val curInst = v.statement
                val fact = (v.domainFact as? TaintNode) ?: return
                var canBeKilled = false

                if (!fact.variable.isOnHeap) {
                    return
                }

                if (curInst is JcAssignInst && fact.variable.startsWith(curInst.lhv.toPath())) {
                    canBeKilled = true
                }

                curInst.callExpr?.let { callExpr ->
                    if (callExpr is JcInstanceCallExpr && fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                        canBeKilled = true
                    }
                    callExpr.args.forEach {
                        if (fact.variable.startsWith(it.toPathOrNull())) {
                            canBeKilled = true
                        }
                    }
                }
                if (canBeKilled) {
                    e.handoverPathEdgeTo(forward, pred, updateActivation = false, propZero = false)
                }
            }

            override fun onExitPoint(e: IfdsEdge) {
                val fact = e.v.domainFact as? TaintNode ?: return
                if (fact.variable.isOnHeap) {
                    e.handoverPathEdgeTo(forward, pred = null, updateActivation = false, propZero = false)
                }
            }
        })
    }

    override fun addStart(method: JcMethod) = forward.addStart(method)
    override fun addStartFact(method: JcMethod, fact: DomainFact) = forward.addStartFact(method, fact)

    override fun analyze(): Map<JcMethod, IfdsMethodSummary> = forward.analyze()

    companion object {
        fun createProvider(
            forwardAnalyzer: Analyzer,
            backwardAnalyzer: Analyzer,
        ) = IfdsInstanceProvider { graph, devirtualizer, context, unitResolver, unit ->
            val forward = IfdsUnitInstance(graph, forwardAnalyzer, devirtualizer, context, unitResolver, unit)
            val backward = IfdsUnitInstance(graph.reversed, backwardAnalyzer, devirtualizer, context, unitResolver, unit)
            BidiIfdsForTaintAnalysis(forward, backward, graph)
        }
    }
}