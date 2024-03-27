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

import org.jacodb.analysis.ifds.SingletonUnitResolver
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.analysis.taint.TaintVulnerability
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.testing.analysis.TaintExamples
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private val logger = mu.KotlinLogging.logger {}

class IfdsTaintTest : BaseAnalysisTest() {

    @Test
    fun `analyze simple taint on bad method`() {
        testOneMethod<TaintExamples>("bad")
    }

    private fun findSinks(method: JcMethod): List<TaintVulnerability<JcMethod, JcInst>> {
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        return manager.analyze(listOf(method), timeout = 3000.seconds)
    }

    private inline fun <reified T> testOneMethod(methodName: String) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val sinks = findSinks(method)
        logger.info { "Sinks: ${sinks.size}" }
        for ((i, sink) in sinks.withIndex()) {
            logger.info { "[${i + 1}/${sinks.size}]: ${sink.sink}" }
        }
        assertTrue(sinks.isNotEmpty())
    }
}