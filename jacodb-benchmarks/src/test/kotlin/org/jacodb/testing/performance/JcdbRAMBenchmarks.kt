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
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.impl.JcRamErsSettings
import org.jacodb.impl.features.Usages
import org.jacodb.impl.jacodb
import org.jacodb.testing.allClasspath
import org.jacodb.testing.guavaLib
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(2, jvmArgs = ["-Xmx12g", "-Xms12g"])
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbRAMBenchmarks {

    private var db: JcDatabase? = null

    @Benchmark
    fun jvmRuntime() {
        db = runBlocking {
            jacodb {
                persistenceImpl(JcRamErsSettings)
                useProcessJavaRuntime()
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithUsages() {
        db = runBlocking {
            jacodb {
                persistenceImpl(JcRamErsSettings)
                useProcessJavaRuntime()
                installFeatures(Usages)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspath() {
        db = runBlocking {
            jacodb {
                persistenceImpl(JcRamErsSettings)
                useProcessJavaRuntime()
                loadByteCode(allClasspath)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspathWithUsages() {
        db = runBlocking {
            jacodb {
                persistenceImpl(JcRamErsSettings)
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
                persistenceImpl(JcRamErsSettings)
                useProcessJavaRuntime()
                loadByteCode(listOf(guavaLib))
            }
        }
    }

    @Benchmark
    fun jvmRuntimeWithGuavaWithUsages() {
        db = runBlocking {
            jacodb {
                persistenceImpl(JcRamErsSettings)
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
                persistenceImpl(JcRamErsSettings)
                useProcessJavaRuntime()
                loadByteCode(allIdeaJars)
            }
        }
    }

    @Benchmark
    fun jvmRuntimeIdeaCommunityWithUsages() {
        db = runBlocking {
            jacodb {
                persistenceImpl(JcRamErsSettings)
                useProcessJavaRuntime()
                loadByteCode(allIdeaJars)
                installFeatures(Usages)
            }
        }
    }

    @TearDown(Level.Invocation)
    fun tearDown() {
        db?.let {
            db = null
            it.close()
        }
    }
}
