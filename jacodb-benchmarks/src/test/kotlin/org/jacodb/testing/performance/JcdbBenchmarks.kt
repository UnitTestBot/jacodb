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
import org.jacodb.api.JcDatabase
import org.jacodb.impl.features.Usages
import org.jacodb.impl.jacodb
import org.jacodb.testing.allClasspath
import org.jacodb.testing.guavaLib
import org.openjdk.jmh.annotations.*
import java.io.File
import java.util.concurrent.TimeUnit


@State(Scope.Benchmark)
@Fork(1, jvmArgs = ["-Xmx12288m"])
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbBenchmarks {

    private var db: JcDatabase? = null

    @Benchmark
    fun jvmRuntime() {
        db = runBlocking {
            jacodb {
                useProcessJavaRuntime()
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithUsages() {
        db = runBlocking {
            jacodb {
                useProcessJavaRuntime()
                installFeatures(Usages)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspath() {
        db = runBlocking {
            jacodb {
                useProcessJavaRuntime()
                loadByteCode(allClasspath)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspathWithUsages() {
        db = runBlocking {
            jacodb {
                useProcessJavaRuntime()
                loadByteCode(allClasspath)
                installFeatures(Usages)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithGuava() {
        db = runBlocking {
            jacodb {
                useProcessJavaRuntime()
                loadByteCode(listOf(guavaLib))
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithGuavaWithUsages() {
        db = runBlocking {
            jacodb {
                useProcessJavaRuntime()
                loadByteCode(listOf(guavaLib))
                installFeatures(Usages)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithIdeaCommunity() {
        db = runBlocking {
            jacodb {
                useProcessJavaRuntime()
                persistent(File.createTempFile("jcdb-", "-db").absolutePath)
                loadByteCode(allIdeaJars)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeIdeaCommunityWithUsages() {
        db = runBlocking {
            jacodb {
                useProcessJavaRuntime()
                loadByteCode(allIdeaJars)
                persistent(File.createTempFile("jcdb-", "-db").absolutePath)
                installFeatures(Usages)
            }
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        db?.close()
    }
}
