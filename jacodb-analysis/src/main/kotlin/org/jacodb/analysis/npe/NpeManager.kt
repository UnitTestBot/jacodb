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

package org.jacodb.analysis.npe

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
import org.jacodb.analysis.ifds.Manager
import org.jacodb.analysis.ifds.QueueEmptinessChanged
import org.jacodb.analysis.ifds.SummaryStorageImpl
import org.jacodb.analysis.ifds.UniRunner
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.ifds.UnitType
import org.jacodb.analysis.taint.EdgeForOtherRunner
import org.jacodb.analysis.taint.NewSummaryEdge
import org.jacodb.analysis.taint.NewVulnerability
import org.jacodb.analysis.taint.SummaryEdge
import org.jacodb.analysis.taint.TaintEdge
import org.jacodb.analysis.taint.TaintEvent
import org.jacodb.analysis.taint.TaintFact
import org.jacodb.analysis.taint.TaintRunner
import org.jacodb.analysis.taint.Vulnerability
import org.jacodb.analysis.util.getGetPathEdges
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = mu.KotlinLogging.logger {}

class NpeManager(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver,
) : Manager<TaintFact, TaintEvent> {

    private val methodsForUnit: MutableMap<UnitType, MutableSet<JcMethod>> = hashMapOf()
    private val runnerForUnit: MutableMap<UnitType, TaintRunner> = hashMapOf()
    private val queueIsEmpty = ConcurrentHashMap<UnitType, Boolean>()

    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdge>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<Vulnerability>()

    private val stopRendezvous = Channel<Unit>(Channel.RENDEZVOUS)

    private fun newRunner(
        unit: UnitType,
    ): TaintRunner {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }

        val analyzer = NpeAnalyzer(graph)
        val runner = UniRunner(graph, analyzer, this@NpeManager, unitResolver, unit)

        runnerForUnit[unit] = runner
        return runner
    }

    private fun addStart(method: JcMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        methodsForUnit.getOrPut(unit) { hashSetOf() }.add(method)
        // TODO: val isNew = (...).add(); if (isNew) { deps.forEach { addStart(it) } }
    }

    @OptIn(ExperimentalTime::class)
    fun analyze(
        startMethods: List<JcMethod>,
        timeout: Duration = 3600.seconds,
    ): List<Vulnerability> = runBlocking(Dispatchers.Default) {
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
                        runnerForUnit.values.sumOf { it.getGetPathEdges().size }
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
        val foundVulnerabilities = vulnerabilitiesStorage.knownMethods
            .flatMap { method ->
                vulnerabilitiesStorage.getCurrentFacts(method)
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
                runnerForUnit.values.sumOf { it.getGetPathEdges().size }
            } path edges"
        }
        logger.info {
            "Analysis done in %.1f s".format(
                timeStart.elapsedNow().toDouble(DurationUnit.SECONDS)
            )
        }
        foundVulnerabilities
    }

    override fun handleEvent(event: TaintEvent) {
        when (event) {
            is NewSummaryEdge -> {
                summaryEdgesStorage.add(SummaryEdge(event.edge))
            }

            is NewVulnerability -> {
                vulnerabilitiesStorage.add(event.vulnerability)
            }

            is EdgeForOtherRunner -> {
                val method = event.edge.method
                val unit = unitResolver.resolve(method)
                val otherRunner = runnerForUnit[unit] ?: run {
                    logger.trace { "Ignoring event=$event for non-existing runner for unit=$unit" }
                    return
                }
                otherRunner.submitNewEdge(event.edge, event.reason)
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
        handler: (TaintEdge) -> Unit,
    ) {
        summaryEdgesStorage
            .getFacts(method)
            .onEach { handler(it.edge) }
            .launchIn(scope)
    }
}

fun runNpeAnalysis(
    graph: JcApplicationGraph,
    unitResolver: UnitResolver,
    startMethods: List<JcMethod>,
): List<Vulnerability> {
    val manager = NpeManager(graph, unitResolver)
    return manager.analyze(startMethods)
}
