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

import juliet.testcasesupport.AbstractTestCase
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.AnalysisEngine
import org.jacodb.analysis.toDumpable
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream
import kotlin.streams.asStream

abstract class BaseAnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy) {
        @JvmStatic
        fun provideClassesForJuliet(cweNum: Int, cweSpecificBans: List<String> = emptyList()): Stream<Arguments> = runBlocking {
            val cp = db.classpath(allClasspath)
            val hierarchyExt = cp.hierarchyExt()
            val baseClass = cp.findClass<AbstractTestCase>()
            val classes = hierarchyExt.findSubClasses(baseClass, false)
            classes.toArguments("CWE$cweNum", cweSpecificBans)
        }

        private fun Sequence<JcClassOrInterface>.toArguments(cwe: String, cweSpecificBans: List<String>): Stream<Arguments> = this
            .map { it.name }
            .filter { it.contains(cwe) }
            .filterNot { className -> (commonJulietBans + cweSpecificBans).any { className.contains(it) } }
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
            "_10", "_14"
        )
    }

    protected inline fun <reified T> testOneAnalysisOnOneMethod(
        engine: AnalysisEngine,
        vulnerabilityType: String,
        methodName: String,
        expectedLocations: Collection<String>,
    ) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method, engine, vulnerabilityType)

        // TODO: think about better assertions here
        assertEquals(expectedLocations.size, sinks.size)
        expectedLocations.forEach { expected ->
            assertTrue(sinks.any { it.contains(expected) })
        }
    }

    protected fun testSingleJulietClass(engine: AnalysisEngine, vulnerabilityType: String, className: String) {
        val clazz = cp.findClass(className)
        val goodMethod = clazz.methods.single { it.name == "good" }
        val badMethod = clazz.methods.single { it.name == "bad" }

        val goodNPE = findSinks(goodMethod, engine, vulnerabilityType)
        val badNPE = findSinks(badMethod, engine, vulnerabilityType)

        assertTrue(goodNPE.isEmpty())
        assertTrue(badNPE.isNotEmpty())
    }

    protected fun findSinks(method: JcMethod, engine: AnalysisEngine, vulnerabilityType: String): Set<String> {
        engine.addStart(method)
        val sinks = engine.analyze().toDumpable().foundVulnerabilities.filter { it.vulnerabilityType == vulnerabilityType }
        return sinks.map { it.sink }.toSet()
    }
}