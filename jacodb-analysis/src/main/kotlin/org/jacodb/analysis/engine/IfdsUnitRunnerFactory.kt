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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.jacodb.analysis.graph.reversed
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

interface IfdsUnitRunner<UnitType> {
    val unit: UnitType
    val job: Job

    fun submitNewEdge(edge: IfdsEdge)
}

/**
 * Allows to run analysis for any given unit.
 * Note that multiple calls of [newRunner] can be made concurrently for one instance of runner,
 * so the implementations should be thread-safe.
 */
interface IfdsUnitRunnerFactory {
    /**
     * Launches some analysis for given [unit], using given [startMethods] as entry points.
     * All start methods should belong to the [unit].
     *
     * @param graph provides supergraph for application (including methods, belonging to other units)
     *
     * @param manager can be used as a knowledge base about other units.
     * Also, anything observed during analysis may be saved to [manager] so that it can be seen by other units.
     *
     * @param unitResolver may be used to get units of methods observed during analysis.
     */
    fun <UnitType> newRunner(
        graph: JcApplicationGraph,
        manager: IfdsUnitManager<UnitType>,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
        scope: CoroutineScope
    ) : IfdsUnitRunner<UnitType>
}

/**
 * A composite [IfdsUnitRunnerFactory] that launches two runners (backward and forward) in parallel
 * on the same unit with the same startMethods.
 *
 * [backwardRunner] is launched on the reversed application graph,
 * while [forwardRunner] is launched on the direct graph.
 */
class BidiIfdsUnitRunnerFactory(
    private val forwardRunnerFactory: IfdsUnitRunnerFactory,
    private val backwardRunnerFactory: IfdsUnitRunnerFactory,
    private val isParallel: Boolean = true
) : IfdsUnitRunnerFactory {
    override fun <UnitType> newRunner(
        graph: JcApplicationGraph,
        manager: IfdsUnitManager<UnitType>,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
        scope: CoroutineScope,
    ) = object : IfdsUnitRunner<UnitType> {
        private val thisRef = this

        private val forwardManager: IfdsUnitManager<UnitType> = object : IfdsUnitManager<UnitType> by manager {

            override fun addEdgeForOtherRunner(edge: IfdsEdge) {
                if (unitResolver.resolve(edge.method) == unit) {
                    backwardRunner?.submitNewEdge(edge)
                } else {
                    manager.addEdgeForOtherRunner(edge)
                }
            }

            override fun updateQueueStatus(isEmpty: Boolean, runner: IfdsUnitRunner<UnitType>) {
                manager.updateQueueStatus(isEmpty, thisRef)
            }
        }

        private val backwardManager: IfdsUnitManager<UnitType> = object : IfdsUnitManager<UnitType> by manager {

            override fun addEdgeForOtherRunner(edge: IfdsEdge) {
                if (unitResolver.resolve(edge.method) == unit) {
                    forwardRunner?.submitNewEdge(edge)
                } else {
                    manager.addEdgeForOtherRunner(edge)
                }
            }

            override fun subscribeForSummaryEdgesOf(
                method: JcMethod,
                runner: IfdsUnitRunner<UnitType>
            ): Flow<IfdsEdge> = emptyFlow()

            override fun updateQueueStatus(isEmpty: Boolean, runner: IfdsUnitRunner<UnitType>) {
                if (!isParallel) {
                    runner.job.cancel()
                }
            }
        }

        private var backwardRunner: IfdsUnitRunner<UnitType>? = null

        private var forwardRunner: IfdsUnitRunner<UnitType>? = null

        override fun submitNewEdge(edge: IfdsEdge) {
            forwardRunner?.submitNewEdge(edge)
        }

        override val unit: UnitType = unit

        override val job: Job = scope.launch(start = CoroutineStart.LAZY) {
            backwardRunner = backwardRunnerFactory.newRunner(
                graph.reversed,
                backwardManager,
                unitResolver,
                unit,
                startMethods,
                this
            )

            forwardRunner = forwardRunnerFactory.newRunner(graph, forwardManager, unitResolver, unit, startMethods, this)

            if (isParallel) {
                backwardRunner!!.job.start()
                forwardRunner!!.job.start()
            }

            backwardRunner!!.job.join()
            forwardRunner!!.job.join()
        }
    }
}