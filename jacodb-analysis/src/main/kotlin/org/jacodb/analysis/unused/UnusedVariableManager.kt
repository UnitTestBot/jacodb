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

package org.jacodb.analysis.unused

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jacodb.analysis.ifds.ControlEvent
import org.jacodb.analysis.ifds.Edge
import org.jacodb.analysis.ifds.Manager
import org.jacodb.analysis.ifds.QueueEmptinessChanged
import org.jacodb.analysis.ifds.Runner
import org.jacodb.analysis.ifds.SummaryStorageWithFlows
import org.jacodb.analysis.ifds.UniRunner
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.ifds.UnitType
import org.jacodb.analysis.ifds.UnknownUnit
import org.jacodb.analysis.ifds.Vertex
import org.jacodb.analysis.util.getPathEdges
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = mu.KotlinLogging.logger {}

class UnusedVariableManager(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver,
) : Manager<UnusedVariableDomainFact, Event> {

    private val methodsForUnit: MutableMap<UnitType, MutableSet<JcMethod>> = hashMapOf()
    private val runnerForUnit: MutableMap<UnitType, Runner<UnusedVariableDomainFact>> = hashMapOf()
    private val queueIsEmpty = ConcurrentHashMap<UnitType, Boolean>()

    private val summaryEdgesStorage = SummaryStorageWithFlows<UnusedVariableSummaryEdge>()
    private val vulnerabilitiesStorage = SummaryStorageWithFlows<UnusedVariableVulnerability>()

    private val stopRendezvous = Channel<Unit>(Channel.RENDEZVOUS)

    private fun newRunner(
        unit: UnitType,
    ): Runner<UnusedVariableDomainFact> {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }

        logger.debug { "Creating a new runner for $unit" }
        val analyzer = UnusedVariableAnalyzer(graph)
        val runner = UniRunner(
            graph = graph,
            analyzer = analyzer,
            manager = this@UnusedVariableManager,
            unitResolver = unitResolver,
            unit = unit,
            zeroFact = UnusedVariableZeroFact
        )

        runnerForUnit[unit] = runner
        return runner
    }

    private fun getAllCallees(method: JcMethod): Set<JcMethod> {
        val result: MutableSet<JcMethod> = hashSetOf()
        for (inst in method.flowGraph().instructions) {
            result += graph.callees(inst)
        }
        return result
    }

    private fun addStart(method: JcMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        if (unit == UnknownUnit) return
        val isNew = methodsForUnit.getOrPut(unit) { hashSetOf() }.add(method)
        if (isNew) {
            for (dep in getAllCallees(method)) {
                addStart(dep)
            }
        }
    }

    @JvmName("analyze") // needed for Java interop because of inline class (Duration)
    @OptIn(ExperimentalTime::class)
    fun analyze(
        startMethods: List<JcMethod>,
        timeout: Duration = 3600.seconds,
    ): List<UnusedVariableVulnerability> = runBlocking {
        val timeStart = TimeSource.Monotonic.markNow()

        // Add start methods:
        for (method in startMethods) {
            addStart(method)
        }

        // Determine all units:
        val allUnits = methodsForUnit.keys.toList()
        logger.info {
            "Starting analysis of ${
                methodsForUnit.values.sumOf { it.size }
            } methods in ${allUnits.size} units"
        }

        // Spawn runner jobs:
        val allJobs = allUnits.map { unit ->
            // Create the runner:
            val runner = newRunner(unit)

            // Start the runner:
            launch(start = CoroutineStart.LAZY) {
                val methods = methodsForUnit[unit]!!.toList()
                runner.run(methods)
            }
        }

        // Spawn progress job:
        val progress = launch(Dispatchers.IO) {
            while (isActive) {
                delay(1.seconds)
                logger.info {
                    "Progress: propagated ${
                        runnerForUnit.values.sumOf { it.getPathEdges().size }
                    } path edges"
                }
            }
        }

        // Spawn stopper job:
        val stopper = launch(Dispatchers.IO) {
            stopRendezvous.receive()
            logger.info { "Stopping all runners..." }
            allJobs.forEach { it.cancel() }
        }

        // Start all runner jobs:
        val timeStartJobs = TimeSource.Monotonic.markNow()
        allJobs.forEach { it.start() }

        // Await all runners:
        withTimeoutOrNull(timeout) {
            allJobs.joinAll()
        } ?: run {
            logger.info { "Timeout!" }
            allJobs.forEach { it.cancel() }
            allJobs.joinAll()
        }
        progress.cancelAndJoin()
        stopper.cancelAndJoin()
        logger.info {
            "All ${allJobs.size} jobs completed in %.1f s".format(
                timeStartJobs.elapsedNow().toDouble(DurationUnit.SECONDS)
            )
        }

        // Extract found vulnerabilities (sinks):
        val foundVulnerabilities = allUnits.flatMap { unit ->
            val runner = runnerForUnit[unit] ?: error("No runner for $unit")
            val result = runner.getIfdsResult()
            val allFacts = result.facts

            val used = hashMapOf<JcInst, Boolean>()
            for ((inst, facts) in allFacts) {
                for (fact in facts) {
                    if (fact is UnusedVariable) {
                        used.putIfAbsent(fact.initStatement, false)
                        if (fact.variable.isUsedAt(inst)) {
                            used[fact.initStatement] = true
                        }
                    }

                }
            }
            used.filterValues { !it }.keys.map {
                UnusedVariableVulnerability(
                    message = "Assigned value is unused",
                    sink = Vertex(it, UnusedVariableZeroFact)
                )
            }
        }

        if (logger.isDebugEnabled) {
            logger.debug { "Total found ${foundVulnerabilities.size} vulnerabilities" }
            for (vulnerability in foundVulnerabilities) {
                logger.debug { "$vulnerability in ${vulnerability.method}" }
            }
        }
        logger.info { "Total sinks: ${foundVulnerabilities.size}" }
        logger.info {
            "Total propagated ${
                runnerForUnit.values.sumOf { it.getPathEdges().size }
            } path edges"
        }
        logger.info {
            "Analysis done in %.1f s".format(
                timeStart.elapsedNow().toDouble(DurationUnit.SECONDS)
            )
        }
        foundVulnerabilities
    }

    override fun handleEvent(event: Event) {
        when (event) {
            is NewSummaryEdge -> {
                summaryEdgesStorage.add(UnusedVariableSummaryEdge(event.edge))
            }
        }
    }

    override fun handleControlEvent(event: ControlEvent) {
        when (event) {
            is QueueEmptinessChanged -> {
                queueIsEmpty[event.runner.unit] = event.isEmpty
                if (event.isEmpty) {
                    if (runnerForUnit.keys.all { queueIsEmpty[it] == true }) {
                        logger.debug { "All runners are empty" }
                        stopRendezvous.trySend(Unit).getOrNull()
                    }
                }
            }
        }
    }

    override fun subscribeOnSummaryEdges(
        method: JcMethod,
        scope: CoroutineScope,
        handler: (Edge<UnusedVariableDomainFact>) -> Unit,
    ) {
        summaryEdgesStorage
            .getFacts(method)
            .onEach { handler(it.edge) }
            .launchIn(scope)
    }
}
