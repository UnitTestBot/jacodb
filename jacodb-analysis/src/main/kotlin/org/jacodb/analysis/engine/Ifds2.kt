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

@file:Suppress("LiftReturnOrAssignment")

package org.jacodb.analysis.engine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.config.BasicConditionEvaluator
import org.jacodb.analysis.config.CallPositionToAccessPathResolver
import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.config.TaintActionEvaluator
import org.jacodb.analysis.library.analyzers.TaintAnalysisNode
import org.jacodb.analysis.library.analyzers.TaintNode
import org.jacodb.analysis.library.analyzers.getFormalParamsOf
import org.jacodb.analysis.library.analyzers.thisInstance
import org.jacodb.analysis.logger
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
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.RemoveAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.TaintCleaner
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import java.util.concurrent.ConcurrentHashMap

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
     *     |
     *     | (sequent edge)
     *     |
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
     *   :
     *   : (call-to-return-site edge)
     *   :
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
    private val cp: JcClasspath,
) : FlowFunctionsSpace2 {
    // private fun generates(inst: JcInst): Collection<Tainted> {
    //     if (inst.callExpr == null) return emptyList()
    //     val conditionEvaluator = ConditionEvaluator(CallPositionResolverToJcValue(inst))
    //     val actionEvaluator = TaintActionEvaluator(CallPositionResolverToAccessPath(inst))
    //     val facts = mutableSetOf<Tainted>()
    //     for (item in config.items.filterIsInstance<TaintMethodSource>()) {
    //         if (item.condition.accept(conditionEvaluator)) {
    //             for (action in item.actionsAfter) {
    //                 when (action) {
    //                     is AssignMark -> {
    //                         facts += actionEvaluator.evaluate(action)
    //                     }
    //
    //                     else -> error("$action is not supported for $item")
    //                 }
    //             }
    //         }
    //     }
    //     return facts
    // }

    // private fun sanitizes(inst: JcInst, fact: Tainted): Boolean {
    // ...
    // }

    override fun obtainPossibleStartFacts(startStatement: JcInst): List<Fact> {
        // FIXME
        // TODO: handle TaintEntryPointSource
        return listOf(ZeroFact)
    }

    // TODO: rename / refactor
    // TODO: consider splitting into transmitTaintAssign / transmitTaintArgument
    // TODO: consider: moveTaint/copyTaint
    private fun transmitTaint(
        fact: Tainted,
        from: JcExpr,
        to: JcValue,
    ): List<Fact> {
        val toPath = to.toPathOrNull() ?: return emptyList() // FIXME: check, add comment
        val fromPath = from.toPathOrNull() ?: TODO() // TODO: how to handle it?

        // 'from' is tainted with 'fact':
        // TODO: replace with ==, in general case
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
        fact: Tainted,
        inst: JcInst,
    ): List<Fact> {
        // Pass-through:
        return listOf(fact)
    }

    // TODO: consider transmitTaintSequent / transmitTaintCall

    override fun obtainSequentFlowFunction(
        current: JcInst,
    ) = FlowFunction { fact ->
        if (fact == ZeroFact) {
            // FIXME: calling 'generates' here is not correct, since sequent flow function are NOT for calls,
            //        and 'generates' is only applicable for calls.
            return@FlowFunction listOf(ZeroFact) // + generates(current)
        }

        if (fact !is Tainted) {
            return@FlowFunction emptyList()
        }

        if (current is JcAssignInst) {
            transmitTaint(fact, current.rhv, current.lhv)
        } else {
            transmitTaintNormal(fact, current)
        }
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst, // unused?
    ) = FlowFunction { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")

        val config = cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
            ?.let { feature ->
                val callee = callExpr.method.method
                println("Extracting config for callee = $callee")
                feature.getConfigForMethod(callee)
            }

        // If 'fact' is ZeroFact, handle MethodSource. If there are no suitable MethodSource items, perform default.
        // For other facts (Tainted only?), handle PassThrough/Cleaner items.
        // TODO: what to do with "other facts" on CopyAllMarks/RemoveAllMarks?

        // TODO: the call-to-return flow function should also return (or somehow mark internally)
        //  whether we need to analyze the callee. For example, when we have MethodSource,
        //  PassThrough or Cleaner for a call statement, we do not need to analyze the callee at all.
        //  However, when we do not have such items in our config, we have to perform the whole analysis
        //  of the callee: calling call-to-start flow function, launching the analysis of the callee,
        //  awaiting for summary edges, and finally executing the exit-to-return flow function.
        //  In such case, the call-to-return flow function should return empty list of facts,
        //  since they are going to be "handled by the summary edge".

        // Handle MethodSource config items:
        if (fact == ZeroFact) {
            // return@FlowFunction listOf(ZeroFact) + generates(callStatement)
            if (config != null) {
                val conditionEvaluator = BasicConditionEvaluator(CallPositionToJcValueResolver(callStatement))
                val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))
                // TODO: replace with buildSet?
                val facts = mutableSetOf<Tainted>()
                for (item in config.filterIsInstance<TaintMethodSource>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        for (action in item.actionsAfter) {
                            when (action) {
                                is AssignMark -> {
                                    facts += actionEvaluator.evaluate(action)
                                }

                                else -> error("$action is not supported for $item")
                            }
                        }
                    }
                }
                return@FlowFunction facts + ZeroFact
            } else {
                return@FlowFunction listOf(ZeroFact)
            }
        }

        // FIXME: adhoc to satisfy types
        if (fact !is Tainted) {
            // TODO: what to return here?
            return@FlowFunction emptyList()
        }

        if (config == null) {
            return@FlowFunction emptyList()
        }

        val conditionEvaluator = FactAwareConditionEvaluator(
            fact,
            CallPositionToJcValueResolver(callStatement)
        )
        val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))
        val facts = mutableSetOf<Tainted>()

        for (item in config.filterIsInstance<TaintPassThrough>()) {
            if (item.condition.accept(conditionEvaluator)) {
                for (action in item.actionsAfter) {
                    when (action) {
                        is CopyMark -> {
                            facts += actionEvaluator.evaluate(action, fact)
                        }

                        is CopyAllMarks -> {
                            facts += actionEvaluator.evaluate(action, fact)
                        }

                        else -> error("$action is not supported for $item")
                    }
                }
            }
        }
        for (item in config.filterIsInstance<TaintCleaner>()) {
            if (item.condition.accept(conditionEvaluator)) {
                for (action in item.actionsAfter) {
                    when (action) {
                        is RemoveMark -> {
                            facts += actionEvaluator.evaluate(action, fact)
                        }

                        is RemoveAllMarks -> {
                            facts += actionEvaluator.evaluate(action, fact)
                        }

                        else -> error("$action is not supported for $item")
                    }
                }
            }
        }

        facts
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
                addAll(transmitTaint(fact, actual, formal))
            }

            // Transmit facts on instance ('instance' to 'this'):
            if (callExpr is JcInstanceCallExpr) {
                addAll(transmitTaint(fact, callExpr.instance, callee.thisInstance))
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
                addAll(transmitTaint(fact, formal, actual))
            }

            // Transmit facts on instance ('this' to 'instance'):
            if (callExpr is JcInstanceCallExpr) {
                addAll(transmitTaint(fact, callee.thisInstance, callExpr.instance))
            }

            // Transmit facts on static value:
            if (fact is Tainted && fact.variable.isStatic) {
                add(fact)
            }

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (exitStatement is JcReturnInst && callStatement is JcAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                exitStatement.returnValue?.let { returnValue ->
                    addAll(transmitTaint(fact, returnValue, callStatement.lhv))
                }
            }
        }
    }
}

class Manager(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver,
) {
    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdgeFact>()
    private val runners: MutableMap<UnitType, Ifds> = ConcurrentHashMap()

    fun newRunner(
        graph: JcApplicationGraph,
        unitResolver: UnitResolver,
        unit: UnitType,
        analyzer: Analyzer2,
    ): Ifds {
        check(unit !in runners) { "Runner for $unit already exists" }
        val runner = Ifds(graph, analyzer, this@Manager, unitResolver, unit)
        runners[unit] = runner
        return runner
    }

    suspend fun handleEvent(event: Event, runner: Ifds): Unit = coroutineScope {
        when (event) {
            is EdgeForOtherRunner -> {
                val method = event.edge.method
                val unit = unitResolver.resolve(method)
                val otherRunner = runners[unit] ?: error("No runner for $unit")
                otherRunner.submitNewEdge(event.edge)
            }

            is SubscriptionForSummaryEdges2 -> {
                summaryEdgesStorage
                    .getFacts(event.method)
                    .map { it.edge }
                    .map { Edge(it) }
                    .collect(event.collector)
            }

            is NewSummaryEdge -> {
                summaryEdgesStorage.send(SummaryEdgeFact(event.edge.toIfds()))
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

// TODO: replace with 'BeginAnalysis(val statement: Vertex)', where 'statement' is
//       the first instruction of the analyzed method together with a fact.
data class EdgeForOtherRunner(
    val edge: Edge,
) : Event {
    init {
        check(edge.from == edge.to) { "Edge for another runner must be a loop" }
    }
}

interface Analyzer2 {
    val flowFunctions: FlowFunctionsSpace2

    fun isSkipped(method: JcMethod): Boolean = false

    fun handleNewEdge(edge: Edge): List<Event>
    fun handleCrossUnitCall(caller: Vertex, callee: Vertex): List<Event>
}

class TaintAnalyzer(
    private val graph: JcApplicationGraph,
) : Analyzer2 {
    override val flowFunctions: FlowFunctionsSpace2 by lazy {
        TaintForwardFlowFunctions(graph.classpath)
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

    override fun handleCrossUnitCall(caller: Vertex, callee: Vertex): List<Event> = buildList {
        add(EdgeForOtherRunner(Edge(callee, callee)))
    }
}

class Ifds(
    private val graph: JcApplicationGraph,
    private val analyzer: Analyzer2,
    private val manager: Manager,
    private val unitResolver: UnitResolver,
    private val unit: UnitType,
) {
    private val flowSpace: FlowFunctionsSpace2 = analyzer.flowFunctions

    private val workList: Channel<Edge> = Channel(Channel.UNLIMITED)
    private val pathEdges: MutableSet<Edge> = mutableSetOf() // TODO: replace with concurrent set
    private val summaryEdges: MutableMap<Vertex, MutableSet<Vertex>> = mutableMapOf()
    private val callSitesOf: MutableMap<Vertex, MutableSet<Edge>> = mutableMapOf()

    fun run(startMethods: List<JcMethod>): Unit = runBlocking {
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

    fun stop() {
        workList.close()
    }

    fun submitNewEdge(edge: Edge): Unit = runBlocking {
        propagate(edge)
    }

    private suspend fun propagate(edge: Edge): Boolean {
        // TODO: replace comment with 'require' string argument
        // Propagated edge must be in the same unit:
        require(unitResolver.resolve(edge.method) == unit)

        if (pathEdges.add(edge)) {
            // Add edge to worklist:
            // FIXME: make 'propagate' suspend fun and use 'workList.send(edge)' here
            workList.send(edge)

            // Send edge to analyzer/manager:
            for (event in analyzer.handleNewEdge(edge)) {
                manager.handleEvent(event, this@Ifds)
            }

            return true
        }

        return false
    }

    private val JcMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    private data class BackInfo(
        val startVertex: Vertex,
        val caller: JcInst,
        val returnSite: JcInst,
    )

    private val backInfoForCallee: MutableMap<Vertex, BackInfo> = mutableMapOf()

    suspend fun handleSummaryEdge(edge: Edge) {
        val info = backInfoForCallee[edge.from]
        if (info != null) {
            handleSummaryEdge(edge, info.startVertex, info.caller, info.returnSite)
        }
    }

    private suspend fun handleSummaryEdge(
        edge: Edge,
        startVertex: Vertex,
        caller: JcInst,
        returnSite: JcInst,
    ) {
        // val calleeStartVertex = edge.from
        val (exit, exitFact) = edge.to
        val finalFacts = flowSpace
            .obtainExitToReturnSiteFlowFunction(caller, returnSite, exit)
            .compute(exitFact)
        for (returnSiteFact in finalFacts) {
            val returnSiteVertex = Vertex(returnSite, returnSiteFact)
            val newEdge = Edge(startVertex, returnSiteVertex)
            propagate(newEdge)
        }
    }

    private suspend fun tabulationAlgorithm() {
        for (edge in workList) {
            handle(edge)
        }
    }

    private suspend fun handle(currentEdge: Edge) {
        val (startVertex, currentVertex) = currentEdge
        val (current, currentFact) = currentVertex
        // TODO: replace with (current.callExpr != null)
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
                    // TODO: check whether we need to analyze the callee (or it was skipped due to MethodSource)
                    if (analyzer.isSkipped(callee)) {
                        logger.info { "Skipping method $callee" }
                    } else {
                        val factsAtCalleeStart = flowSpace
                            .obtainCallToStartFlowFunction(current, callee)
                            .compute(currentFact)
                        for (calleeStart in graph.entryPoints(callee)) {
                            for (calleeStartFact in factsAtCalleeStart) {
                                val calleeStartVertex = Vertex(calleeStart, calleeStartFact)

                                if (callee.isExtern) {
                                    // Initialize analysis of callee:
                                    // TODO: send Edge(calleeStartVertex, calleeStartVertex) loop-edge
                                    // for (event in analyzer.)

                                    // Subscribe on summary edges:
                                    val event = SubscriptionForSummaryEdges2(callee) {
                                        if (it.from == calleeStartVertex) {
                                            handleSummaryEdge(it, startVertex, current, returnSite)
                                        }
                                    }
                                    backInfoForCallee[calleeStartVertex] = BackInfo(startVertex, current, returnSite)
                                    manager.handleEvent(event, this@Ifds)
                                } else {
                                    // Save info about the call for summary edges that will be found later:
                                    callSitesOf.getOrPut(calleeStartVertex) { mutableSetOf() }.add(currentEdge)

                                    // Initialize analysis of callee:
                                    this@Ifds.run {
                                        val newEdge = Edge(calleeStartVertex, calleeStartVertex) // loop
                                        this@Ifds.propagate(newEdge)
                                    }

                                    // Handle already-found summary edges:
                                    for (exitVertex in summaryEdges[calleeStartVertex].orEmpty()) {
                                        val edge = Edge(calleeStartVertex, exitVertex)
                                        handleSummaryEdge(edge, startVertex, current, returnSite)
                                    }
                                    // val exits = summaryEdges[calleeStartVertex].orEmpty()
                                    // for ((exit, exitFact) in exits) {
                                    //     val finalFacts = flowSpace
                                    //         .obtainExitToReturnSiteFlowFunction(current, returnSite, exit)
                                    //         .compute(exitFact)
                                    //     for (returnSiteFact in finalFacts) {
                                    //         val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                                    //         val newEdge = Edge(startVertex, returnSiteVertex)
                                    //         propagate(newEdge)
                                    //     }
                                    // }
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
                            val callerStartVertex = callerPathEdge.from
                            val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                            val newEdge = Edge(callerStartVertex, returnSiteVertex)
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

fun main() {
    //
}
