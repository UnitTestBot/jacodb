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

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import org.jacodb.analysis.graph.reversed
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

/**
 * This factory produces composite runners. Each of them launches two runners (backward and forward)
 * on the same unit with the same startMethods.
 *
 * Backward runner is launched on the reversed application graph,
 * while forward runner is launched on the direct graph.
 *
 * Both runners will be given their own managers with the following policy:
 * - all [NewSummaryFact] events are delegated to the outer manager
 * - [EdgeForOtherRunnerQuery] events are submitted to the other runner if the corresponding edge
 * belongs to the same unit, otherwise they are transmitted to the outer manager (for forward runner)
 * or ignored (for backward runner)
 * - Queue is thought to be empty when queues of both forward and backward runners are empty.
 * The [QueueEmptinessChanged] event is sent to outer manager correspondingly.
 * - [SubscriptionForSummaryEdges] event is delegated to the outer manager for forward runner and
 * is ignored for backward runner
 *
 * @param forwardRunnerFactory a factory that produces forward runner for each [newRunner] call
 * @param backwardRunnerFactory a factory that produces backward runner for each [newRunner] call
 * @param isParallel if true, the produced composite runner will launch backward and forward runners in parallel.
 * Otherwise, the backward runner will be executed first, and after it, the forward runner will be executed.
 */
class BidiIfdsUnitRunnerFactory(
    private val forwardRunnerFactory: IfdsUnitRunnerFactory,
    private val backwardRunnerFactory: IfdsUnitRunnerFactory,
    private val isParallel: Boolean = true,
) : IfdsUnitRunnerFactory {
    private inner class BidiIfdsUnitRunner<UnitType>(
        graph: JcApplicationGraph,
        private val manager: IfdsUnitManager<UnitType>,
        private val unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
    ) : AbstractIfdsUnitRunner<UnitType>(unit) {

        @Volatile
        private var forwardQueueIsEmpty: Boolean = false

        @Volatile
        private var backwardQueueIsEmpty: Boolean = false

        private val forwardManager: IfdsUnitManager<UnitType> = object : IfdsUnitManager<UnitType> by manager {

            override suspend fun handleEvent(event: IfdsUnitRunnerEvent, runner: IfdsUnitRunner<UnitType>) {
                when (event) {
                    is EdgeForOtherRunnerQuery -> {
                        if (unitResolver.resolve(event.edge.method) == unit) {
                            backwardRunner.submitNewEdge(event.edge)
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

                    is SubscriptionForSummaryEdges -> {
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
                            forwardRunner.submitNewEdge(event.edge)
                        }
                    }

                    is NewSummaryFact -> {
                        manager.handleEvent(event, this@BidiIfdsUnitRunner)
                    }

                    is QueueEmptinessChanged -> {
                        backwardQueueIsEmpty = event.isEmpty
                        if (!isParallel && event.isEmpty) {
                            runner.job?.cancel() ?: error("Runner job is not instantiated")
                        }
                        val newEvent =
                            QueueEmptinessChanged(backwardQueueIsEmpty && forwardQueueIsEmpty)
                        manager.handleEvent(newEvent, this@BidiIfdsUnitRunner)
                    }

                    is SubscriptionForSummaryEdges -> {}
                }
            }
        }

        private val backwardRunner: IfdsUnitRunner<UnitType> = backwardRunnerFactory
            .newRunner(graph.reversed, backwardManager, unitResolver, unit, startMethods)

        private val forwardRunner: IfdsUnitRunner<UnitType> = forwardRunnerFactory
            .newRunner(graph, forwardManager, unitResolver, unit, startMethods)

        override suspend fun submitNewEdge(edge: IfdsEdge) {
            forwardRunner.submitNewEdge(edge)
        }

        override suspend fun run() = coroutineScope {
            val backwardRunnerJob: Job = backwardRunner.launchIn(this)
            val forwardRunnerJob: Job

            if (isParallel) {
                forwardRunnerJob = forwardRunner.launchIn(this)

                backwardRunnerJob.join()
                forwardRunnerJob.join()
            } else {
                backwardRunnerJob.join()

                forwardRunnerJob = forwardRunner.launchIn(this)
                forwardRunnerJob.join()
            }
        }
    }

    override fun <UnitType> newRunner(
        graph: JcApplicationGraph,
        manager: IfdsUnitManager<UnitType>,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
    ): IfdsUnitRunner<UnitType> = BidiIfdsUnitRunner(graph, manager, unitResolver, unit, startMethods)
}
