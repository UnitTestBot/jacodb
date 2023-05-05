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
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.required
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import mu.KotlinLogging
import org.jacodb.analysis.analyzers.NpeAnalyzer
import org.jacodb.analysis.analyzers.TaintAnalysisNode
import org.jacodb.analysis.analyzers.TaintAnalyzer
import org.jacodb.analysis.engine.Analyzer
import org.jacodb.analysis.engine.TaintAnalysisWithPointsTo
import org.jacodb.analysis.graph.JcApplicationGraphImpl
import org.jacodb.analysis.graph.SimplifiedJcApplicationGraph
import org.jacodb.analysis.points2.AllOverridesDevirtualizer
import org.jacodb.analysis.points2.Devirtualizer
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.usagesExt
import org.jacodb.impl.jacodb
import java.io.File
import java.util.*

@Serializable
data class VulnerabilityInstance(
    val vulnerabilityType: String,
    val sources: List<String>,
    val sink: String,
    val realisationPaths: List<List<String>>
)

@Serializable
data class DumpableAnalysisResult(val foundVulnerabilities: List<VulnerabilityInstance>)

interface AnalysisEngine {
    fun analyze(): DumpableAnalysisResult
    fun addStart(method: JcMethod)
}

interface Points2Engine {
    fun obtainDevirtualizer(): Devirtualizer
}

interface Factory {
    val name: String
}

interface AnalysisEngineFactory : Factory {
    fun createAnalysisEngine(
        graph: JcApplicationGraph,
        points2Engine: Points2Engine,
    ): AnalysisEngine
}

abstract class FlowDroidFactory : AnalysisEngineFactory {

    protected abstract fun getAnalyzer(graph: JcApplicationGraph): Analyzer

    override fun createAnalysisEngine(
        graph: JcApplicationGraph,
        points2Engine: Points2Engine,
    ): AnalysisEngine {
        val analyzer = getAnalyzer(graph)
        val instance = TaintAnalysisWithPointsTo(graph, analyzer, points2Engine)

        return instance
    }

    override val name: String
        get() = "JacoDB-FlowDroid"
}

class NPEAnalysisFactory : FlowDroidFactory() {
    override fun getAnalyzer(graph: JcApplicationGraph): Analyzer {
        return NpeAnalyzer(graph.classpath, graph, graph)
    }
}

class TaintAnalysisFactory(private val generates: (JcInst) -> List<TaintAnalysisNode>) : FlowDroidFactory() {
    override fun getAnalyzer(graph: JcApplicationGraph): Analyzer {
        return TaintAnalyzer(graph.classpath, graph, graph, generates)
    }
}

interface Points2EngineFactory : Factory {
    fun createPoints2Engine(graph: JcApplicationGraph): Points2Engine
}

interface GraphFactory : Factory {
    fun createGraph(classpath: JcClasspath): JcApplicationGraph

    fun createGraph(classpath: List<File>, cacheDir: File): JcApplicationGraph = runBlocking {
        val classpathHash = classpath.toString().hashCode()
        val persistentPath = cacheDir.resolve("jacodb-for-$classpathHash")

        val jcdb = jacodb {
            loadByteCode(classpath)
            persistent(persistentPath.absolutePath)
            installFeatures(InMemoryHierarchy, Usages)
        }
        val cp = jcdb.classpath(classpath)
        createGraph(cp)
    }
}

class JcSimplifiedGraphFactory(
    private val bannedPackagePrefixes: List<String>? = null
) : GraphFactory {
    override val name: String = "JacoDB-graph simplified for IFDS"

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

class JcNaivePoints2EngineFactory : Points2EngineFactory {
    override fun createPoints2Engine(
        graph: JcApplicationGraph,
    ): Points2Engine {
        val cp = graph.classpath
        return AllOverridesDevirtualizer(graph, cp)
    }

    override val name: String
        get() = "JacoDB-P2-Naive"
}

inline fun <reified T : Factory> loadFactories(): List<T> {
    assert(T::class.java != Factory::class.java)
    return ServiceLoader.load(T::class.java).toList()
}

private inline fun <reified T : Factory> factoryChoice(): ArgType.Choice<T> {
    val factories = loadFactories<T>()
    val nameToFactory = { requiredFactoryName: String -> factories.single { it.name == requiredFactoryName } }
    val factoryToName = { factory: T -> factory.name }

    return ArgType.Choice(factories, nameToFactory, factoryToName)
}

private val logger = KotlinLogging.logger {}


class AnalysisMain {
    fun run(args: List<String>) = main(args.toTypedArray())
}

fun main(args: Array<String>) {
    val parser = ArgParser("taint-analysis")
    val classpath by parser.option(
        ArgType.String,
        fullName = "classpath",
        shortName = "cp",
        description = "Classpath for analysis. Used by JacoDB."
    ).required()
    val graphFactory by parser.option(
        factoryChoice<GraphFactory>(),
        fullName = "graph-type",
        shortName = "g",
        description = "Type of code graph to be used by analysis."
    ).required()
    val engineFactory by parser.option(
        factoryChoice<AnalysisEngineFactory>(),
        fullName = "engine",
        shortName = "e",
        description = "Type of IFDS engine."
    ).required()
    val points2Factory by parser.option(
        factoryChoice<Points2EngineFactory>(),
        fullName = "points2",
        shortName = "p2",
        description = "Type of points-to engine."
    ).required()
    val cacheDirPath by parser.option(
        ArgType.String,
        fullName = "cache-directory",
        shortName = "c",
        description = "Directory with caches for analysis. All parent directories will be created if not exists. Directory will be created if not exists. Directory must be empty."
    ).required()
    val outputPath by parser.option(
        ArgType.String,
        fullName = "output",
        shortName = "o",
        description = "File where analysis report will be written. All parent directories will be created if not exists. File will be created if not exists. Existing file will be overwritten."
    ).required()

    parser.parse(args)

    val outputFile = File(outputPath)

    if (outputFile.exists() && outputFile.isDirectory) {
        throw IllegalArgumentException("Provided path for output file is directory, please provide correct path")
    } else if (outputFile.exists()) {
        logger.info { "Output file $outputFile already exists, results will be overwritten" }
    } else {
        outputFile.parentFile.mkdirs()
    }

    val cacheDir = File(cacheDirPath)

    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }

    if (!cacheDir.isDirectory) {
        throw IllegalArgumentException("Provided path to cache directory is not directory")
    }

    val classpathAsFiles = classpath.split(File.pathSeparatorChar).sorted().map { File(it) }
    val graph = graphFactory.createGraph(classpathAsFiles, cacheDir)
    val points2Engine = points2Factory.createPoints2Engine(graph)
    val analysisEngine = engineFactory.createAnalysisEngine(graph, points2Engine)
    val analysisResult = analysisEngine.analyze()
    val json = Json { prettyPrint = true }

    outputFile.outputStream().use { fileOutputStream ->
        json.encodeToStream(analysisResult, fileOutputStream)
    }
}