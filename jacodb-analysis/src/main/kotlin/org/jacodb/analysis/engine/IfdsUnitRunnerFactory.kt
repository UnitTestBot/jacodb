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
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

interface IfdsUnitRunner<UnitType> {
    val unit: UnitType
    val job: Job?

    fun launchIn(scope: CoroutineScope): Job

    suspend fun submitNewEdge(edge: IfdsEdge)
}

abstract class AbstractIfdsUnitRunner<UnitType>(final override val unit: UnitType) : IfdsUnitRunner<UnitType> {
    protected abstract suspend fun run()

    private var _job: Job? = null

    final override val job: Job? by ::_job

    final override fun launchIn(scope: CoroutineScope): Job = scope.launch(start = CoroutineStart.LAZY) {
        run()
    }.also {
        _job = it
        it.start()
    }
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
        startMethods: List<JcMethod>
    ) : IfdsUnitRunner<UnitType>
}