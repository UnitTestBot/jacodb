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

import juliet.support.AbstractTestCase
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.engine.VulnerabilityInstance
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.methods
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithGlobalDB
import org.jacodb.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.streams.asStream

abstract class BaseAnalysisTest : BaseTest() {
    companion object : WithGlobalDB(UnknownClasses) {
        @JvmStatic
        fun provideClassesForJuliet(cweNum: Int, cweSpecificBans: List<String> = emptyList()): Stream<Arguments> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            val classes = hierarchyExt.findSubClasses(baseClass, false)
            classes.toArguments("CWE${cweNum}_", cweSpecificBans)
        }

        private fun Sequence<JcClassOrInterface>.toArguments(cwe: String, cweSpecificBans: List<String>): Stream<Arguments> = this
            .map { it.name }
            .filter { it.contains(cwe) }
            .filterNot { className -> (commonJulietBans + cweSpecificBans).any { className.contains(it) } }
//            .filter { it.contains("_68") }
            .sorted()
            .map { Arguments.of(it) }
            .asStream()

        private val commonJulietBans = listOf(
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
            "_10", "_14",
        )
    }

    protected abstract fun launchAnalysis(methods: List<JcMethod>): List<VulnerabilityInstance<JcMethod, JcInstLocation, JcInst>>

    protected inline fun <reified T> testOneAnalysisOnOneMethod(
        vulnerabilityType: String,
        methodName: String,
        expectedLocations: Collection<String>,
    ) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method, vulnerabilityType)

        // TODO: think about better assertions here
        assertEquals(expectedLocations.size, sinks.size)
        expectedLocations.forEach { expected ->
            assertTrue(sinks.any { it.contains(expected) })
        }
    }

    protected fun testSingleJulietClass(vulnerabilityType: String, className: String) {
        val clazz = cp.findClass(className)
        val goodMethod = clazz.methods.single { it.name == "good" }
        val badMethod = clazz.methods.single { it.name == "bad" }

        val goodIssues = findSinks(goodMethod, vulnerabilityType)
        val badIssues = findSinks(badMethod, vulnerabilityType)

        assertTrue(goodIssues.isEmpty())
        assertTrue(badIssues.isNotEmpty())
    }

    protected fun findSinks(method: JcMethod, vulnerabilityType: String): Set<String> {
        val sinks = launchAnalysis(listOf(method))
            .filter { it.vulnerabilityDescription.ruleId == vulnerabilityType }
            .map { it.traceGraph.sink.toString() }

        return sinks.toSet()
    }
}