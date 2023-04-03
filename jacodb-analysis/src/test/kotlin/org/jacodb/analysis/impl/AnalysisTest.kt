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

import NPEExamples
import juliet.testcasesupport.AbstractTestCase
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.impl.AnalysisTest.AllOverridesDevirtualizer.Companion.bannedPackagePrefixes
import org.jacodb.analysis.impl.SimplifiedJcApplicationGraph.Companion.bannedPackagePrefixes
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcNoopInst
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.impl.analysis.JcAnalysisPlatformImpl
import org.jacodb.impl.analysis.features.JcCacheGraphFeature
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.SyncUsagesExtension
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.features.usagesExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream
import kotlin.streams.asStream


/**
 * Simplification of JcApplicationGraph that ignores method calls matching [bannedPackagePrefixes]
 */
class SimplifiedJcApplicationGraph(
    override val classpath: JcClasspath,
    usages: SyncUsagesExtension,
    cacheSize: Long = 10_000,
) : JcAnalysisPlatformImpl(classpath, listOf(JcCacheGraphFeature(cacheSize))), ApplicationGraph<JcMethod, JcInst> {
    private val impl = JcApplicationGraphImpl(classpath, usages, cacheSize)

    private val visitedCallers: MutableMap<JcMethod, MutableSet<JcInst>> = mutableMapOf()

    private fun getStartInst(method: JcMethod): JcNoopInst {
        return JcNoopInst(JcInstLocation(method, -1, -1))
    }

    override fun predecessors(node: JcInst): Sequence<JcInst> {
        val method = methodOf(node)
        return if (node == getStartInst(method)) {
            emptySequence()
        } else {
            if (node in impl.entryPoint(method)) {
                sequenceOf(getStartInst(method))
            } else {
                impl.predecessors(node)
            }
        }
    }
    override fun successors(node: JcInst): Sequence<JcInst> {
        val method = methodOf(node)
        return if (node == getStartInst(method)) {
            impl.entryPoint(method)
        } else {
            impl.successors(node)
        }
    }
    override fun callees(node: JcInst): Sequence<JcMethod> = impl.callees(node).filterNot { callee ->
        bannedPackagePrefixes.any { callee.enclosingClass.name.startsWith(it) }
    }.map {
        val curSet = visitedCallers.getOrPut(it) { mutableSetOf() }
        curSet.add(node)
        it
    }
    override fun callers(method: JcMethod): Sequence<JcInst> = visitedCallers.getOrDefault(method, mutableSetOf()).asSequence()//impl.callers(method)
    override fun entryPoint(method: JcMethod): Sequence<JcInst> = sequenceOf(getStartInst(method))//impl.entryPoint(method)
    override fun exitPoints(method: JcMethod): Sequence<JcInst> = impl.exitPoints(method)
    override fun methodOf(node: JcInst): JcMethod = impl.methodOf(node)

    companion object {
        private val bannedPackagePrefixes = listOf(
            "kotlin.",
            "java.",
            "kotlin.jvm.internal.",
            "jdk.internal.",
            "sun.",
            "java.security.",
            "java.util.regex."
        )
    }

}

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet476(): Stream<Arguments> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            val classes = hierarchyExt.findSubClasses(baseClass, false)
            classes.toArguments("CWE476")
        }

        @JvmStatic
        fun provideClassesForJuliet690(): Stream<Arguments> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            val classes = hierarchyExt.findSubClasses(baseClass, false)
            classes.toArguments("CWE690")
        }

        private fun Sequence<JcClassOrInterface>.toArguments(cwe: String): Stream<Arguments> = map { it.name }
            .filter { it.contains(cwe) }
            .filterNot { className -> bannedTests.any { className.contains(it) } }
            .sorted()
            .map { Arguments.of(it) }
            .asStream()

        private val bannedTests = listOf(
            // not NPE problems
            "null_check_after_deref",

            // TODO: containers not supported
            "_72", "_73", "_74",

            // TODO/Won't fix(?): dead parts of switches shouldn't be analyzed
            "_15",

            // TODO/Won't fix(?): passing through channels not supported
            "_75",

            // TODO/Won't fix(?): constant private/static methods not analyzed
            "_11", "_08",

            // TODO/Won't fix(?): unmodified non-final private variables not analyzed
            "_05", "_07",

            // TODO/Won't fix(?): unmodified non-final static variables not analyzed
            "_10", "_14"
        )
    }

    @Test
    fun `fields resolving should work through interfaces`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
        val callers = graph.callers(cp.findClass<StringTokenizer>().constructors[2])
        println(callers.toList().size)
    }
    
    @Test
    fun `analyse something`() {
        val graph = runBlocking {
            SimplifiedJcApplicationGraph(cp, cp.usagesExt())
        }
        val devirtualizer = AllOverridesDevirtualizer(graph, cp)
//      graph.callees(graph.successors(graph.successors(graph.successors(graph.entryPoint(graph.classpath.findClassOrNull("org.jacodb.analysis.impl.IFDSMainKt").methods.find {it.name.equals("main")}!!).toList().single()).single()).single()).last()!!)
//      cp.execute(object: JcClassProcessingTask{
//            override fun process(clazz: JcClassOrInterface) {
//
//            }
//        })
        val testingMethod = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "npeOnLength" }
        val results = runNPEWithPointsTo(graph, graph, testingMethod, devirtualizer)
        print(results)
    }

    @Test
    fun `analyze simple NPE`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "npeOnLength" }
        val actual = findNPEInstructions(method)

        // TODO: think about better assertions here
        assertEquals(
            setOf("%3 = %2.length()"),
            actual.map { it.inst.toString() }.toSet()
        )
    }

    @Test
    fun `analyze no NPE`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "noNPE" }
        val actual = findNPEInstructions(method)

        assertEquals(emptyList<NPELocation>(), actual)
    }

    @Test
    fun `analyze NPE after fun with two exits`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "npeAfterTwoExits" }
        val actual = findNPEInstructions(method)

        assertEquals(
            setOf("%4 = %2.length()", "%5 = %3.length()"),
            actual.map { it.inst.toString() }.toSet()
        )
    }

    @Test
    fun `no NPE after checked access`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "checkedAccess" }
        val actual = findNPEInstructions(method)

        assertEquals(emptyList<NPELocation>(), actual)
    }

    @Test
    fun `consecutive NPEs handled properly`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "consecutiveNPEs" }
        val actual = findNPEInstructions(method)

        assertEquals(
            setOf("%2 = arg$0.length()", "%4 = arg$0.length()"),
            actual.map { it.inst.toString() }.toSet()
        )
    }

    @Test
    fun `npe on virtual call when possible`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "possibleNPEOnVirtualCall" }
        val actual = findNPEInstructions(method)

        // TODO: one false-positive here due to not-parsed @NotNull annotation
        assertEquals(2, actual.size)
    }

    @Test
    fun `no npe on virtual call when impossible`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "noNPEOnVirtualCall" }
        val actual = findNPEInstructions(method)

        // TODO: false-positive here due to not-parsed @NotNull annotation
        assertEquals(1, actual.size)
    }

    @Test
    fun `basic test for NPE on fields`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "simpleNPEOnField" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%8 = %6.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `simple points-to analysis`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "simplePoints2" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%5 = %4.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `complex aliasing`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "complexAliasing" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%6 = %5.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `context injection in points-to`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "contextInjection" }
        val actual = findNPEInstructions(method)

        assertEquals(
            setOf("%6 = %5.length()", "%3 = %2.length()"),
            actual.map { it.inst.toString() }.toSet()
        )
    }

    @Test
    fun `activation points maintain flow sensitivity`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "flowSensitive" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%8 = %7.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `overridden null assignment in callee don't affect next caller's instructions`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "overriddenNullInCallee" }
        // This call may take infinite time in case of bugs with limiting field accesses
        val actual = findNPEInstructions(method)

        assertEquals(
            emptyList<NPELocation>(),
            actual
        )
    }

    @Test
    fun `recursive classes handled correctly`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "recursiveClass" }
        // This call may take infinite time in case of bugs with limiting field accesses
        val actual = findNPEInstructions(method)

        assertEquals(
            setOf("%10 = %9.toString()", "%15 = %14.toString()"),
            actual.map { it.inst.toString() }.toSet()
        )
    }

    @Test
    fun `NPE on uninitialized array element dereferencing`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "simpleArrayNPE" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%5 = %4.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `no NPE on array element dereferencing after initialization`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "noNPEAfterArrayInit" }
        val actual = findNPEInstructions(method)

        assertEquals(
            emptyList<String>(),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `array aliasing`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "arrayAliasing" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%5 = %4.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `mixed array and class aliasing`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "mixedArrayClassAliasing" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%13 = %12.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `dereferencing field of null object`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "npeOnFieldDeref" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%1 = %0.field"),
            actual.map { it.inst.toString() }
        )
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet476")
    fun `test on Juliet's CWE 476`(className: String) {
        testJuliet(className)
    }

    @ParameterizedTest
    @MethodSource("provideClassesForJuliet690")
    fun `test on Juliet's CWE 690`(className: String) {
        testJuliet(className)
    }

    private fun testJuliet(className: String) {
        val clazz = cp.findClass(className)
        val goodMethod = clazz.methods.single { it.name == "good" }
        val badMethod = clazz.methods.single { it.name == "bad" }

        goodMethod.flowGraph()
        badMethod.flowGraph()
        val goodNPE = findNPEInstructions(goodMethod)
        val badNPE = findNPEInstructions(badMethod)

        assertEquals(listOf(emptyList<NPELocation>(), true), listOf(goodNPE, badNPE.isNotEmpty()))
    }


    data class NPELocation(val inst: JcInst, val path: AccessPath, val possibleStackTrace: List<JcInst>)

    /**
     * The method finds all places where NPE may occur
     */
    fun findNPEInstructions(method: JcMethod): List<NPELocation> {
        val graph = runBlocking {
            SimplifiedJcApplicationGraph(cp, cp.usagesExt())
        }
        val devirtualizer = AllOverridesDevirtualizer(graph, cp)
        val ifdsResults = runNPEWithPointsTo(graph, graph, method, devirtualizer)
        val possibleNPEInstructions = mutableListOf<NPELocation>()
        ifdsResults.resultFacts.forEach { (inst, facts) ->
            facts.forEach { fact ->
                if (fact.activation == null && fact.variable.isDereferencedAt(inst)) {
                    val possibleStackTrace = ifdsResults.resolvePossibleStackTrace(
                        Vertex(inst, fact), method
                    )
                    possibleNPEInstructions.add(NPELocation(inst, fact.variable!!, possibleStackTrace))
                }
            }
        }
        return possibleNPEInstructions
    }

    /**
     * Simple devirtualizer that substitutes method with all ov its overrides, but no more then [limit].
     * Also, it doesn't devirtualize methods matching [bannedPackagePrefixes]
     */
    class AllOverridesDevirtualizer(
        private val initialGraph: ApplicationGraph<JcMethod, JcInst>,
        private val classpath: JcClasspath,
        private val limit: Int = 3
    ) : Devirtualizer<JcMethod, JcInst> {
        private val hierarchyExtension = runBlocking {
            classpath.hierarchyExt()
        }

        override fun findPossibleCallees(sink: JcInst): Collection<JcMethod> {
            val methods = initialGraph.callees(sink).toList()
            if (sink.callExpr !is JcVirtualCallExpr)
                return methods
            return methods
                .flatMap { method ->
                    if (bannedPackagePrefixes.any { method.enclosingClass.name.startsWith(it) })
                        listOf(method)
                    else {
                        hierarchyExtension
                            .findOverrides(method) // TODO: maybe filter inaccessible methods here?
                            .take(limit - 1)
                            .toList() + listOf(method)
                    }
                }
        }

        companion object {
            private val bannedPackagePrefixes = listOf(
                "sun.",
                "jdk.internal.",
                "java.",
                "kotlin."
            )
        }
    }
}