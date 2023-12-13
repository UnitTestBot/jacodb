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
import org.jacodb.analysis.engine.MethodUnitResolver
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.library.newSqlInjectionRunnerFactory
import org.jacodb.analysis.runAnalysis
import org.jacodb.analysis.sarif.SarifReport
import org.jacodb.api.JcByteCodeLocation
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.api.JcMethod
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
import kotlin.io.path.extension
import kotlin.io.path.walk

private val logger = KotlinLogging.logger {}

private class BenchCp(
    val cp: JcClasspath,
    val db: JcDatabase,
    val benchLocations: List<JcByteCodeLocation>,
) : AutoCloseable {
    override fun close() {
        cp.close()
        db.close()
    }
}

private fun loadBenchCp(path: String, dependencies: List<Path>): BenchCp = runBlocking {
    val benchCpFile = File(path)
    val cpFiles = listOf(benchCpFile) + dependencies.map { it.toFile() }

    val db = jacodb {
        useProcessJavaRuntime()
        installFeatures(InMemoryHierarchy, Usages)
        loadByteCode(cpFiles)
    }
    db.awaitBackgroundJobs()

    val defaultConfigResource = this.javaClass.getResourceAsStream("/defaultTaintConfig.json")!!
    val configJson = defaultConfigResource.bufferedReader().readText()
    val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
    val features = listOf(configurationFeature, UnknownClasses)
    val cp = db.classpath(cpFiles, features)
    val locations = cp.locations.filter { it.jarOrFolder == benchCpFile }

    BenchCp(cp, db, locations)
}

@OptIn(ExperimentalPathApi::class)
fun main() {
    val benchPath = "jacodb-analysis/webgoat/classes"
    val benchDeps = "jacodb-analysis/webgoat/lib"
    val dependencies = Path(benchDeps)
        .walk(PathWalkOption.INCLUDE_DIRECTORIES)
        .filter { it.extension == "jar" }

    val benchCp = loadBenchCp(benchPath, dependencies.toList())
    benchCp.use { analyzeBench(it.cp, it.benchLocations) }
}

private fun analyzeBench(cp: JcClasspath, benchLocations: List<JcByteCodeLocation>) {
    val startMethods = cp.publicClasses(benchLocations).flatMap { it.publicAndProtectedMethods() }.toList()
    logger.info { "Start analysis" }
    for (method in startMethods) {
        logger.info { method }
    }
    analyzeTaint(cp, startMethods)
}

private fun analyzeTaint(cp: JcClasspath, startMethods: List<JcMethod>) {
    val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
    val vulnerabilities = runAnalysis(graph, MethodUnitResolver, newSqlInjectionRunnerFactory(), startMethods)
    logger.info { "Found ${vulnerabilities.size} sinks" }
    for (vulnerability in vulnerabilities) {
        logger.info { "${vulnerability.location} in ${vulnerability.location.method}" }
    }

    val report = SarifReport.fromVulnerabilities(vulnerabilities)
    File("report.sarif").outputStream().use { fileOutputStream ->
        report.encodeToStream(fileOutputStream)
    }
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
