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
import kotlinx.coroutines.launch
import org.jacodb.analysis.graph.reversed
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

interface IfdsUnitRunner<UnitType> {
    val unit: UnitType
    val job: Job

    suspend fun submitNewEdge(edge: IfdsEdge)
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
    private inner class BidiIfdsUnitRunner<UnitType>(
        private val graph: JcApplicationGraph,
        private val manager: IfdsUnitManager<UnitType>,
        private val unitResolver: UnitResolver<UnitType>,
        override val unit: UnitType,
        startMethods: List<JcMethod>,
        scope: CoroutineScope
    ) : IfdsUnitRunner<UnitType> {

        @Volatile
        private var forwardQueueIsEmpty: Boolean = false

        @Volatile
        private var backwardQueueIsEmpty: Boolean = false

        private val forwardManager: IfdsUnitManager<UnitType> = object : IfdsUnitManager<UnitType> by manager {

            override suspend fun handleEvent(event: IfdsUnitRunnerEvent, runner: IfdsUnitRunner<UnitType>) {
                when (event) {
                    is EdgeForOtherRunnerQuery -> {
                        if (unitResolver.resolve(event.edge.method) == unit) {
                            backwardRunner?.submitNewEdge(event.edge)
                        } else {
                            manager.handleEvent(event, this@BidiIfdsUnitRunner)
                        }
                    }
                    is NewSummaryFact -> {
                        manager.handleEvent(event, this@BidiIfdsUnitRunner)
                    }
                    is QueueEmptinessChanged -> {
                        forwardQueueIsEmpty = event.isEmpty
                        val newEvent = QueueEmptinessChanged(backwardQueueIsEmpty && forwardQueueIsEmpty)
                        manager.handleEvent(newEvent, this@BidiIfdsUnitRunner)
                    }
                    is SubscriptionForSummaries -> {
                        manager.handleEvent(event, this@BidiIfdsUnitRunner)
                    }
                }
            }
        }

        private val backwardManager: IfdsUnitManager<UnitType> = object : IfdsUnitManager<UnitType> {
            override suspend fun handleEvent(event: IfdsUnitRunnerEvent, runner: IfdsUnitRunner<UnitType>) {
                when (event) {
                    is EdgeForOtherRunnerQuery -> {
                        if (unitResolver.resolve(event.edge.method) == unit) {
                            forwardRunner?.submitNewEdge(event.edge)
                        }
                    }
                    is NewSummaryFact -> {
                        manager.handleEvent(event, this@BidiIfdsUnitRunner)
                    }
                    is QueueEmptinessChanged -> {
                        backwardQueueIsEmpty = event.isEmpty
                        if (!isParallel && event.isEmpty) {
                            runner.job.cancel()
                        }
                        val newEvent =
                            QueueEmptinessChanged(backwardQueueIsEmpty && forwardQueueIsEmpty)
                        manager.handleEvent(newEvent, this@BidiIfdsUnitRunner)
                    }

                    is SubscriptionForSummaries -> {}
                }
            }
        }

        private var backwardRunner: IfdsUnitRunner<UnitType>? = null

        private var forwardRunner: IfdsUnitRunner<UnitType>? = null

        override suspend fun submitNewEdge(edge: IfdsEdge) {
            forwardRunner?.submitNewEdge(edge)
        }

        override val job: Job = scope.launch(start = CoroutineStart.LAZY) {
            backwardRunner = backwardRunnerFactory
                .newRunner(graph.reversed, backwardManager, unitResolver, unit, startMethods, this)

            forwardRunner = forwardRunnerFactory
                .newRunner(graph, forwardManager, unitResolver, unit, startMethods, this)

            if (isParallel) {
                backwardRunner!!.job.start()
                forwardRunner!!.job.start()
            }

            backwardRunner!!.job.join()
            forwardRunner!!.job.join()
        }
    }

    override fun <UnitType> newRunner(
        graph: JcApplicationGraph,
        manager: IfdsUnitManager<UnitType>,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
        scope: CoroutineScope,
    ): IfdsUnitRunner<UnitType> = BidiIfdsUnitRunner(graph, manager, unitResolver, unit, startMethods, scope)
}