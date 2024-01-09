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

package org.jacodb.analysis.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.engine.PackageUnitResolver
import org.jacodb.analysis.engine.SingletonUnitResolver
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.library.newSqlInjectionRunnerFactory
import org.jacodb.analysis.runAnalysis
import org.jacodb.analysis.sarif.SarifReport
import org.jacodb.api.JcByteCodeLocation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.packageName
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import org.jacodb.taint.configuration.TaintConfigurationFeature
import java.io.File
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.walk

private val logger = KotlinLogging.logger {}

object WebGoatBenchRunner {
    private fun loadWebGoatBench(): BenchCp {
        val webGoatDir = Path(object {}.javaClass.getResource("/webgoat")!!.path)
        return loadWebAppBenchCp(
            classes = webGoatDir / "classes",
            dependencies = webGoatDir / "deps",
            unitResolver = PackageUnitResolver
        ).apply {
            entrypointFilter = { method ->
                if (method.enclosingClass.packageName.startsWith("org.owasp.webgoat.lessons")) {
                    true
                } else {
                    false
                }
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val bench = loadWebGoatBench()
        bench.use { analyzeBench(it) }
    }
}

object OwaspBenchRunner {
    private fun loadOwaspJavaBench(): BenchCp {
        val owaspJavaPath = Path(object {}.javaClass.getResource("/owasp")!!.path)
        val runAll = true // make it 'false' to test on specific method(s) only
        return loadWebAppBenchCp(
            classes = owaspJavaPath / "classes",
            dependencies = owaspJavaPath / "deps",
            unitResolver = PackageUnitResolver,
            // unitResolver = SingletonUnitResolver
        ).apply {
            entrypointFilter = { method ->
                if (method.enclosingClass.packageName.startsWith("org.owasp.benchmark.testcode")) {
                    if (runAll) {
                        // All methods:
                        true
                    } else {
                        // Specific method:
                        val specificMethod = "BenchmarkTest00008" // ok
                        // val specificMethod = "BenchmarkTest00018" // ok
                        // val specificMethod = "BenchmarkTest00024" // ok
                        // val specificMethod = "BenchmarkTest00025" // ok?
                        // val specificMethod = "BenchmarkTest00026" // ok?
                        // val specificMethod = "BenchmarkTest00027" // ok?
                        // val specificMethod = "BenchmarkTest00032" // problems: isEmpty, String[], Map::get
                        // val specificMethod = "BenchmarkTest00033" // problems: isEmpty, String[], Map::get
                        // val specificMethod = "BenchmarkTest00034" // problems: isEmpty, String[], Map::get
                        // val specificMethod = "BenchmarkTest00037" // 0 facts, problems: String[]
                        // val specificMethod = "BenchmarkTest00043" // 0 facts
                        if (method.enclosingClass.simpleName == specificMethod) {
                            true
                        } else {
                            false
                        }

                        // Methods with specific annotation:
                        // // println("Annotations of $method: ${method.enclosingClass.annotations.map{it.name}}")
                        // method.enclosingClass.annotations.any { annotation ->
                        //     if (annotation.name == "javax.servlet.annotation.WebServlet") {
                        //         // println("$method has annotation ${annotation.name} with values ${annotation.values}")
                        //         (annotation.values["value"]!! as List<String>)[0].startsWith("/sqli-")
                        //     } else {
                        //         false
                        //     }
                        // }
                    }
                } else {
                    false
                }
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val bench = loadOwaspJavaBench()
        bench.use { analyzeBench(it) }
    }
}

object ShopizerBenchRunner {
    private fun loadShopizerBench(): BenchCp {
        val shopizerPath = Path(object {}.javaClass.getResource("/shopizer")!!.path)
        return loadWebAppBenchCp(
            classes = shopizerPath / "classes",
            dependencies = shopizerPath / "deps",
            unitResolver = PackageUnitResolver
        ).apply {
            entrypointFilter = { true }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val bench = loadShopizerBench()
        bench.use { analyzeBench(it) }
    }
}

private class BenchCp(
    val cp: JcClasspath,
    val db: JcDatabase,
    val benchLocations: List<JcByteCodeLocation>,
    val unitResolver: UnitResolver,
    var entrypointFilter: (JcMethod) -> Boolean = { true },
) : AutoCloseable {
    override fun close() {
        cp.close()
        db.close()
    }
}

private fun loadBenchCp(
    classes: List<File>,
    dependencies: List<File>,
    unitResolver: UnitResolver,
): BenchCp = runBlocking {
    val cpFiles = classes + dependencies

    val db = jacodb {
        useProcessJavaRuntime()
        installFeatures(InMemoryHierarchy, Usages)
        loadByteCode(cpFiles)
    }
    db.awaitBackgroundJobs()

    val defaultConfigResource = this.javaClass.getResourceAsStream("/defaultTaintConfig.json")!!
    var configJson = defaultConfigResource.bufferedReader().readText()
    val additionalConfigResource = this.javaClass.getResourceAsStream("/additional.json")
    if (additionalConfigResource != null) {
        val additionalConfigJson = additionalConfigResource.bufferedReader().readText()
        val configJsonLines = configJson.lines().toMutableList()
        if (configJsonLines.last().isEmpty()) {
            configJsonLines.removeLast()
        }
        check(configJsonLines.last() == "]")
        val additionalConfigJsonLines = additionalConfigJson.lines().toMutableList()
        if (additionalConfigJsonLines.last().isEmpty()) {
            additionalConfigJsonLines.removeLast()
        }
        check(additionalConfigJsonLines.first() == "[")
        check(additionalConfigJsonLines.last() == "]")
        if (additionalConfigJsonLines.size > 2) {
            configJsonLines.removeLast()
            configJsonLines[configJsonLines.size - 1] = configJsonLines[configJsonLines.size - 1] + ","
            for (line in additionalConfigJsonLines.subList(1, additionalConfigJsonLines.size - 1)) {
                configJsonLines.add(line)
            }
            configJsonLines.add("]")
        }
        configJson = configJsonLines.joinToString("\n")
    }
    val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
    val features = listOf(configurationFeature, UnknownClasses)
    val cp = db.classpath(cpFiles, features)
    val locations = cp.locations.filter { it.jarOrFolder in classes }

    BenchCp(cp, db, locations, unitResolver)
}

@OptIn(ExperimentalPathApi::class)
private fun loadWebAppBenchCp(
    classes: Path,
    dependencies: Path,
    unitResolver: UnitResolver,
): BenchCp =
    loadBenchCp(
        classes = listOf(classes.toFile()),
        dependencies = dependencies
            .walk(PathWalkOption.INCLUDE_DIRECTORIES)
            .filter { it.extension == "jar" }
            .map { it.toFile() }
            .toList(),
        unitResolver = unitResolver
    )

private fun analyzeBench(benchmark: BenchCp) {
    val startMethods = benchmark.cp.publicClasses(benchmark.benchLocations)
        .flatMap { it.publicAndProtectedMethods() }
        .filter { benchmark.entrypointFilter(it) }
        .toList()
    logger.info { "Start analysis" }
    for (method in startMethods) {
        logger.info { method }
    }
    analyzeTaint(benchmark.cp, benchmark.unitResolver, startMethods)
}

private fun analyzeTaint(
    cp: JcClasspath,
    unitResolver: UnitResolver,
    startMethods: List<JcMethod>,
) {
    val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
    val vulnerabilities = runAnalysis(graph, unitResolver, newSqlInjectionRunnerFactory(), startMethods)
    logger.info { "Total found ${vulnerabilities.size} sinks" }
    for (vulnerability in vulnerabilities) {
        logger.info { "${vulnerability.location} in ${vulnerability.location.method}" }
    }

    val report = SarifReport.fromVulnerabilities(vulnerabilities)
    File("report.sarif").outputStream().use { fileOutputStream ->
        report.encodeToStream(fileOutputStream)
    }

    logger.info { "ALL DONE" }
}

private fun JcClasspath.publicClasses(locations: List<JcByteCodeLocation>): Sequence<JcClassOrInterface> =
    locations
        .asSequence()
        .flatMap { it.classNames ?: emptySet() }
        .mapNotNull { findClassOrNull(it) }
        .filterNot { it is JcUnknownClass }
        .filterNot { it.isAbstract || it.isInterface || it.isAnonymous }

private fun JcClassOrInterface.publicAndProtectedMethods(): Sequence<JcMethod> =
    declaredMethods.asSequence()
        .filter { it.instList.size > 0 }
        .filter { it.isPublic || it.isProtected }
