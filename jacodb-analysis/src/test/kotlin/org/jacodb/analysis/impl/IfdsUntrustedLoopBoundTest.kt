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
import mu.KotlinLogging
import org.jacodb.analysis.ifds.SingletonUnitResolver
import org.jacodb.analysis.taint.Globals
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.testing.WithDB
import org.jacodb.testing.allClasspath
import org.jacodb.testing.analysis.UntrustedLoopBound
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

class Ifds2UpperBoundTest : BaseAnalysisTest() {

    companion object : WithDB(Usages, InMemoryHierarchy)

    override val cp: JcClasspath = runBlocking {
        val defaultConfigResource = this.javaClass.getResourceAsStream("/config_untrusted_loop_bound.json")
        if (defaultConfigResource != null) {
            val configJson = defaultConfigResource.bufferedReader().readText()
            val configurationFeature = TaintConfigurationFeature.fromJson(configJson)
            db.classpath(allClasspath, listOf(configurationFeature) + classpathFeatures)
        } else {
            super.cp
        }
    }

    @Test
    fun `analyze untrusted upper bound`() {
        Globals.UNTRUSTED_LOOP_BOUND_SINK = true
        testOneMethod<UntrustedLoopBound>("handle")
    }

    private inline fun <reified T> testOneMethod(methodName: String) {
        val method = cp.findClass<T>().declaredMethods.single { it.name == methodName }
        val unitResolver = SingletonUnitResolver
        val manager = TaintManager(graph, unitResolver)
        val sinks = manager.analyze(listOf(method))
        logger.info { "Sinks: ${sinks.size}" }
        for (sink in sinks) {
            logger.info { sink }
        }
        assertTrue(sinks.isNotEmpty())
    }
}
