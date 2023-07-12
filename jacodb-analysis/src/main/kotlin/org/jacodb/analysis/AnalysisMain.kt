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
import org.jacodb.analysis.analyzers.AliasAnalyzerFactory
import org.jacodb.analysis.analyzers.NpeAnalyzerFactory
import org.jacodb.analysis.analyzers.NpePrecalcBackwardAnalyzerFactory
import org.jacodb.analysis.analyzers.TaintAnalysisNode
import org.jacodb.analysis.analyzers.TaintBackwardAnalyzerFactory
import org.jacodb.analysis.analyzers.UnusedVariableAnalyzerFactory
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.IfdsBaseUnitRunner
import org.jacodb.analysis.engine.ParallelBidiIfdsUnitRunner
import org.jacodb.analysis.engine.SequentialBidiIfdsUnitRunner
import org.jacodb.analysis.engine.TraceGraph
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.SimplifiedJcApplicationGraph
import org.jacodb.api.JcClasspath
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

val UnusedVariableRunner = IfdsBaseUnitRunner(UnusedVariableAnalyzerFactory)

fun createNpeRunner(maxPathLength: Int = 5) = SequentialBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(NpeAnalyzerFactory(maxPathLength)),
    IfdsBaseUnitRunner(NpePrecalcBackwardAnalyzerFactory(maxPathLength)),
)

fun createAliasRunner(
    generates: (JcInst) -> List<TaintAnalysisNode>,
    isSink: (JcInst, DomainFact) -> Boolean,
    maxPathLength: Int = 5
) = ParallelBidiIfdsUnitRunner(
    IfdsBaseUnitRunner(AliasAnalyzerFactory(generates, isSink, maxPathLength)),
    IfdsBaseUnitRunner(TaintBackwardAnalyzerFactory(maxPathLength)),
)

fun createApplicationGraph(classpath: JcClasspath, bannedPackagePrefixes: List<String>?) = runBlocking {
    val mainGraph = JcApplicationGraphImpl(classpath, classpath.usagesExt())
    if (bannedPackagePrefixes != null) {
        SimplifiedJcApplicationGraph(mainGraph, bannedPackagePrefixes)
    } else {
        SimplifiedJcApplicationGraph(mainGraph)
    }
}


internal val logger = object : KLogging() {}.logger
