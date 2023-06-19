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
import org.jacodb.analysis.analyzers.AliasAnalyzer
import org.jacodb.analysis.analyzers.NpeAnalyzer
import org.jacodb.analysis.analyzers.TaintAnalysisNode
import org.jacodb.analysis.analyzers.UnusedVariableAnalyzer
import org.jacodb.analysis.engine.Analyzer
import org.jacodb.analysis.engine.BidiIfdsForTaintAnalysis
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.IfdsUnitInstance
import org.jacodb.analysis.engine.IfdsUnitTraverser
import org.jacodb.analysis.engine.SingletonUnitResolver
import org.jacodb.analysis.engine.TaintRealisationsGraph
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.SimplifiedJcApplicationGraph
import org.jacodb.analysis.points2.AllOverridesDevirtualizer
import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.features.usagesExt
import java.util.*

@Serializable
data class DumpableVulnerabilityInstance(
    val vulnerabilityType: String,
    val sources: List<String>,
    val sink: String,
    val realisationPaths: List<List<String>>
)

@Serializable
data class DumpableAnalysisResult(val foundVulnerabilities: List<DumpableVulnerabilityInstance>)

data class VulnerabilityInstance(
    val vulnerabilityType: String,
    val realisationsGraph: TaintRealisationsGraph
) {
    private fun JcInst.prettyPrint(): String {
        return "${toString()} (${location.method.enclosingClass.name}#${location.method.name}:${location.lineNumber})"
    }

    fun toDumpable(maxPathsCount: Int): DumpableVulnerabilityInstance {
        return DumpableVulnerabilityInstance(
            vulnerabilityType,
            realisationsGraph.sources.map { it.statement.prettyPrint() },
            realisationsGraph.sink.statement.prettyPrint(),
            realisationsGraph.getAllPaths().take(maxPathsCount).map { intermediatePoints ->
                intermediatePoints.map { it.statement.prettyPrint() }
            }.toList()
        )
    }
}

fun List<VulnerabilityInstance>.toDumpable(maxPathsCount: Int = 10): DumpableAnalysisResult {
    return DumpableAnalysisResult(map { it.toDumpable(maxPathsCount) })
}

typealias AnalysesOptions = Map<String, String>

@Serializable
data class AnalysisConfig(val analyses: Map<String, AnalysesOptions>)

interface AnalysisEngine {
    fun analyze(): List<VulnerabilityInstance>
    fun addStart(method: JcMethod)
}

interface Factory {
    val name: String
}

interface AnalysisEngineFactory : Factory {
    fun createAnalysisEngine(
        graph: JcApplicationGraph,
        devirtualizer: Devirtualizer,
    ): AnalysisEngine
}

class UnusedVariableAnalysisFactory : AnalysisEngineFactory {
    override fun createAnalysisEngine(graph: JcApplicationGraph, devirtualizer: Devirtualizer): AnalysisEngine {
        return IfdsUnitTraverser(
            graph,
            UnusedVariableAnalyzer(graph),
            SingletonUnitResolver,
            devirtualizer,
            IfdsUnitInstance
        )
    }

    override val name: String
        get() = "unused-variable"
}

abstract class FlowDroidFactory : AnalysisEngineFactory {

    protected abstract val JcApplicationGraph.analyzer: Analyzer

    override fun createAnalysisEngine(
        graph: JcApplicationGraph,
        devirtualizer: Devirtualizer,
    ): AnalysisEngine {
        val analyzer = graph.analyzer
        return IfdsUnitTraverser(graph, analyzer, SingletonUnitResolver, devirtualizer, BidiIfdsForTaintAnalysis)
    }

    override val name: String
        get() = "flow-droid"
}

class NPEAnalysisFactory : FlowDroidFactory() {
    override val JcApplicationGraph.analyzer: Analyzer
        get() {
            return NpeAnalyzer(this)
        }
}

class AliasAnalysisFactory(
    private val generates: (JcInst) -> List<TaintAnalysisNode>,
    private val isSink: (JcInst, DomainFact) -> Boolean,
) : FlowDroidFactory() {
    override val JcApplicationGraph.analyzer: Analyzer
        get() {
            return AliasAnalyzer(this, generates, isSink)
        }
}

interface DevirtualizerFactory : Factory {
    fun createDevirtualizer(graph: JcApplicationGraph): Devirtualizer
}

interface GraphFactory : Factory {
    fun createGraph(classpath: JcClasspath): JcApplicationGraph
}

class JcSimplifiedGraphFactory(
    private val bannedPackagePrefixes: List<String>? = null
) : GraphFactory {
    override val name: String = "ifds-simplification"

    override fun createGraph(
        classpath: JcClasspath
    ): JcApplicationGraph = runBlocking {
        val mainGraph = JcApplicationGraphImpl(classpath, classpath.usagesExt())
        if (bannedPackagePrefixes != null) {
            SimplifiedJcApplicationGraph(mainGraph, bannedPackagePrefixes)
        } else {
            SimplifiedJcApplicationGraph(mainGraph)
        }
    }
}

object JcNaiveDevirtualizerFactory : DevirtualizerFactory {
    override fun createDevirtualizer(
        graph: JcApplicationGraph,
    ): Devirtualizer {
        val cp = graph.classpath
        return AllOverridesDevirtualizer(graph, cp)
    }

    override val name: String
        get() = "naive-p2"
}

inline fun <reified T : Factory> loadFactories(): List<T> {
    assert(T::class.java != Factory::class.java)
    return ServiceLoader.load(T::class.java).toList()
}
