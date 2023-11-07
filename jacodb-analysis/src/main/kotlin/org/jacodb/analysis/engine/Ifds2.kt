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

import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.config.CallPositionResolverToAccessPath
import org.jacodb.analysis.config.CallPositionResolverToJcValue
import org.jacodb.analysis.config.ConditionEvaluator
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.config.TaintActionEvaluator
import org.jacodb.analysis.config.TaintConfig
import org.jacodb.analysis.library.analyzers.TaintAnalysisNode
import org.jacodb.analysis.library.analyzers.TaintNode
import org.jacodb.analysis.library.analyzers.getFormalParamsOf
import org.jacodb.analysis.library.analyzers.thisInstance
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.configuration.AssignMark
import org.jacodb.configuration.CopyAllMarks
import org.jacodb.configuration.CopyMark
import org.jacodb.configuration.RemoveAllMarks
import org.jacodb.configuration.RemoveMark
import org.jacodb.configuration.TaintCleaner
import org.jacodb.configuration.TaintMark
import org.jacodb.configuration.TaintMethodSource
import org.jacodb.configuration.TaintPassThrough

interface Fact

object ZeroFact : Fact

data class Tainted(
    val variable: AccessPath,
    val mark: TaintMark,
) : Fact {
    constructor(fact: TaintNode) : this(fact.variable, TaintMark("Taint"))
}

fun DomainFact.toFact(): Fact = when (this) {
    ZEROFact -> ZeroFact
    is TaintNode -> Tainted(variable, TaintMark("Taint"))
    else -> TODO()
}

fun Fact.toDomainFact(): DomainFact = when (this) {
    ZeroFact -> ZEROFact
    is Tainted -> TaintAnalysisNode(variable)
    else -> object : DomainFact {}
}

data class Vertex(
    val statement: JcInst,
    val fact: Fact,
) {
    val method: JcMethod get() = statement.location.method

    constructor(vertex: IfdsVertex) : this(vertex.statement, vertex.domainFact.toFact())
}

fun Vertex.toIfds(): IfdsVertex = IfdsVertex(statement, fact.toDomainFact())

data class Edge(
    val from: Vertex,
    val to: Vertex,
) {
    // TODO: inline and remove
    val method: JcMethod get() = from.method

    init {
        require(from.method == to.method)
    }

    constructor(edge: IfdsEdge) : this(Vertex(edge.from), Vertex(edge.to))
}

fun Edge.toIfds(): IfdsEdge = IfdsEdge(from.toIfds(), to.toIfds())

fun interface FlowFunction {
    fun compute(fact: Fact): Collection<Fact>
}

interface FlowFunctionsSpace2 {
    fun obtainPossibleStartFacts(startStatement: JcInst): List<Fact>

    /**
     * Sequent flow function.
     *
     * ```
     *   [ DO() ] :: current
     *     ||
     *     || (sequent edge)
     *     ||
     *   [ DO() ]
     * ```
     */
    fun obtainSequentFlowFunction(
        current: JcInst,
    ): FlowFunction

    /**
     * Call-to-return-site flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   ||
     *   || (call-to-return-site edge)
     *   ||
     * [ RETURN FROM p ] :: returnSite
     * ```
     */
    fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
    ): FlowFunction

    /**
     * Call-to-start flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   : \
     *   :  \ (call-to-start edge)
     *   :   \
     *   :  [ START p ]
     *   :    |
     *   :  [ EXIT p ]
     *   :   /
     *   :  /
     * [ RETURN FROM p ]
     * ```
     */
    fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod,
    ): FlowFunction

    /**
     * Exit-to-return-site flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   :  \
     *   :   \
     *   :  [ START p ]
     *   :    |
     *   :  [ EXIT p ] :: exitStatement
     *   :   /
     *   :  / (exit-to-return-site edge)
     *   : /
     * [ RETURN FROM p ] :: returnSite
     * ```
     */
    fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
    ): FlowFunction
}

@Suppress("PublicApiImplicitType")
class TaintForwardFlowFunctions(
    private val config: TaintConfig,
    private val cp: JcClasspath,
) : FlowFunctionsSpace2 {
    private fun generates(inst: JcInst): Collection<Tainted> {
        if (inst.callExpr == null) return emptyList()
        val conditionEvaluator = ConditionEvaluator(CallPositionResolverToJcValue(inst))
        val actionEvaluator = TaintActionEvaluator(CallPositionResolverToAccessPath(inst))
        val facts = mutableSetOf<Tainted>()
        for (item in config.items.filterIsInstance<TaintMethodSource>()) {
            if (item.condition.accept(conditionEvaluator)) {
                facts += item.actionsAfter
                    .filterIsInstance<AssignMark>()
                    .map { action -> actionEvaluator.evaluate(action) }
            }
        }
        return facts
    }

    // private fun sanitizes(inst: JcInst, fact: Tainted): Boolean {
    // ...
    // }

    override fun obtainPossibleStartFacts(startStatement: JcInst): List<Fact> {
        // FIXME
        // TODO: handle TaintEntryPointSource
        return listOf(ZeroFact)
    }

    // TODO: rename / refactor
    private fun transmitTaint(
        from: JcExpr,
        to: JcValue,
        fact: Tainted,
    ): List<Fact> {
        val toPath = to.toPathOrNull() ?: return emptyList() // FIXME: check, add comment
        val fromPath = from.toPathOrNull() ?: TODO() // TODO: how to handle it?

        // 'from' is tainted with 'fact':
        if (fromPath.startsWith(fact.variable)) {
            val newTaint = fact.copy(variable = toPath)
            // Both 'from' and 'to' are now tainted:
            return listOf(fact, newTaint)
        }

        // Some sub-path in 'to' is tainted with 'fact':
        if (fact.variable.startsWith(toPath)) {
            // Drop 'fact' taint:
            return emptyList()
        }

        // 'to' is tainted (strictly) with 'fact':
        // Note: "non-strict" case is handled above.
        if (toPath.startsWith(fact.variable)) {
            // No drop:
            return listOf(fact)
        }

        // Neither 'from' nor 'to' is tainted with 'fact', simply pass-through:
        return listOf(fact)
    }

    // TODO: rename / refactor
    private fun transmitTaintNormal(
        inst: JcInst,
        fact: Tainted,
    ): List<Fact> {
        // TODO: read the config in order to determine whether to pass-through the 'fact'.
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: JcInst,
    ) = FlowFunction { fact ->
        if (fact == ZeroFact) {
            return@FlowFunction listOf(ZeroFact) + generates(current)
        }

        if (fact !is Tainted) {
            return@FlowFunction emptyList()
        }

        if (current is JcAssignInst) {
            transmitTaint(current.rhv, current.lhv, fact)
        } else {
            transmitTaintNormal(current, fact)
        }
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
    ) = FlowFunction { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

        // If 'fact' is ZeroFact, handle MethodSource. If there are no suitable MethodSource items, perform default.
        // For other facts (Tainted only?), handle PassThrough/Cleaner items.
        // TODO: what to do with "other facts" on CopyAllMarks/RemoveAllMarks?

        // Handle MethodSource config items:
        if (fact == ZeroFact) {
            val conditionEvaluator = ConditionEvaluator(CallPositionResolverToJcValue(callStatement))
            val actionEvaluator = TaintActionEvaluator(CallPositionResolverToAccessPath(callStatement))
            // TODO: replace with buildSet?
            val facts = mutableSetOf<Tainted>()
            for (item in config.items.filterIsInstance<TaintMethodSource>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    facts += item.actionsAfter
                        .filterIsInstance<AssignMark>()
                        .map { action -> actionEvaluator.evaluate(action) }
                }
            }
            return@FlowFunction facts + ZeroFact
        }

        // FIXME: adhoc to satisfy types
        if (fact !is Tainted) {
            // TODO: what to return here?
            return@FlowFunction emptyList()
        }

        val conditionEvaluator = FactAwareConditionEvaluator(
            fact,
            CallPositionResolverToJcValue(callStatement)
        )
        val actionEvaluator = TaintActionEvaluator(CallPositionResolverToAccessPath(callStatement))
        // val actionEvaluatorVisitor = FactAwareTaintActionEvaluator(fact, actionEvaluator)
        val resultingFacts = mutableSetOf<Tainted>()

        for (item in config.items.filterIsInstance<TaintPassThrough>()) {
            if (item.condition.accept(conditionEvaluator)) {
                resultingFacts += item.actionsAfter
                    .filterIsInstance<CopyMark>()
                    .mapNotNull { action ->
                        actionEvaluator.evaluate(action, fact)
                    }
                resultingFacts += item.actionsAfter
                    .filterIsInstance<CopyAllMarks>()
                    .mapNotNull { action ->
                        actionEvaluator.evaluate(action, fact)
                    }
            }
        }
        for (item in config.items.filterIsInstance<TaintCleaner>()) {
            if (item.condition.accept(conditionEvaluator)) {
                resultingFacts += item.actionsAfter
                    .filterIsInstance<RemoveMark>()
                    .mapNotNull { action ->
                        actionEvaluator.evaluate(action, fact)
                    }
                resultingFacts += item.actionsAfter
                    .filterIsInstance<RemoveAllMarks>()
                    .mapNotNull { action ->
                        actionEvaluator.evaluate(action, fact)
                    }
            }
        }

        resultingFacts
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod,
    ) = FlowFunction { fact ->
        if (fact == ZeroFact) {
            return@FlowFunction listOf(ZeroFact) // TODO: + entry point config?
        }

        if (fact !is Tainted) {
            // TODO: what to return here?
            return@FlowFunction emptyList()
        }

        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val formalParams = cp.getFormalParamsOf(callee)

        buildSet {
            // Transmit facts on arguments ('actual' to 'formal'):
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(transmitTaint(actual, formal, fact))
            }

            // Transmit facts on instance ('instance' to 'this'):
            if (callExpr is JcInstanceCallExpr) {
                addAll(transmitTaint(callExpr.instance, callee.thisInstance, fact))
            }

            // TODO: check
            // Transmit facts on static value:
            if (fact is Tainted && fact.variable.isStatic) {
                add(fact)
            }

            // TODO: can't happen here, because we already handled ZeroFact at the beginning.
            // // Transmit zero fact:
            // if (fact == ZeroFact) {
            //     add(fact)
            // }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
    ) = FlowFunction { fact ->
        // TODO: do we even need to return non-empty list for zero fact here?
        if (fact == ZeroFact) {
            return@FlowFunction listOf(ZeroFact)
        }

        if (fact !is Tainted) {
            // TODO: what to return here?
            return@FlowFunction emptyList()
        }

        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val callee = exitStatement.location.method
        val formalParams = cp.getFormalParamsOf(callee)

        buildSet {
            // Transmit facts on arguments ('formal' back to 'actual'), if they are passed by-ref:
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(transmitTaint(formal, actual, fact))
            }

            // Transmit facts on instance ('this' to 'instance'):
            if (callExpr is JcInstanceCallExpr) {
                addAll(transmitTaint(callee.thisInstance, callExpr.instance, fact))
            }

            // Transmit facts on static value:
            if (fact is Tainted && fact.variable.isStatic) {
                add(fact)
            }

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (exitStatement is JcReturnInst && callStatement is JcAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                exitStatement.returnValue?.let { returnValue ->
                    addAll(transmitTaint(returnValue, callStatement.lhv, fact))
                }
            }
        }
    }
}

// interface EdgeObserver {
//     fun handleNewEdge(edge: Edge)
// }
//
// class EdgeObserverImpl : EdgeObserver {
//     override fun handleNewEdge(edge: Edge) {
//         println("new edge: $edge")
//     }
// }

class Manager<UnitType>(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver<UnitType>,
) {
    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdgeFact>()

    suspend fun handleEvent(event: Event, runner: Ifds<UnitType>) {
        when (event) {
            is SubscriptionForSummaryEdges2 -> {
                summaryEdgesStorage
                    .getFacts(event.method)
                    .map { it.edge }
                    .map { Edge(it) }
                    .collect(event.collector)
            }

            is NewSummaryEdge -> {
                // FIXME
                runBlocking {
                    summaryEdgesStorage.send(SummaryEdgeFact(event.edge.toIfds()))
                }
            }
        }
    }
}

sealed interface Event

data class SubscriptionForSummaryEdges2(
    val method: JcMethod,
    val collector: FlowCollector<Edge>,
) : Event

data class NewSummaryEdge(
    val edge: Edge,
) : Event {
    val method: JcMethod = edge.method
}

interface Analyzer2 {
    val flowFunctions: FlowFunctionsSpace2

    fun handleNewEdge(edge: Edge): List<Event>
}

class TaintAnalyzer(
    private val config: TaintConfig,
    private val graph: JcApplicationGraph,
) : Analyzer2 {
    override val flowFunctions: FlowFunctionsSpace2 by lazy {
        TaintForwardFlowFunctions(config, graph.classpath)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    override fun handleNewEdge(edge: Edge): List<Event> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }
        // TODO: check whether 'edge.to.statement' is sink. If it is a sink (due to config, or by some other reason),
        //       return a new event with found vulnerability. Do not forget to include the config's rule for a sink.
    }
}

private fun <T> magic(): T = error("No magic!")

class Ifds<UnitType>(
    private val graph: JcApplicationGraph,
    private val analyzer: Analyzer2,
    private val manager: Manager<UnitType>,
    private val unitResolver: UnitResolver<UnitType>,
    private val unit: UnitType,
    private val startMethods: List<JcMethod>,
    private val config: TaintConfig,
) {
    private val flowSpace: FlowFunctionsSpace2 = analyzer.flowFunctions

    // private val workList: Channel<Edge> = Channel(Channel.UNLIMITED)
    private val workList: ArrayDeque<Edge> = ArrayDeque()
    private val pathEdges: MutableSet<Edge> = mutableSetOf()
    private val summaryEdges: MutableMap<Vertex, MutableSet<Vertex>> = mutableMapOf()
    private val callSitesOf: MutableMap<Vertex, MutableSet<Edge>> = mutableMapOf()

    fun run() {
        // TODO: maybe move 'startMethods' to 'run' arguments?

        for (method in startMethods) {
            require(unitResolver.resolve(method) == unit)
            for (start in graph.entryPoints(method)) {
                val startFacts = flowSpace.obtainPossibleStartFacts(start)
                for (startFact in startFacts) {
                    val vertex = Vertex(start, startFact)
                    val edge = Edge(vertex, vertex) // loop
                    propagate(edge)
                }
            }
        }

        tabulationAlgorithm()
    }

    private fun propagate(edge: Edge): Boolean {
        require(unitResolver.resolve(edge.method) == unit)

        if (pathEdges.add(edge)) {
            workList.add(edge)
            // TODO: send 'edge' to subscribers/manager
            // TODO: something.handleNewEdge(edge)
            return true
        }
        return false
    }

    private val JcMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    private fun tabulationAlgorithm() {
        while (workList.isNotEmpty()) {
            val currentEdge = workList.removeFirst()
            val (startVertex, currentVertex) = currentEdge
            val (current, currentFact) = currentVertex

            val currentCallees = graph.callees(current).toList()
            val currentIsCall = currentCallees.isNotEmpty()
            // FIXME: [old] val currentIsExit = current in graph.exitPoints(graph.methodOf(current))
            val currentIsExit = current in graph.exitPoints(current.location.method)

            if (currentIsCall) {
                for (returnSite in graph.successors(current)) {
                    // Propagate through the call-to-return-site edge:
                    val factsAtReturnSite = flowSpace
                        .obtainCallToReturnSiteFlowFunction(current, returnSite)
                        .compute(currentFact)
                    for (returnSiteFact in factsAtReturnSite) {
                        val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                        val newEdge = Edge(startVertex, returnSiteVertex)
                        propagate(newEdge)
                    }

                    // Propagate through the call:
                    for (callee in currentCallees) {
                        val factsAtCalleeStart = flowSpace
                            .obtainCallToStartFlowFunction(current, callee)
                            .compute(currentFact)
                        for (calleeStart in graph.entryPoints(callee)) {
                            for (calleeStartFact in factsAtCalleeStart) {
                                val calleeStartVertex = Vertex(calleeStart, calleeStartFact)

                                if (callee.isExtern) {
                                    // TODO: Initialize analysis for callee
                                    // TODO: send Edge(calleeStartVertex, calleeStartVertex) loop-edge

                                    // Subscribe on summary edges:
                                    val summaries = flow {
                                        val event = SubscriptionForSummaryEdges2(callee, this@flow)
                                        manager.handleEvent(event, this@Ifds)
                                    }
                                    summaries
                                        .filter { it.from == calleeStartVertex }
                                        .map { it.to }
                                        .onEach { (exit, exitFact) ->
                                            val finalFacts = flowSpace
                                                .obtainExitToReturnSiteFlowFunction(current, returnSite, exit)
                                                .compute(exitFact)
                                            for (returnSiteFact in finalFacts) {
                                                val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                                                val newEdge = Edge(startVertex, returnSiteVertex)
                                                propagate(newEdge)
                                            }
                                        }
                                    // TODO: add `.launchIn(this)` to the above Flow

                                    TODO()
                                } else {
                                    // Save info about the call for summary edges that will be found later:
                                    callSitesOf.getOrPut(calleeStartVertex) { mutableSetOf() }.add(currentEdge)

                                    // Initialize analysis for callee:
                                    run {
                                        val newEdge = Edge(calleeStartVertex, calleeStartVertex) // loop
                                        propagate(newEdge)
                                    }

                                    // Handle already-found summary edges:
                                    val exits = summaryEdges[calleeStartVertex].orEmpty()
                                    for ((exit, exitFact) in exits) {
                                        val finalFacts = flowSpace
                                            .obtainExitToReturnSiteFlowFunction(current, returnSite, exit)
                                            .compute(exitFact)
                                        for (returnSiteFact in finalFacts) {
                                            val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                                            val newEdge = Edge(startVertex, returnSiteVertex)
                                            propagate(newEdge)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (currentIsExit) {
                    // Propagate through the summary edge:
                    for (callerPathEdge in callSitesOf[startVertex].orEmpty()) {
                        val caller = callerPathEdge.to.statement
                        for (returnSite in graph.successors(caller)) {
                            val factsAtReturnSite = flowSpace
                                .obtainExitToReturnSiteFlowFunction(caller, returnSite, current)
                                .compute(currentFact)
                            for (returnSiteFact in factsAtReturnSite) {
                                val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                                val newEdge = Edge(callerPathEdge.from, returnSiteVertex)
                                propagate(newEdge)
                            }
                        }
                    }

                    // Add new summary edge:
                    summaryEdges.getOrPut(startVertex) { mutableSetOf() }.add(currentVertex)
                }

                // Simple propagation to the next instruction:
                for (next in graph.successors(current)) {
                    val factsAtNext = flowSpace
                        .obtainSequentFlowFunction(current/*, next*/)
                        .compute(currentFact)
                    for (nextFact in factsAtNext) {
                        val nextVertex = Vertex(next, nextFact)
                        val newEdge = Edge(startVertex, nextVertex)
                        propagate(newEdge)
                    }
                }
            }
        }
    }
}

fun main() {
    //
}