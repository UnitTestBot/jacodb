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

package org.jacodb.analysis.codegen

import mu.KotlinLogging
import org.jacodb.analysis.codegen.language.base.AnalysisVulnerabilityProvider
import org.jacodb.analysis.codegen.language.base.TargetLanguage
import org.jacodb.analysis.codegen.language.base.VulnerabilityInstance
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.Collections.min
import kotlin.io.path.notExists
import kotlin.io.path.useDirectoryEntries
import kotlin.random.Random

private val logger = KotlinLogging.logger { }

class AccessibilityCache(n: Int, private val graph: Map<Int, Set<Int>>) {
    private val used = Array(n) { 0 }
    private var currentWave = 0
    val badQuery = -1 to -1
    val badPath = listOf(-1)
    private var lastQuery = badQuery
    private var lastQueryPath = mutableListOf<Int>()

    private fun dfs(u: Int, target: Int): Boolean {
        used[u] = currentWave
        if (u == target) {
            lastQueryPath = mutableListOf(u)
            return true
        }

        for (v in graph.getOrDefault(u, emptySet())) {
            if (used[v] != currentWave && dfs(v, target)) {
                lastQueryPath.add(u)
                return true
            }
        }

        return false
    }

    fun isAccessible(u: Int, v: Int): Boolean {
        ++currentWave
        lastQuery = badQuery
        if (dfs(u, v)) {
            lastQueryPath.reverse()
            lastQuery = u to v
            return true
        }
        return false
    }


    fun getAccessPath(u: Int, v: Int): List<Int> {
        if (lastQuery == u to v || isAccessible(u, v))
            return lastQueryPath

        return badPath
    }
}

fun main(args: Array<String>) {
    assert(args.size in 5..6) {
        "vertices:Int edges:Int vulnerabilities:Int pathToProjectDir:String targetLanguage:String [clearTargetDir: Boolean]"
    }
    val n = args[0].toInt()
    val m = args[1].toInt()
    val k = args[2].toInt()

    assert(n in 2 .. 1000) { "currently big graphs not supported just in case" }
    assert(m in 1 .. 1000000) { "though we permit duplicated edges, do not overflow graph too much" }
    assert(k in 0 .. min(listOf(255, n, m)))

    val projectPath = Paths.get(args[3]).normalize()

    assert(projectPath.notExists() || projectPath.useDirectoryEntries { it.none() }) { "Provide path to directory which either does not exists or empty" }

    val targetLanguageString = args[4]
    val targetLanguageService = ServiceLoader.load(TargetLanguage::class.java)
    val targetLanguage = targetLanguageService.single { it.javaClass.simpleName == targetLanguageString }

    val vulnerabilityProviderService = ServiceLoader.load(AnalysisVulnerabilityProvider::class.java)
    val vulnerabilityProviders = mutableListOf<AnalysisVulnerabilityProvider>()

    for (analysis in vulnerabilityProviderService) {
        if (analysis.isApplicable(targetLanguage)) {
            logger.info { "analysis used: ${analysis.javaClass.simpleName}" }
            vulnerabilityProviders.add(analysis)
        }
    }

    logger.info { "analyses summary: vertices - $n, edges - $m, analyses instances - $k" }

    val randomSeed = arrayOf(n, m, k).contentHashCode()
    val randomer = Random(randomSeed)

    logger.info { "debug seed: $randomSeed" }

    val fullClear = args.getOrElse(5) { "false" }.toBooleanStrict()

    targetLanguage.unzipTemplateProject(projectPath, fullClear)

    val graph = mutableMapOf<Int, MutableSet<Int>>()

    var i = 0
    while (i < m) {
        val u = randomer.nextInt(n)
        val v = randomer.nextInt(n)

        if (u != v) {
            // TODO loops v->v?
            graph.getOrPut(u) { mutableSetOf() }.add(v)
            i++
        }
    }

    val accessibilityCache = AccessibilityCache(n, graph)
    val codeRepresentation = CodeRepresentation(targetLanguage)
    val generatedVulnerabilitiesList = mutableListOf<VulnerabilityInstance>()

    i = 0
    while (i < k) {
        val u = randomer.nextInt(n)
        val v = randomer.nextInt(n)
        val vulnerabilityIndex = randomer.nextInt(vulnerabilityProviders.size)
        val vulnerabilityProvider = vulnerabilityProviders[vulnerabilityIndex]

        if (accessibilityCache.isAccessible(u, v)) {
            val path = accessibilityCache.getAccessPath(u, v)
            val instance = vulnerabilityProvider.provideInstance(codeRepresentation) {
                createSource(u)
                for (j in 0 until path.lastIndex) {
                    val startOfEdge = path[j]
                    val endOfEdge = path[j + 1]
                    mutateVulnerability(startOfEdge, endOfEdge)
                    transitVulnerability(startOfEdge, endOfEdge)
                }
                createSink(v)
            }
            generatedVulnerabilitiesList.add(instance)
            i++
        }
    }

    codeRepresentation.dumpTo(projectPath)
    runGradleAssemble(targetLanguage, projectPath)
}

private fun runGradleAssemble(targetLanguage: TargetLanguage, projectPath: Path) {
    val zipName = targetLanguage.projectZipInResourcesName()
    val workingDir = File(projectPath.toFile(), File(zipName).nameWithoutExtension)
    checkJava(workingDir)
    if (!isWindows) {
        chmodGradlew(workingDir)
    }
    val gradlewScript =
        (if (isWindows) workingDir.absolutePath else "") +
                DOT + File.separator + "gradlew" + if (isWindows) ".bat" else ""
    runCmd(
        cmd = listOf(gradlewScript, "assemble"),
        errorPrefix = "problems with assembling",
        filePrefix = "gradlew",
        logPrefix = "gradle assembling",
        workingDir = workingDir
    )
}

private data class RunCmdResult(val output: File, val error: File)

private fun runCmd(
    cmd: List<String>,
    errorPrefix: String,
    filePrefix: String,
    logPrefix: String,
    workingDir: File,
    mergeOutput: Boolean = false
): RunCmdResult {
    val errorFileName = workingDir.absolutePath + File.separator + filePrefix + "Error"
    val outputFileName = workingDir.absolutePath + File.separator + filePrefix + "Output"
    val outputFile = File(outputFileName)
    val errorFile = if (mergeOutput) outputFile else File(errorFileName)
    try {
        val cmdBuilder =
            ProcessBuilder().directory(workingDir).redirectError(errorFile).redirectOutput(outputFile).command(cmd)
        val process = cmdBuilder.start()
        val pid = process.waitFor()
        val hasErrors = errorFile.length() != 0L
        if (pid != 0 && hasErrors) {
            logger.error { "$errorPrefix: check logs in - ${errorFile.path}" }
            throw IllegalStateException(errorPrefix)
        }
        logger.info {
            "$logPrefix: check more logs in - ${outputFile.path}"
        }
        return RunCmdResult(outputFile, errorFile)
    } catch (e: IOException) {
        logger.error { "$errorPrefix: check logs in - ${errorFile.path}" }
        errorFile.writeText(e.stackTraceToString())
        throw IllegalStateException(errorPrefix + " " + (e.message?.split(System.lineSeparator())?.get(0) ?: ""))
    }
}

class JavaVersion(version: String) : Comparable<JavaVersion> {
    private val numbers: IntArray

    init {
        val split = version
            .split("-".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()[0]
            .split("_".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()[0]
            .split("\\.".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
        numbers = IntArray(split.size)
        for (i in split.indices) {
            numbers[i] = Integer.valueOf(split[i])
        }
    }

    override operator fun compareTo(other: JavaVersion): Int {
        val maxLength = numbers.size.coerceAtLeast(other.numbers.size)
        for (i in 0 until maxLength) {
            val left = if (i < numbers.size) numbers[i] else 0
            val right = if (i < other.numbers.size) other.numbers[i] else 0
            if (left != right) {
                return if (left < right) -1 else 1
            }
        }
        return 0
    }

    override fun toString(): String {
        return numbers.joinToString(separator = ".")
    }
}

private fun checkJava(file: File) {
    val (output, _) = runCmd(
        cmd = listOf("java", "-version"),
        errorPrefix = "problems with java",
        filePrefix = "javaVersion",
        logPrefix = "java version checking",
        workingDir = file,
        mergeOutput = true
    )
    val javaDescription = BufferedReader(output.reader()).readLine()
    val versionElements = javaDescription.split(" ")[2].trim { it.isWhitespace() || it == '\"' }
    val symVersion = JavaVersion(versionElements)
    if (symVersion < JavaVersion("1.8")) {
        logger.error { "java version must being 8 or higher. current env java: $symVersion" }
        throw IllegalStateException("java version must being 8 or higher. current env java: $symVersion")
    }
}

private fun chmodGradlew(file: File) {
    runCmd(
        cmd = listOf("chmod", "+x", "gradlew"),
        errorPrefix = "problems with chmod",
        filePrefix = "chmod",
        logPrefix = "chmod command execution",
        workingDir = file
    )
}