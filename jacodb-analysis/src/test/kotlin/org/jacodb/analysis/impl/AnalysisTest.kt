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
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.impl.AnalysisTest.AllOverridesDevirtualizer.Companion.bannedPackagePrefixes
import org.jacodb.analysis.impl.SimplifiedJcApplicationGraph.Companion.bannedPackagePrefixes
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcNoopInst
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.cfg.fieldRef
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findClass
import org.jacodb.impl.analysis.JcAnalysisPlatformImpl
import org.jacodb.impl.analysis.features.JcCacheGraphFeature
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.SyncUsagesExtension
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.features.usagesExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*


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
//            "kotlin.",
//            "java.",
            "kotlin.jvm.internal.",
            "jdk.internal.",
            "sun.",
            "java.security.",
            "java.util.regex."
        )
    }

}

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

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

    data class NPELocation(val inst: JcInst, val value: JcValue, val possibleStackTrace: List<JcInst>)

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
        ifdsResults.resultFacts.forEach { (instruction, facts) ->
            val instance = (instruction.callExpr as? JcInstanceCallExpr)?.instance as? JcLocal ?: return@forEach
            if (TaintNode.fromPath(AccessPath.fromLocal(instance)) in facts) {
                val possibleStackTrace = ifdsResults.resolvePossibleStackTrace(
                    Vertex(instruction, TaintNode.fromPath(AccessPath.fromLocal(instance))), method
                )
                possibleNPEInstructions.add(NPELocation(instruction, instance, possibleStackTrace))
            }

            // TODO: check for JcLengthExpr and JcArrayAccess

            val fieldRef = instruction.fieldRef
            if (fieldRef is JcFieldRef) {
                fieldRef.instance?.let {
                    if (it !is JcLocal)
                        return@let
                    if (TaintNode.fromPath(AccessPath.fromLocal(it)) in facts) {
                        val possibleStackTrace = ifdsResults.resolvePossibleStackTrace(
                            Vertex(instruction, TaintNode.fromPath(AccessPath.fromLocal(it))), method
                        )
                        possibleNPEInstructions.add(NPELocation(instruction, it, possibleStackTrace))
                    }
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