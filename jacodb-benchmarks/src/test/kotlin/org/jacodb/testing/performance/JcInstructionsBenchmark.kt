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

package org.jacodb.testing.performance

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcDatabase
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.ext.findClass
import org.jacodb.impl.JcCacheSettings
import org.jacodb.impl.JcClasspathImpl
import org.jacodb.impl.cfg.nonCachedFlowGraph
import org.jacodb.impl.cfg.nonCachedInstList
import org.jacodb.impl.cfg.nonCachedRawInstList
import org.jacodb.impl.features.classpaths.ClasspathCache
import org.jacodb.impl.jacodb
import org.jacodb.testing.allClasspath
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@State(Scope.Benchmark)
@Warmup(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 1000, time = 1, timeUnit = TimeUnit.NANOSECONDS)
class JcInstructionsBenchmark {

    object NoInstructionsCache : ClasspathCache(JcCacheSettings()) {

        override fun instList(method: JcMethod): JcInstList<JcInst> {
            return nonCachedInstList(method)
        }

        override fun rawInstList(method: JcMethod): JcInstList<JcRawInst> {
            return nonCachedRawInstList(method)
        }

        override fun flowGraph(method: JcMethod): JcGraph {
            return nonCachedFlowGraph(method)
        }

    }

    private lateinit var db: JcDatabase
    private lateinit var cp: JcClasspath

    @Setup(Level.Trial)
    fun setup() {
        runBlocking {
            db = jacodb {
                useProcessJavaRuntime()
                loadByteCode(allClasspath)
            }
            cp = db.classpath(allClasspath, listOf(NoInstructionsCache))
        }
    }

    @Benchmark
    fun rawInstList() {
        runFor<JcClasspathImpl> { it.rawInstList }
    }

    @Benchmark
    fun instList() {
        runFor<JcClasspathImpl> { it.instList }
    }

    @Benchmark
    fun flowGraph() {
        runFor<JcClasspathImpl> { it.flowGraph() }
    }

    private inline fun <reified T> runFor(call: (JcMethod) -> Unit) {
        cp.findClass<T>().declaredMethods.forEach(call)
    }

}

@OptIn(ExperimentalTime::class)
fun main() {
    val cp = runBlocking {
        val db = jacodb {
            useProcessJavaRuntime()
            loadByteCode(allClasspath)
        }
        db.classpath(allClasspath, listOf(JcInstructionsBenchmark.NoInstructionsCache))
    }
    repeat(3000) {
        println("$it consumes " + measureTime {
            cp.findClass<JcClasspathImpl>().declaredMethods.forEach {
                it.flowGraph()
            }
        })
    }
}
