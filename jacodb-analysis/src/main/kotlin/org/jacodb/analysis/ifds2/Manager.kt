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

package org.jacodb.analysis.ifds2

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.jacodb.analysis.engine.SummaryStorageImpl
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.engine.UnitType
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

class Manager(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver,
) {

    private val methodsForUnit: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()
    private val runnerForUnit: MutableMap<UnitType, Runner> = mutableMapOf()
    private val queueIsEmpty: MutableMap<UnitType, Boolean> = mutableMapOf()

    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdge>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<Vulnerability>()

    private val stopRendezvous = Channel<Unit>(Channel.RENDEZVOUS)

    private fun newRunner(
        unit: UnitType,
        analyzer: Analyzer,
    ): Runner {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }
        val runner = Runner(graph, analyzer, this@Manager, unitResolver, unit)
        runnerForUnit[unit] = runner
        return runner
    }

    private fun addStart(method: JcMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        methodsForUnit.getOrPut(unit) { mutableSetOf() }.add(method)
        // TODO: val isNew = (...).add(); if (isNew) { deps.forEach { addStart(it) } }
    }

    @OptIn(ExperimentalTime::class)
    fun analyze(startMethods: List<JcMethod>): List<Vulnerability> = runBlocking(Dispatchers.Default) {
        val timeStart = TimeSource.Monotonic.markNow()

        // Add start methods:
        for (method in startMethods) {
            addStart(method)
        }

        // Determine all units:
        val allUnits = methodsForUnit.keys.toList()
        logger.info { "Starting analysis of ${methodsForUnit.values.sumOf { it.size }} methods in ${allUnits.size} units" }

        // Spawn runner jobs:
        val allJobs = allUnits.map { unit ->
            // Initialize the analyzer:
            val analyzer = TaintAnalyzer(graph)

            // Create the runner:
            val runner = newRunner(unit, analyzer)

            // Start the runner:
            launch(start = CoroutineStart.LAZY) {
                val methods = methodsForUnit[unit]!!.toList()
                runner.run(methods)
            }
        }

        // Spawn progress job:
        val progress = launch(Dispatchers.IO) {
            logger.info { "Progress job started" }
            while (isActive) {
                delay(1.seconds)
                logger.info { "Progress: total propagated ${runnerForUnit.values.sumOf { it.pathEdges.size }} path edges" }
            }
            logger.info { "Progress job finished" }
        }

        // Spawn stopper job:
        val stopper = launch(Dispatchers.IO) {
            logger.info { "Stopper job started" }
            stopRendezvous.receive()
            // delay(100)
            // if (runnerForUnit.values.any { !it.workList.isEmpty }) {
            //     logger.warn { "NOT all runners have empty work list" }
            // }
            logger.info { "Stopping all runners..." }
            allJobs.forEach { it.cancel() }
            logger.info { "Stopper job finished" }
        }

        // Start all runner jobs:
        val timeStartJobs = TimeSource.Monotonic.markNow()
        allJobs.forEach { it.start() }

        // Await all runners:
        // withTimeoutOrNull(5.seconds) {
        allJobs.joinAll()
        // } ?: run {
        //     allJobs.forEach { it.cancel() }
        //     allJobs.joinAll()
        // }
        progress.cancelAndJoin()
        stopper.cancelAndJoin()
        logger.info {
            "All ${allJobs.size} jobs completed in %.1f s".format(
                timeStartJobs.elapsedNow().toDouble(DurationUnit.SECONDS)
            )
        }

        // Extract found vulnerabilities (sinks):
        val foundVulnerabilities = vulnerabilitiesStorage.knownMethods.flatMap { method ->
            vulnerabilitiesStorage.getCurrentFacts(method)
        }
        logger.info { "Total found ${foundVulnerabilities.size} vulnerabilities" }
        for (vulnerability in foundVulnerabilities) {
            logger.info { "$vulnerability in ${vulnerability.method}" }
        }
        logger.info { "Total sinks: ${foundVulnerabilities.size}" }

        logger.debug { "Total propagated ${runnerForUnit.values.sumOf { it.pathEdges.size }} path edges" }

        val statsFileName = "stats.csv"
        logger.debug { "Writing stats in '$statsFileName'..." }
        File(statsFileName).outputStream().bufferedWriter().use {
            it.write("classname,cwe,method\n")
            for (vulnerability in foundVulnerabilities) {
                for (cwe in vulnerability.rule!!.cwe) {
                    it.write("${vulnerability.method.enclosingClass.simpleName},$cwe,${vulnerability.method.name}\n")
                }
            }
        }

        logger.info { "Analysis done in %.1f s".format(timeStart.elapsedNow().toDouble(DurationUnit.SECONDS)) }
        foundVulnerabilities
    }

    suspend fun handleEvent(event: Event) {
        when (event) {
            is EdgeForOtherRunner -> {
                val method = event.edge.method
                val unit = unitResolver.resolve(method)
                val otherRunner = runnerForUnit[unit] ?: run {
                    // logger.debug { "Ignoring event=$event for non-existing runner for unit=$unit" }
                    return
                }
                otherRunner.submitNewEdge(event.edge)
            }

            is SubscriptionForSummaryEdges2 -> {
                summaryEdgesStorage
                    .getFacts(event.method)
                    .map { it.edge }
                    .collect(event.collector)
            }

            is SubscriptionForSummaryEdges3 -> {
                summaryEdgesStorage
                    .getFacts(event.method)
                    .map { it.edge }
                    .onEach(event.handler)
                    .launchIn(event.scope)
            }

            is NewSummaryEdge -> {
                summaryEdgesStorage.add(SummaryEdge(event.edge))
            }

            is NewVulnerability -> {
                vulnerabilitiesStorage.add(event.vulnerability)
            }

            is QueueEmptinessChanged -> {
                // val oldIsEmpty = queueIsEmpty[event.runner.unit]
                // if (oldIsEmpty != null) {
                //     check(event.isEmpty == !oldIsEmpty)
                // }
                queueIsEmpty[event.runner.unit] = event.isEmpty
                if (event.isEmpty) {
                    yield()
                    // if (runnerForUnit.values.all { it.workList.isEmpty }) {
                    if (runnerForUnit.keys.all { queueIsEmpty[it] == true }) {
                        stopRendezvous.send(Unit)
                    }
                }
            }
        }
    }
}
