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

package org.jacodb.analysis
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mu.KLogging
import org.jacodb.analysis.analyzers.AliasAnalyzer
import org.jacodb.analysis.analyzers.NpeAnalyzer
import org.jacodb.analysis.analyzers.NpePrecalcBackwardAnalyzer
import org.jacodb.analysis.analyzers.TaintAnalysisNode
import org.jacodb.analysis.analyzers.TaintBackwardAnalyzer
import org.jacodb.analysis.analyzers.UnusedVariableAnalyzer
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.IfdsBaseUnitRunner
import org.jacodb.analysis.engine.IfdsUnitManager
import org.jacodb.analysis.engine.IfdsUnitRunner
import org.jacodb.analysis.engine.ParallelBidiIfdsUnitRunner
import org.jacodb.analysis.engine.TraceGraph
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.SimplifiedJcApplicationGraph
import org.jacodb.analysis.graph.reversed
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.features.usagesExt

@Serializable
data class DumpableVulnerabilityInstance(
    val vulnerabilityType: String,
    val sources: List<String>,
    val sink: String,
    val traces: List<List<String>>
)

@Serializable
data class DumpableAnalysisResult(val foundVulnerabilities: List<DumpableVulnerabilityInstance>)

data class VulnerabilityInstance(
    val vulnerabilityType: String,
    val traceGraph: TraceGraph
) {
    private fun JcInst.prettyPrint(): String {
        return "${toString()} (${location.method}:${location.lineNumber})"
    }

    fun toDumpable(maxPathsCount: Int): DumpableVulnerabilityInstance {
        return DumpableVulnerabilityInstance(
            vulnerabilityType,
            traceGraph.sources.map { it.statement.prettyPrint() },
            traceGraph.sink.statement.prettyPrint(),
            traceGraph.getAllTraces().take(maxPathsCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.prettyPrint() }
            }.toList()
        )
    }
}

fun List<VulnerabilityInstance>.toDumpable(maxPathsCount: Int = 3): DumpableAnalysisResult {
    return DumpableAnalysisResult(map { it.toDumpable(maxPathsCount) })
}

typealias AnalysesOptions = Map<String, String>

@Serializable
data class AnalysisConfig(val analyses: Map<String, AnalysesOptions>)

interface AnalysisEngine {
    fun analyze(): List<VulnerabilityInstance>
    fun addStart(method: JcMethod)
}

fun interface AnalysisEngineFactory {
    fun createAnalysisEngine(
        graph: JcApplicationGraph,
        unitResolver: UnitResolver<*>
    ): AnalysisEngine
}

val UnusedVariableAnalysisFactory = AnalysisEngineFactory { graph, unitResolver ->
    IfdsUnitManager(
        graph,
        unitResolver,
        IfdsBaseUnitRunner(UnusedVariableAnalyzer(graph))
    )
}

private fun createBidiIfdsEngine(
    forwardRunner: IfdsUnitRunner,
    backwardRunner: IfdsUnitRunner,
    graph: JcApplicationGraph,
    unitResolver: UnitResolver<*>
): AnalysisEngine {
    return IfdsUnitManager(
        graph,
        unitResolver,
        ParallelBidiIfdsUnitRunner(forwardRunner, backwardRunner)
    )
}

val NpeAnalysisFactory = AnalysisEngineFactory { graph, unitResolver ->
    createBidiIfdsEngine(
        IfdsBaseUnitRunner(NpeAnalyzer(graph)),
        IfdsBaseUnitRunner(NpePrecalcBackwardAnalyzer(graph.reversed)),
        graph,
        unitResolver
    )
}

class AliasAnalysisFactory(
    private val generates: (JcInst) -> List<TaintAnalysisNode>,
    private val isSink: (JcInst, DomainFact) -> Boolean,
) : AnalysisEngineFactory {
    override fun createAnalysisEngine(graph: JcApplicationGraph, unitResolver: UnitResolver<*>): AnalysisEngine {
        return createBidiIfdsEngine(
            IfdsBaseUnitRunner(AliasAnalyzer(graph.classpath, generates, isSink)),
            IfdsBaseUnitRunner(TaintBackwardAnalyzer(graph.reversed)),
            graph,
            unitResolver
        )
    }
}

fun buildApplicationGraph(classpath: JcClasspath, bannedPackagePrefixes: List<String>?) = runBlocking {
    val mainGraph = JcApplicationGraphImpl(classpath, classpath.usagesExt())
    if (bannedPackagePrefixes != null) {
        SimplifiedJcApplicationGraph(mainGraph, bannedPackagePrefixes)
    } else {
        SimplifiedJcApplicationGraph(mainGraph)
    }
}


internal val logger = object : KLogging() {}.logger
