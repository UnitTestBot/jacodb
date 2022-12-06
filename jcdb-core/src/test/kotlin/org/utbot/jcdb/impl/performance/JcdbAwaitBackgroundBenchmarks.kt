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

package org.utbot.jcdb.impl.performance

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.impl.allClasspath
import org.utbot.jcdb.jcdb
import java.io.File
import java.util.concurrent.TimeUnit

abstract class JcdbAbstractAwaitBackgroundBenchmarks {

    private lateinit var db: JCDB

    abstract fun JCDBSettings.configure()

    @Setup(Level.Iteration)
    fun setup() {
        db = runBlocking {
            jcdb {
                useProcessJavaRuntime()
                configure()
            }
        }
    }

    @Benchmark
    fun awaitBackground() {
        runBlocking {
            db.awaitBackgroundJobs()
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        db.close()
    }
}


@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbJvmBackgroundBenchmarks : JcdbAbstractAwaitBackgroundBenchmarks() {

    override fun JCDBSettings.configure() {
    }

}

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbAllClasspathBackgroundBenchmarks : JcdbAbstractAwaitBackgroundBenchmarks() {

    override fun JCDBSettings.configure() {
        loadByteCode(allClasspath)
    }

}

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbIdeaBackgroundBenchmarks : JcdbAbstractAwaitBackgroundBenchmarks() {

    override fun JCDBSettings.configure() {
        loadByteCode(allIdeaJars)
        persistent(File.createTempFile("jcdb-", "-db").absolutePath)
    }

}
