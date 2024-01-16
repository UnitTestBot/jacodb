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

import org.jacodb.api.core.analysis.ApplicationGraph
import org.jacodb.api.core.cfg.CoreInst
import org.jacodb.api.core.cfg.CoreInstLocation
import org.jacodb.api.jvm.analysis.JcApplicationGraph

/**
 * Interface for flow functions -- mappings of kind DomainFact -> Collection of DomainFacts
 */
fun interface FlowFunctionInstance {
    fun compute(fact: DomainFact): Collection<DomainFact>
}

/**
 * An interface with which facts appearing in analyses should be marked
 */
interface DomainFact

/**
 * A special [DomainFact] that always holds
 */
object ZEROFact : DomainFact {
    override fun toString() = "[ZERO fact]"
}

/**
 * Implementations of the interface should provide all four kinds of flow functions mentioned in RHS95,
 * thus fully describing how the facts are propagated through the supergraph.
 */
interface FlowFunctionsSpace<Statement : CoreInst<*, Method, *>, Method> {
    /**
     * @return facts that may hold when analysis is started from [startStatement]
     * (these are needed to initiate worklist in ifds analysis)
     */
    fun obtainPossibleStartFacts(startStatement: Statement): Collection<DomainFact>
    fun obtainSequentFlowFunction(current: Statement, next: Statement): FlowFunctionInstance
    fun obtainCallToStartFlowFunction(callStatement: Statement, callee: Method): FlowFunctionInstance
    fun obtainCallToReturnFlowFunction(callStatement: Statement, returnSite: Statement): FlowFunctionInstance
    fun obtainExitToReturnSiteFlowFunction(callStatement: Statement, returnSite: Statement, exitStatement: Statement): FlowFunctionInstance
}

/**
 * [Analyzer] interface describes how facts are propagated and how [AnalysisDependentEvent]s are produced by these facts during
 * the run of tabulation algorithm by [BaseIfdsUnitRunner].
 *
 * Note that methods and properties of this interface may be accessed concurrently from different threads,
 * so the implementations should be thread-safe.
 *
 * @property flowFunctions a [FlowFunctionsSpace] instance that describes how facts are generated and propagated
 * during run of tabulation algorithm.
 */
interface Analyzer<Method, Location, Statement>
        where Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, *> {
    val flowFunctions: FlowFunctionsSpace<Statement, Method>

    /**
     * This method is called by [BaseIfdsUnitRunner] each time a new path edge is found.
     *
     * @return [AnalysisDependentEvent]s that are produced by this edge.
     * Usually these are [NewSummaryFact] events with [SummaryEdgeFact] or [VulnerabilityLocation] facts
     */
    fun handleNewEdge(edge: IfdsEdge<Method, Location, Statement>): List<AnalysisDependentEvent>

    /**
     * This method is called by [BaseIfdsUnitRunner] each time a new cross-unit called is observed.
     *
     * @return [AnalysisDependentEvent]s that are produced by this [fact].
     */
    fun handleNewCrossUnitCall(fact: CrossUnitCallFact<Method, Location, Statement>): List<AnalysisDependentEvent>

    /**
     * This method is called once by [BaseIfdsUnitRunner] when the propagation of facts is finished
     * (normally or due to cancellation).
     *
     * @return [AnalysisDependentEvent]s that should be processed after the facts propagation was completed
     * (usually these are some [NewSummaryFact]s).
     */
    fun handleIfdsResult(ifdsResult: IfdsResult): List<AnalysisDependentEvent>
}

/**
 * A functional interface that allows to produce [Analyzer] by [ApplicationGraph].
 *
 * It simplifies instantiation of [IfdsUnitRunnerFactory]s because this way you don't have to pass graph and reversed
 * graph to analyzers' constructors directly, relying on runner to do it by itself.
 */
fun interface AnalyzerFactory<Method, Location, Statement>
        where Location : CoreInstLocation<Method>,
              Statement : CoreInst<Location, Method, *> {
    fun newAnalyzer(graph: ApplicationGraph<Method, Statement>): Analyzer<Method, Location, Statement>
}