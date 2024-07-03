package analysis.type

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.graph.reversed
import org.jacodb.analysis.ifds.Accessor
import org.jacodb.analysis.ifds.ControlEvent
import org.jacodb.analysis.ifds.Edge
import org.jacodb.analysis.ifds.FieldAccessor
import org.jacodb.analysis.ifds.Manager
import org.jacodb.analysis.ifds.QueueEmptinessChanged
import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UniRunner
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.impl.cfg.graphs.GraphDominators
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.graph.findDominators
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import java.util.concurrent.ConcurrentHashMap

class TypeInferenceManager(
    val graph: ApplicationGraph<EtsMethod, EtsStmt>,
) : Manager<Nothing, AnalyzerEvent, EtsMethod, EtsStmt> {
    private lateinit var runnerFinished: CompletableDeferred<Unit>

    private val backwardSummaries = ConcurrentHashMap<EtsMethod, MutableSet<BackwardSummaryAnalyzerEvent>>()
    private val forwardSummaries = ConcurrentHashMap<EtsMethod, MutableSet<ForwardSummaryAnalyzerEvent>>()

    private val methodDominatorsCache = ConcurrentHashMap<EtsMethod, GraphDominators<EtsStmt>>()

    private fun methodDominators(method: EtsMethod): GraphDominators<EtsStmt> =
        methodDominatorsCache.computeIfAbsent(method) {
            method.flowGraph().findDominators()
        }

    fun analyze(startMethods: List<EtsMethod>): Unit = runBlocking(Dispatchers.Default) {
        val backwardGraph = graph.reversed
        val backwardAnalyzer = BackwardAnalyzer(backwardGraph, ::methodDominators)
        val backwardRunner = UniRunner(
            this@TypeInferenceManager,
            backwardGraph,
            backwardAnalyzer,
            { SingletonUnit },
            SingletonUnit,
            BackwardTypeDomainFact.Zero
        )

        val backwardJob = launch(start = CoroutineStart.LAZY) {
            backwardRunner.run(startMethods)
        }

        runnerFinished = CompletableDeferred()
        backwardJob.start()
        runnerFinished.await()
        backwardJob.cancelAndJoin()

        val methodTypeScheme = methodTypeScheme()

        logger.info {
            buildString {
                appendLine("Backward types:")
                methodTypeScheme.values.forEach { appendLine(it) }
            }
        }

        val forwardGraph = graph
        val forwardAnalyzer = ForwardAnalyzer(forwardGraph, methodTypeScheme)
        val forwardRunner = UniRunner(
            this@TypeInferenceManager,
            forwardGraph,
            forwardAnalyzer,
            { SingletonUnit },
            SingletonUnit,
            ForwardTypeDomainFact.Zero
        )

        val forwardJob = launch(start = CoroutineStart.LAZY) {
            forwardRunner.run(startMethods)
        }

        runnerFinished = CompletableDeferred()
        forwardJob.start()
        runnerFinished.await()
        forwardJob.cancelAndJoin()

        val refinedTypes = refineMethodTypes(methodTypeScheme)
        logger.info {
            buildString {
                appendLine("Forward types:")
                refinedTypes.values.forEach { appendLine(it) }
            }
        }

        backwardRunner.let {  }
        forwardRunner.let {  }
    }

    private fun methodTypeScheme(): Map<EtsMethod, EtsMethodTypeFacts> =
        backwardSummaries.mapValues { (method, summaries) -> buildMethodTypeScheme(method, summaries) }

    private fun refineMethodTypes(types: Map<EtsMethod, EtsMethodTypeFacts>): Map<EtsMethod, EtsMethodTypeFacts> =
        types.mapValues { (method, type) ->
            val summaries = forwardSummaries[method].orEmpty()
            refineMethodType(type, summaries)
        }

    private fun buildMethodTypeScheme(
        method: EtsMethod,
        summaries: Iterable<BackwardSummaryAnalyzerEvent>
    ): EtsMethodTypeFacts {
        val types = summaries
            .mapNotNull { it.exitFact as? BackwardTypeDomainFact.TypedVariable }
            .groupBy({ it.variable }, { it.type })
            .filter { (base, _) -> base is AccessPathBase.This || base is AccessPathBase.Arg }
            .mapValues { (_, typeFacts) ->
                typeFacts.reduce { acc, typeFact ->
                    val intersection = acc.intersect(typeFact)

                    if (intersection == null) {
                        System.err.println("Empty intersection type: $acc & $typeFact")
                    }

                    intersection ?: acc
                }
            }

        return EtsMethodTypeFacts(method, types)
    }

    private fun refineMethodType(
        type: EtsMethodTypeFacts,
        summaries: Iterable<ForwardSummaryAnalyzerEvent>
    ): EtsMethodTypeFacts {
        val typeFacts = summaries
            .mapNotNull { it.initialFact as? ForwardTypeDomainFact.TypedVariable }
            .groupBy({ it.variable.base }, { normalizeTypeAccessPath(it.variable.accesses.iterator(), it.type) })
            .filter { (base, _) -> base is AccessPathBase.This || base is AccessPathBase.Arg }

        val types = type.types.mapValues { (base, typeScheme) ->
            val typeRefinements = typeFacts[base] ?: return@mapValues typeScheme
            val refinedScheme = typeRefinements.mapNotNull {
                val refinedScheme = typeScheme.intersect(it)

                if (refinedScheme == null) {
                    System.err.println("Empty intersection type: $typeScheme & $it")
                }

                refinedScheme
            }

            refinedScheme.reduce { acc, type -> acc.union(type) }
        }

        return EtsMethodTypeFacts(type.method, types)
    }

    private fun normalizeTypeAccessPath(ap: Iterator<Accessor>, type: EtsTypeFact): EtsTypeFact {
        if (!ap.hasNext()) return type

        val accessor = ap.next()

        if (accessor !is FieldAccessor) {
            TODO()
        }

        val normalized = normalizeTypeAccessPath(ap, type)
        return EtsTypeFact.ObjectEtsTypeFact(cls = null, properties = mapOf(accessor.name to normalized))
    }

    override fun handleEvent(event: AnalyzerEvent) {
        when (event) {
            is BackwardSummaryAnalyzerEvent -> {
                backwardSummaries.computeIfAbsent(event.method) {
                    ConcurrentHashMap.newKeySet()
                }.add(event)
            }

            is ForwardSummaryAnalyzerEvent -> {
                forwardSummaries.computeIfAbsent(event.method) {
                    ConcurrentHashMap.newKeySet()
                }.add(event)
            }
        }
    }

    override fun subscribeOnSummaryEdges(
        method: EtsMethod,
        scope: CoroutineScope,
        handler: (Edge<Nothing, EtsStmt>) -> Unit
    ) {
        error("No cross unit subscriptions")
    }

    override fun handleControlEvent(event: ControlEvent) {
        if (event is QueueEmptinessChanged) {
            if (event.isEmpty) {
                runnerFinished.complete(Unit)
            }
        }
    }

    companion object {
        val logger = mu.KotlinLogging.logger {}
    }
}