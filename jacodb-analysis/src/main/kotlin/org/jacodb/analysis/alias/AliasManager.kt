package org.jacodb.analysis.alias

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.jacodb.analysis.alias.apg.AccessGraph
import org.jacodb.analysis.alias.flow.BackwardAliasFlowFunctions
import org.jacodb.analysis.alias.flow.ForwardAliasFlowFunctions
import org.jacodb.analysis.engine.SummaryFact
import org.jacodb.analysis.engine.SummaryStorageImpl
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.engine.UnitType
import org.jacodb.analysis.graph.reversed
import org.jacodb.analysis.ifds2.ControlEvent
import org.jacodb.analysis.ifds2.Edge
import org.jacodb.analysis.ifds2.IRunner
import org.jacodb.analysis.ifds2.Manager
import org.jacodb.analysis.ifds2.QueueEmptinessChanged
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = KotlinLogging.logger {}

data class AliasSummaryEdge(
    val edge: AliasEdge
) : SummaryFact {
    override val method: JcMethod
        get() = edge.method
}

class AliasManager(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver
) : Manager<AccessGraph, AliasEvent> {
    private val methodsForUnit: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()

    private val forwardRunnerForUnit: MutableMap<UnitType, AliasRunner> = mutableMapOf()
    private val backwardRunnerForUnit: MutableMap<UnitType, AliasRunner> = mutableMapOf()

    private val queueIsEmpty: MutableMap<IRunner<*>, Boolean> = mutableMapOf()
    private val summaryEdgesStorage = SummaryStorageImpl<AliasSummaryEdge>()

    private val stopRendezvous = Channel<Unit>(Channel.RENDEZVOUS)

    private fun getForwardRunner(
        unit: UnitType
    ): AliasRunner =
        forwardRunnerForUnit.getOrPut(unit) {
            val analyzer = ForwardAliasAnalyzer(graph, ForwardAliasFlowFunctions(graph.classpath))
            AliasRunner(
                graph,
                analyzer,
                this,
                unitResolver,
                unit
            ).also { queueIsEmpty[it] = false }
        }

    private val reversedGraph by lazy { graph.reversed }

    private fun getBackwardRunner(
        unit: UnitType,
    ): AliasRunner =
        backwardRunnerForUnit.getOrPut(unit) {
            val analyzer = BackwardAliasAnalyzer(reversedGraph, BackwardAliasFlowFunctions(graph.classpath))
            AliasRunner(
                reversedGraph,
                analyzer,
                this,
                unitResolver,
                unit
            ).also { queueIsEmpty[it] = false }
        }

    private fun addStart(method: JcMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        methodsForUnit.getOrPut(unit) { mutableSetOf() }.add(method)
    }

    @OptIn(ExperimentalTime::class)
    fun query(
        method: JcMethod,
        location: JcInst,
        accessGraph: AccessGraph
    ): AliasResult = runBlocking(Dispatchers.Default) {
        // Add start methods:
        addStart(method)

        // Determine all units:
        val unit = unitResolver.resolve(method)

        // Create the runner:
        val bRunner = getBackwardRunner(unit)
        val fRunner = getForwardRunner(unit)

        // Spawn runner jobs:
        // Start the runner:
        val bRunnerJob = launch(start = CoroutineStart.LAZY) {
            bRunner.run()
        }

        val fRunnerJob = launch(start = CoroutineStart.LAZY) {
            fRunner.run()
        }

        // Spawn progress job:
        val progress = launch(Dispatchers.IO) {
            logger.info { "Progress job started" }
            while (isActive) {
                delay(1.seconds)
                logger.info { "Progress: total propagated ${forwardRunnerForUnit.values.sumOf { it.pathEdges.size }} path edges" }
            }
            logger.info { "Progress job finished" }
        }

        // Spawn stopper job:
        val stopper = launch(Dispatchers.IO) {
            logger.info { "Stopper job started" }
            stopRendezvous.receive()
             delay(100)
            // if (runnerForUnit.values.any { !it.workList.isEmpty }) {
            //     logger.warn { "NOT all runners have empty work list" }
            // }
            logger.info { "Stopping all runners..." }
            bRunnerJob.cancel()
            fRunnerJob.cancel()
            logger.info { "Stopper job finished" }
        }

        // Start all runner jobs:
        val timeStartJobs = TimeSource.Monotonic.markNow()

        val vertex = AliasVertex(location, accessGraph)
        val edge = AliasEdge(vertex, vertex)
        bRunner.submitNewEdge(edge)

        bRunnerJob.start()
        fRunnerJob.start()

        // Await all runners:
        // withTimeoutOrNull(5.seconds) {
        bRunnerJob.join()
        fRunnerJob.join()
        // } ?: run {
        //     allJobs.forEach { it.cancel() }
        //     allJobs.joinAll()
        // }
        progress.cancelAndJoin()
        stopper.cancelAndJoin()
        logger.info {
            "Runner job completed in %.1f s".format(
                timeStartJobs.elapsedNow().toDouble(DurationUnit.SECONDS)
            )
        }

        val facts = summaryEdgesStorage.getCurrentFacts(method)
        val aliases = facts.map { it.edge.to.fact }
        val allocationSites = facts.map { it.edge.from.statement }.distinct()

        AliasResult(aliases, allocationSites)
    }

    override fun handleEvent(event: AliasEvent) {
        when (event) {
            is SummaryEdge -> {
                logger.info { "Summary: $event" }
                summaryEdgesStorage.add(AliasSummaryEdge(event.edge))
            }

            is AllocationSite -> {
                logger.info { "AllocationSite: $event" }
                val method = event.stmt.location.method
                val unit = unitResolver.resolve(method)
                val forwardRunner = getForwardRunner(unit)

                val startVertex = AliasVertex(event.stmt, AccessGraph(event.lhs, null))
                val successors = graph.successors(event.stmt)
                for (succ in successors) {
                    val endVertex = AliasVertex(succ, AccessGraph(event.lhs, null))
                    val edge = AliasEdge(startVertex, endVertex)
                    forwardRunner.submitNewEdge(edge)
                }
            }

            is FieldWrite -> {
                logger.info { event }
            }

            is AliasOnCall -> {

            }

            is AliasOnReturn -> {

            }

            is FieldRead -> {

            }

            is Turnover -> {
                error("Unsupported")
            }
        }
    }

    override fun subscribeOnSummaryEdges(
        method: JcMethod,
        scope: CoroutineScope,
        handler: suspend (Edge<AccessGraph>) -> Unit
    ) {
        summaryEdgesStorage
            .getFacts(method)
            .map { it.edge }
            .onEach(handler)
            .launchIn(scope)
    }

    override suspend fun handleControlEvent(event: ControlEvent) {
        when (event) {
            is QueueEmptinessChanged -> {
                // val oldIsEmpty = queueIsEmpty[event.runner.unit]
                // if (oldIsEmpty != null) {
                //     check(event.isEmpty == !oldIsEmpty)
                // }
                queueIsEmpty[event.runner] = event.isEmpty
                if (event.isEmpty) {
                    yield()
                    // if (runnerForUnit.values.all { it.workList.isEmpty }) {
                    if (queueIsEmpty.all { it.value }) {
                        stopRendezvous.send(Unit)
                    }
                }
            }
        }
    }
}
