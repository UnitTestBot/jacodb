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

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jacodb.analysis.graph.reversed
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph

interface IfdsUnitRunner {
    suspend fun <UnitType> run(
        graph: JcApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>,
    )
}

class ParallelBidiIfdsUnitRunner (
    private val forwardRunner: IfdsUnitRunner,
    private val backwardRunner: IfdsUnitRunner
) : IfdsUnitRunner {
    override suspend fun <UnitType> run(
        graph: JcApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>
    ): Unit = coroutineScope {
        launch {
            forwardRunner.run(graph, summary, unitResolver, unit, startMethods)
        }

        launch {
            backwardRunner.run(graph.reversed, summary, unitResolver, unit, startMethods)
        }
    }
}

class SequentialBidiIfdsUnitRunner(
    private val forward: IfdsUnitRunner,
    private val backward: IfdsUnitRunner,
) : IfdsUnitRunner {

    override suspend fun <UnitType> run(
        graph: JcApplicationGraph,
        summary: Summary,
        unitResolver: UnitResolver<UnitType>,
        unit: UnitType,
        startMethods: List<JcMethod>
    ) = coroutineScope {

        val job = launch {
            backward.run(graph.reversed, summary, unitResolver, unit, startMethods)
        }

        delay(100)
        job.cancelAndJoin()

        forward.run(graph, summary, unitResolver, unit, startMethods)
    }
}