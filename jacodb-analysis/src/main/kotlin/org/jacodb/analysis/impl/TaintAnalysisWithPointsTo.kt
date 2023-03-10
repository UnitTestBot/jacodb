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

package org.jacodb.analysis.impl

import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcInst

// TODO: rabochee nazvanie
// TODO: this should inherit from IFDSInstance or from some common interface (?)
class TaintAnalysisWithPointsTo(
    private val forward: IFDSInstance<JcMethod, JcInst, TaintNode>,
    private val backward: IFDSInstance<JcMethod, JcInst, TaintNode>
) {
    init {
        // In forward and backward analysis same function will have different entryPoints, so we have to change
        // `from` vertex of pathEdges properly at handover
        fun Edge<JcInst, TaintNode>.handoverPathEdgeTo(
            instance: IFDSInstance<JcMethod, JcInst, TaintNode>,
            pred: JcInst?,
            updateActivation: Boolean,
        ) {
            val (u, v) = this
            val newFact = if (updateActivation) v.domainFact.copy(activation = pred) else v.domainFact // TODO: think between pred and v.statement
            val newStatement = pred ?: v.statement
            instance.graph.entryPoint(u.statement.location.method).forEach {
                instance.propagate(
                    Edge(
                        Vertex(it, u.domainFact),
                        Vertex(newStatement, newFact)
                    )
                )
            }
        }

        // Forward initiates backward analysis and wait until it finishes
        // Backward analysis does not initiate forward one, because it will run with updated queue after the backward finishes
        forward.addListener(object: IFDSInstanceListener<JcInst, TaintNode> {
            override fun onPropagate(e: Edge<JcInst, TaintNode>, pred: JcInst?) {
                val v = e.v
                if (pred is JcAssignInst && v.domainFact.variable.value == pred.lhv && v.domainFact.variable.isOnHeap) {
                    e.handoverPathEdgeTo(backward, pred, updateActivation = true)
                    backward.run()
                }
            }
        })

        backward.addListener(object: IFDSInstanceListener<JcInst, TaintNode> {
            override fun onPropagate(e: Edge<JcInst, TaintNode>, pred: JcInst?) {
                val v = e.v
                val curInst = v.statement
                if (curInst is JcAssignInst && v.domainFact.variable.value == curInst.lhv) {
                    e.handoverPathEdgeTo(forward, pred, updateActivation = false)
                }
            }

            override fun onExitPoint(e: Edge<JcInst, TaintNode>) {
                e.handoverPathEdgeTo(forward, pred = null, updateActivation = false)
            }
        })
    }

    fun addStart(startMethod: JcMethod) = forward.addStart(startMethod)
    fun run() = forward.run()
    fun collectResults() = forward.collectResults()
}