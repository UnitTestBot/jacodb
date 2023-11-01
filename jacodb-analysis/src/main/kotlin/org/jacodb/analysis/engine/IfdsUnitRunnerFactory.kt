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

/**
 * Represents a runner and allows to manipulate it.
 *
 * By convention, runners are created by instances of [IfdsUnitRunnerFactory],
 * but this creation doesn't launch anything.
 * The work itself is launched by [launchIn] method, which should be called exactly once.
 * This method returns a [Job] instance representing a launched coroutine.
 * This [Job] could also be further obtained via [job] property.
 *
 * It is not recommended to implement this interface directly, instead,
 * [AbstractIfdsUnitRunner] should be extended.
 */
interface IfdsUnitRunner<UnitType> {
    val unit: UnitType
    val job: Job?

    fun launchIn(scope: CoroutineScope): Job

    /**
     * Submits a new [IfdsEdge] to runner's queue. Should be called only after [launchIn].
     * Note that this method can be called from different threads.
     */
    suspend fun submitNewEdge(edge: IfdsEdge)
}

/**
 * [AbstractIfdsUnitRunner] contains proper implementation of [launchIn] method and [job] property.
 * Inheritors should only implement [submitNewEdge] and a suspendable [run] method.
 * The latter is the main method of runner, that should do all its work.
 */
abstract class AbstractIfdsUnitRunner<UnitType>(final override val unit: UnitType) : IfdsUnitRunner<UnitType> {
    /**
     * The main method of the runner, which will be called by [launchIn]
     */
    protected abstract suspend fun run()

    private var _job: Job? = null

    final override val job: Job? by ::_job
    // TODO: final override val job: Job? get() = _job

    final override fun launchIn(scope: CoroutineScope): Job = scope.launch(start = CoroutineStart.LAZY) {
        run()
    }.also {
        _job = it
        it.start()
    }
}

/**
 * Produces a runner for any given unit.
 */
interface IfdsUnitRunnerFactory {
    /**
     * Produces a runner for given [unit], using given [startMethods] as entry points.
     * All start methods should belong to the [unit].
     * Note that this method DOES NOT START runner's job.
     *
     * @param graph provides supergraph for application (including methods, belonging to other units)
     *
     * @param manager [IfdsUnitManager] instance that will manage the produced runner.
     *
     * @param unitResolver will be used to get units of methods observed during analysis.
     */
    fun <UnitType> newRunner(
        graph: JcApplicationGraph,
        manager: IfdsUnitManager<UnitType>,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
    ): IfdsUnitRunner<UnitType>
}
