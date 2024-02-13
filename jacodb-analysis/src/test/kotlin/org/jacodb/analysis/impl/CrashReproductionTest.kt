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

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.jacodb.analysis.engine.IfdsUnitRunnerFactory
import org.jacodb.analysis.engine.UnitResolver
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.analysis.graph.reversed
import org.jacodb.analysis.impl.*
import org.jacodb.analysis.library.SingletonUnitResolver
import org.jacodb.analysis.library.analyzers.SliceTaintAnalyzer
import org.jacodb.analysis.library.newSliceTaintRunnerFactory
import org.jacodb.analysis.logger
import org.jacodb.analysis.runAnalysis
import org.jacodb.analysis.sarif.sarifReportFromVulnerabilities
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.jacodb.analysis.engine.VulnerabilityInstance
import org.jacodb.analysis.library.*
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.*
import org.jacodb.api.ext.cfg.arrayRef
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.cfg.fieldRef
import org.jacodb.impl.features.Usages
import org.jacodb.testing.WithDB
import org.joda.time.DateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.Arguments
import java.io.File
import java.nio.file.Path
import java.util.stream.Stream
import java.util.zip.ZipFile

@Serializable
data class CrashPackApplicationVersion(
    @SerialName("src_url")
    val srcUrl: String,
    val version: String
)

@Serializable
data class CrashPackApplication(
    val name: String,
    val url: String,
    val versions: Map<String, CrashPackApplicationVersion>
)

@Serializable
data class CrashPackCrash(
    val application: String, // "JFreeChart"
    @SerialName("buggy_frame")
    val buggyFrame: String, // 6
    @SerialName("fixed_commit")
    val fixedCommit: String,
    val id: String, // "ES-14457"
    val issue: String, // "https://github.com/elastic/elasticsearch/issues/14457"
    @SerialName("target_frames")
    val targetFrames: String, // ".*elasticsearch.*"
    val version: String,
    @SerialName("version_fixed")
    val versionFixed: String,
)

@Serializable
data class CrashPack(
    val applications: Map<String, CrashPackApplication>,
    val crashes: Map<String, CrashPackCrash>
)

@Serializable
data class TraceEntry(
    val className: String,
    val methodName: String,
    val methodDesc: String,
    val instructionIdx: Int
)

@Serializable
data class TraceException(
    val className: String
)

@Serializable
data class CrashTrace(
    val original: String,
    val entries: List<List<TraceEntry>>,
    val exception: TraceException
)


private val json = Json {
    prettyPrint = true
}

val crashPackPath = Path("/Users/rustamazimov/Documents") / "JCrashPack"

const val traceFileName = "traces_jcdb_1.4.json"

@OptIn(ExperimentalSerializationApi::class)
fun parseCrashTraces(crashPackPath: Path, crashPack: CrashPack) {
    val current = loadCrashTraces(crashPackPath) ?: emptyMap()

    val parsed = runBlocking {
        crashPack.crashes.values.mapIndexedNotNull { index, it ->
            println("PARSE ${it.id} | $index / ${crashPack.crashes.size}")
            try {
                val trace = current[it.id]
                    ?: parseTrace(crashPackPath, it)
                    ?: return@mapIndexedNotNull null
                it to trace
            } catch (ex: Throwable) {
                System.err.println(ex)
                null
            }
        }
    }

    println("PARSED ${parsed.size} (${parsed.count { it.second.entries.size == 1 }}) TOTAL ${crashPack.crashes.size}")

    val traces = parsed.associate { it.first.id to it.second }

    val crashPackTracesPath = crashPackPath / traceFileName
    json.encodeToStream(traces, crashPackTracesPath.outputStream())
}

@OptIn(ExperimentalSerializationApi::class)
fun loadCrashTraces(crashPackPath: Path): Map<String, CrashTrace>? {
    val crashPackTracesPath = crashPackPath / traceFileName
    if (!crashPackTracesPath.exists()) return null
    return json.decodeFromStream(crashPackTracesPath.inputStream())
}

private suspend fun parseTrace(crashPackPath: Path, crash: CrashPackCrash): CrashTrace? {
    val crashLog = crashPackPath / "crashes" / crash.application / crash.id / "${crash.id}.log"
    val crashCp = crashPackPath / "applications" / crash.application / crash.version / "bin"

    val cpFiles = crashCp.listDirectoryEntries("*.jar").map { it.toFile() }

    jacodb {
        useProcessJavaRuntime()
        installFeatures(InMemoryHierarchy)
        installFeatures(Usages)
        loadByteCode(cpFiles)
    }.use { db ->
        db.classpath(cpFiles).use { cp ->
            val trace = crashLog.readText()
            return parseTrace(cp, trace, crash)
        }
    }
}

data class UnresolvedTraceEntry(val clsName: String, val methodName: String, val line: Int)
data class ClassTraceEntry(val cls: JcClassOrInterface, val unresolved: UnresolvedTraceEntry)
data class MethodTraceEntry(val cls: JcClassOrInterface, val method: JcMethod, val line: Int)
data class ResolvedTraceEntry(val cls: JcClassOrInterface, val method: JcMethod, val inst: JcInst)

private fun parseTrace(cp: JcClasspath, trace: String, crash: CrashPackCrash): CrashTrace? {
    val allTraceEntries = trace.lines().map { it.trim() }
    val relevantTracePattern = Regex(crash.targetFrames)
    val relevantTrace = allTraceEntries.dropLastWhile { !relevantTracePattern.matches(it) }

    val exceptionName = relevantTrace.first()
        .substringBefore(':').trim()
        .substringAfterLast(' ').trim()

    val exceptionType = cp.findClass(exceptionName)

    val rawTraceEntries = relevantTrace
        .drop(1)
        .map { it.removePrefix("at").trim() }
        .map {
            val lineNumber = it.substringAfterLast(':').trim(')').toInt()
            val classWithMethod = it.substringBefore('(')
            val className = classWithMethod.substringBeforeLast('.')
            val methodName = classWithMethod.substringAfterLast('.')
            UnresolvedTraceEntry(className, methodName, lineNumber)
        }
        .asReversed()

    val classTrace = resolveTraceClasses(cp, rawTraceEntries) ?: return null
    val methodTrace = resolveTraceMethods(classTrace) ?: return null
    val resolvedTraces = resolveTraceInstructions(exceptionType, methodTrace) ?: return null

    val serializableTraces = serializeTraces(resolvedTraces)
    return CrashTrace(trace, serializableTraces, TraceException(exceptionType.name))
}

private fun serializeTraces(traces: List<List<ResolvedTraceEntry>>): List<List<TraceEntry>> =
    traces.map { resolvedTrace ->
        resolvedTrace.map {
            TraceEntry(it.cls.name, it.method.name, it.method.description, it.inst.location.index)
        }
    }

private fun deserializeTraces(cp: JcClasspath, traces: List<List<TraceEntry>>): List<List<ResolvedTraceEntry>>? {
    return traces.map { trace ->
        trace.map { entry ->
            val cls = cp.findClassOrNull(entry.className) ?: return null
            val method = cls.findMethodOrNull(entry.methodName, entry.methodDesc) ?: return null
            val instruction = method.instList.singleOrNull { it.location.index == entry.instructionIdx } ?: return null
            ResolvedTraceEntry(cls, method, instruction)
        }
    }
}

private fun resolveTraceClasses(cp: JcClasspath, trace: List<UnresolvedTraceEntry>): List<ClassTraceEntry>? {
    return trace.map {
        ClassTraceEntry(
            cls = cp.findClassOrNull(it.clsName) ?: return null,
            unresolved = it
        )
    }
}

private fun resolveTraceMethods(trace: List<ClassTraceEntry>): List<MethodTraceEntry>? {
    val result = mutableListOf<MethodTraceEntry>()

    for (entry in trace) {
        val methodsWithSameName = entry.cls.declaredMethods.filter { it.name == entry.unresolved.methodName }
        if (methodsWithSameName.size == 1) {
            result += MethodTraceEntry(entry.cls, methodsWithSameName.single(), entry.unresolved.line)
            continue
        }

        val methodsWithLine = methodsWithSameName.filter {
            val lines = it.instList.mapTo(hashSetOf()) { it.lineNumber }
            entry.unresolved.line in lines
        }

        if (methodsWithLine.size == 1) {
            result += MethodTraceEntry(entry.cls, methodsWithLine.single(), entry.unresolved.line)
            continue
        }

        logger.warn { "Can't identify method" }
        return null
    }

    return result
}

private fun resolveTraceInstructions(
    crashException: JcClassOrInterface,
    trace: List<MethodTraceEntry>
): List<List<ResolvedTraceEntry>>? {
    val resolved = mutableListOf<List<ResolvedTraceEntry>>()

    for ((entry, nextEntry) in trace.zipWithNext()) {
        val correctCallInstructions = entry.method.instList.filter {
            it.hasMethodCall(nextEntry.method)
        }

        if (correctCallInstructions.size == 1) {
            resolved += listOf(ResolvedTraceEntry(entry.cls, entry.method, correctCallInstructions.single()))
            continue
        }

        val sameLineInstructions = correctCallInstructions.filter { it.lineNumber == entry.line }
        if (sameLineInstructions.isNotEmpty()) {
            resolved += sameLineInstructions.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
            continue
        }

        val nextLineInstructions = correctCallInstructions.filter { it.lineNumber == entry.line + 1 }
        if (nextLineInstructions.size == 1) {
            resolved += listOf(ResolvedTraceEntry(entry.cls, entry.method, nextLineInstructions.single()))
            continue
        }

        logger.warn { "Can't identify intermediate instruction" }
        return null
    }

    resolved += resolveTraceCrashInstruction(crashException, trace.last()) ?: return null

    var result = resolved.first().map { listOf(it) }
    for (entry in resolved.drop(1)) {
        val current = result
        result = entry.flatMap { resolvedEntry -> current.map { it + resolvedEntry } }
    }

    return result
}

private fun resolveTraceCrashInstruction(
    crashException: JcClassOrInterface,
    entry: MethodTraceEntry
): List<ResolvedTraceEntry>? {

    if (crashException == crashException.classpath.findClassOrNull("java.lang.ArrayIndexOutOfBoundsException")) {
        val iobInstructions = entry.method.instList.filter { it.canProduceIob() }
        if (iobInstructions.isEmpty()) {
            logger.warn { "Can't identify crash source instruction: ${crashException.name}" }
            return null
        }

        val instructions = iobInstructions
            .filter { it.lineNumber == entry.line }
            .takeIf { it.isNotEmpty() }
            ?: iobInstructions

        return instructions.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
    }

    if (crashException == crashException.classpath.findClassOrNull("java.lang.NullPointerException")) {
        val npeInstructions = entry.method.instList.filter { it.canProduceNpe() }

        if (npeInstructions.isEmpty()) {
            logger.warn { "Can't identify crash source instruction: ${crashException.name}" }
            return null
        }

        val instructions = npeInstructions
            .filter { it.lineNumber == entry.line }
            .takeIf { it.isNotEmpty() }
            ?: npeInstructions

        return instructions.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
    }

    val throwInstructions = entry.method.instList.filterIsInstance<JcThrowInst>()
    val sameLineThrow = throwInstructions.filter { it.lineNumber == entry.line }

    if (sameLineThrow.isNotEmpty()) {
        return sameLineThrow.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
    }

    val sameException = throwInstructions.filter { it.throwable.type == crashException.toType() }
    if (sameException.isNotEmpty()) {
        return sameException.map { ResolvedTraceEntry(entry.cls, entry.method, it) }
    }

    logger.warn { "Can't identify crash source instruction: ${crashException.name}" }
    return null
}

private fun JcInst.hasMethodCall(method: JcMethod): Boolean {
    val currentMethod = callExpr?.method?.method ?: return false
    if (currentMethod == method) return true
    if (!currentMethod.canBeOverridden()) return false
    if (currentMethod.name != method.name) return false
    if (currentMethod.parameters.size != method.parameters.size) return false
    if (!method.enclosingClass.isSubClassOf(currentMethod.enclosingClass)) return false
    if (currentMethod.description == method.description) return true
    val cp = currentMethod.enclosingClass.classpath
    for ((cur, tgt) in currentMethod.parameters.zip(method.parameters)) {
        if (cur.type == tgt.type) continue
        val curType = cp.findTypeOrNull(cur.type.typeName) ?: return false
        val tgtType = cp.findTypeOrNull(tgt.type.typeName) ?: return false
        if (!tgtType.isAssignable(curType)) return false
    }
    return true
}

private fun JcMethod.canBeOverridden(): Boolean =
    !isFinal && !isConstructor

private fun JcInst.canProduceNpe(): Boolean {
    val nullSources = listOfNotNull(
        fieldRef?.instance,
        arrayRef?.array,
        (callExpr as? JcInstanceCallExpr)?.instance
    )
    return nullSources.any { it is JcLocal && it !is JcThis }
}

private fun JcInst.canProduceIob(): Boolean = arrayRef != null


class CrashReproductionTest : BaseAnalysisTest() {

    private inline fun <UnitType> testOne(methods: List<JcMethod>, unitResolver: UnitResolver<UnitType>,
                                          ifdsUnitRunnerFactory: IfdsUnitRunnerFactory, srcDir: File) {
        val result = runAnalysis(graph.reversed, unitResolver, ifdsUnitRunnerFactory,  methods)
            .sortedBy { it.vulnerabilityDescription.message.substringAfterLast("at location ") }

        //println("Vulnerabilities found: ${result.size}")
        /*println("Generated report:")

        val json = Json {
            prettyPrint = true
            encodeDefaults = false
        }*/

        //json.encodeToStream(sarifReportFromVulnerabilities(result), System.out)
        result.forEach {
            println(it.vulnerabilityDescription.message)
        }
        val slicedLines = getSlicedLines(result, srcDir)
        highlightSrc(slicedLines)
    }

    fun getSlicedLines(slicingResult: List<VulnerabilityInstance>, srcDir: File): MutableMap<String, MutableSet<Int>> {
        var slicedLines: MutableMap<String, MutableSet<Int>> = mutableMapOf()
        slicingResult.forEach {
            val locationWithLine = it.vulnerabilityDescription.message.substringAfterLast(" at location ")
            val lineNumber = locationWithLine.substringAfterLast(":").toInt()
            val location = locationWithLine.substringBefore("#").replace(".", "/")
            //println("$location, $lineNumber")
            slicedLines.getOrPut(srcDir.path + "/" + location + ".java") { mutableSetOf() }.add(lineNumber)
        }
        return slicedLines
    }

    fun highlightSrc(slicedLines: MutableMap<String, MutableSet<Int>>) {
        for (filePath in slicedLines.keys) {
            var srcPath = filePath
            if (filePath.contains("$")) {
                srcPath = srcPath.substringBefore("$") + ".java"
            }
            val srcFile = File(srcPath)
            if (!srcFile.exists()) {
                error("No such source file $srcFile")
            }
            var fileContent = srcFile.readText().split("\n").toMutableList()
            slicedLines[filePath]?.forEach {
                fileContent[it - 1] = "+++" + fileContent[it - 1].removePrefix("   ")
            }
            srcFile.writeText(fileContent.foldRight("") { acc, s -> acc + "\n" + s })
            println("Changed src file $filePath")
        }
    }

    fun unZipSrc(zipFileName: String, targetDir: String) {
        ProcessBuilder()
            .directory(File(targetDir))
            .command("unzip", zipFileName)
            //.redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(File("/dev/null"))
            .start()
            .waitFor()

    }

    fun analyzeCrash(crash: CrashPackCrash, trace: CrashTrace) {
        val crashLog = crashPackPath / "crashes" / crash.application / crash.id / "${crash.id}.log"
        val crashCp = crashPackPath / "applications" / crash.application / crash.version / "bin"
        val srcZip = crashPackPath / "applications" / crash.application / crash.version / "src.zip"
        val srcTargetPath = crashPackPath / "applications" / crash.application / crash.version / "generatedSrc"
        val srcDir = File(srcTargetPath.toString())

        srcDir.deleteRecursively()
        srcDir.mkdir()
        unZipSrc(srcZip.toString(), srcTargetPath.toString())

        val cpFiles = crashCp.listDirectoryEntries("*.jar").map { it.toFile() }

        val jcdb = runBlocking {
            jacodb {
            //persistent((crashPackPath / "${crash.id}.db").absolutePathString())
                useProcessJavaRuntime()
                installFeatures(Usages, InMemoryHierarchy)
                loadByteCode(cpFiles)
            }
        }

        jcdb.use { db ->
            val jccp = runBlocking {
                db.awaitBackgroundJobs()
                db.classpath(cpFiles, listOf(UnknownClasses))
            }

            jccp.use { cp ->
                val src = srcDir.walk()
                    .firstOrNull { it.endsWith("src/main/java") || it.endsWith("src/java")}
                if (src != null) {
                    analyzeCrash(cp, trace, crash, src)
                } else {
                    val source = srcDir.walk()
                        .firstOrNull { it.endsWith("source") }
                        ?: throw Exception("No sources for: ${crash.application}")
                    analyzeCrash(cp, trace, crash, source)
                }
                /*runWithHardTimout(30.minutes) {
                    analyzeCrash(cp, trace, crash, srcDir.walk().first { it.endsWith("src/main/java")
                        || it.endsWith("src/java") })
                }*/
            }
        }
    }

    data class RawTraceEntry(val cls: JcClassOrInterface, val methodName: String, val line: Int)

    private fun analyzeCrash(cp: JcClasspath, trace: CrashTrace, crash: CrashPackCrash, srcDir: File) {
        logger.warn { "#".repeat(50) }
        logger.warn { "Try reproduce crash: ${crash.application} | ${crash.id}" }
        logger.warn { "\n$trace" }

        val traces = deserializeTraces(cp, trace.entries) ?: return

        val traceToAnalyze = traces.singleOrNull() ?: TODO("Many traces")

        val sinks = traceToAnalyze.map { it.inst }

        testOne(sinks.map {graph.methodOf(it)}.toSet().toList(), SingletonUnitResolver, newCrashSliceRunnerFactory(sinks), srcDir)

    }

    private fun resolveInstruction(entry: RawTraceEntry): Set<JcInst> {
        val possibleMethods = entry.cls.declaredMethods.filter { it.name == entry.methodName }
        var possibleLocations = possibleMethods.flatMap { it.instList.filter { it.lineNumber == entry.line } }

        if (possibleLocations.isEmpty()) {
            // todo: no locations
            error("no location")
        }

//        val preferredInstructions = possibleLocations.filter { it.callExpr != null || it is JcThrowInst }
//        if (preferredInstructions.isNotEmpty()) {
//            possibleLocations = preferredInstructions
//        }
        // take first instruction
        //return possibleLocations.take(1).single()
        return possibleLocations.toSet()

    }

    private fun runWithHardTimout(timeout: Duration, body: () -> Unit) {
        val completion = CompletableFuture<Unit>()
        val t = thread(start = false) {
            try {
                body()
                completion.complete(Unit)
            } catch (ex: Throwable) {
                completion.completeExceptionally(ex)
            }
        }
        try {
            t.start()
            completion.get(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (ex: Throwable) {
            while (t.isAlive) {
                @Suppress("DEPRECATION")
                t.stop()
            }
            throw ex
        }
    }

    @Test
    fun `test goodIds`() {
        val crashPackDescriptionPath = crashPackPath / "jcrashpack.json"
        val crashPack = Json.decodeFromStream<CrashPack>(crashPackDescriptionPath.inputStream())

//    parseCrashTraces(crashPackPath, crashPack)
        val traces = loadCrashTraces(crashPackPath) ?: error("No traces")

        val crashes = crashPack.crashes.values
            .sortedBy { it.id }
            .filter { traces[it.id]?.entries?.size == 1 }
            .filter { it.id == "CHART-13b" }
            //.filter { it.id == "ES-18657" }

        for (crash in crashes) {
            try {
                analyzeCrash(crash, traces[crash.id]!!)
            } catch (ex: Throwable) {
                logger.error(ex) { "Failed" }
            }
        }
    }

    override fun launchAnalysis(methods: List<JcMethod>): List<VulnerabilityInstance> {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }
        return buildList {
            runAnalysis(graph, SingletonUnitResolver, newSqlInjectionRunnerFactory(), methods).forEach {
                val result = runAnalysis(
                    graph.reversed, SingletonUnitResolver,
                    newSliceTaintRunnerFactory(setOf(it.traceGraph.sink.statement)), methods
                )
                    .filter { it.vulnerabilityDescription.ruleId == SliceTaintAnalyzer.modifiedId }

                println("Important instructions for sink ${it.traceGraph.sink.statement} found: ${result.size}")
                println("Generated report:")
                for (vulnerability : VulnerabilityInstance in result) {
                    println(vulnerability.vulnerabilityDescription.message)
                }
                addAll(result)
            }
        }
    }

    private val graph = runBlocking {
        cp.newApplicationGraphForAnalysis()
    }
}
