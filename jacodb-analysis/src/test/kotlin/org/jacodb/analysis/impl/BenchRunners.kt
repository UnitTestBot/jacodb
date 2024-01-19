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
import org.jacodb.analysis.engine.ClassUnitResolver
import org.jacodb.analysis.engine.PackageUnitResolver
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.engine.runAnalysis2
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.api.JcByteCodeLocation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
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
        bench.use { it.analyze() }
    }
}

object OwaspBenchRunner {
    private fun loadOwaspJavaBench(): BenchCp {
        val owaspJavaPath = Path(object {}.javaClass.getResource("/owasp")!!.path)
        val runAll = true // make it 'false' to test on specific method(s) only

        return loadWebAppBenchCp(
            classes = owaspJavaPath / "classes",
            dependencies = owaspJavaPath / "deps",
            // unitResolver = PackageUnitResolver,
            unitResolver = ClassUnitResolver(true),
            // unitResolver = SingletonUnitResolver,
        ).apply {
            entrypointFilter = { method ->
                if (method.enclosingClass.packageName.startsWith("org.owasp.benchmark.testcode")) {
                    if (runAll) {
                        // All methods:
                        true
                    } else {
                        // Specific method:
                        // val specificMethod = "BenchmarkTest00008"
                        // val specificMethod = "BenchmarkTest00018"
                        // val specificMethod = "BenchmarkTest00024"
                        // val specificMethod = "BenchmarkTest00025"
                        // val specificMethod = "BenchmarkTest00026"
                        // val specificMethod = "BenchmarkTest00027"
                        // val specificMethod = "BenchmarkTest00032"
                        // val specificMethod = "BenchmarkTest00033"
                        // val specificMethod = "BenchmarkTest00034"
                        // val specificMethod = "BenchmarkTest00037"
                        // val specificMethod = "BenchmarkTest00043"
                        val specificMethod = "BenchmarkTest00105"
                        if (method.enclosingClass.simpleName == specificMethod) {
                            true
                        } else {
                            false
                        }

                        // // Methods with specific annotation:
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
        bench.use { it.analyze() }
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
        bench.use { it.analyze() }
    }
}

private class BenchCp(
    val cp: JcClasspath,
    val db: JcDatabase,
    val benchLocations: List<JcByteCodeLocation>,
    val unitResolver: UnitResolver,
    var reportFileName: String? = null,
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
        persistent("/tmp/index.db")
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

private fun BenchCp.analyze() {
    val useSpecificClass = false
    val startMethods = if (useSpecificClass) {
        val className = "org.owasp.benchmark.testcode.BenchmarkTest00032"
        logger.info { "Analyzing '$className'" }
        val clazz = cp.findClass(className)
        clazz.publicAndProtectedMethods().toList()
    } else {
        logger.info { "Filtering start methods..." }
        cp.publicClasses(benchLocations)
            .flatMap { it.publicAndProtectedMethods() }
            .filter(entrypointFilter)
            .toList()
    }

    logger.info { "Start the analysis with ${startMethods.size} start methods:" }
    for (method in startMethods) {
        logger.info { method }
    }
    analyzeTaint(cp, unitResolver, startMethods, reportFileName)
    logger.info { "Done the analysis with ${startMethods.size} start methods" }
}

private fun analyzeTaint(
    cp: JcClasspath,
    unitResolver: UnitResolver,
    startMethods: List<JcMethod>,
    reportFileName: String? = null,
) {
    val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
    runAnalysis2(graph, unitResolver, startMethods)
    // val vulnerabilities = runAnalysis(graph, unitResolver, newSqlInjectionRunnerFactory(), startMethods)
    // val report = SarifReport.fromVulnerabilities(vulnerabilities)
    // File(reportFileName ?: "report.sarif").outputStream().use { fileOutputStream ->
    //     report.encodeToStream(fileOutputStream)
    // }
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
        // TODO: some sinks are NOT found when filtering out constructors here
        // .filter { !it.isConstructor }
        .filter { it.isPublic || it.isProtected }
