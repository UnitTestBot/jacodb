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
import org.jacodb.impl.JcSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.jacodb
import org.jacodb.impl.storage.jooq.tables.references.*
import org.jacodb.testing.allClasspath
import org.openjdk.jmh.annotations.*
import java.io.File
import java.util.concurrent.TimeUnit

abstract class JcdbAbstractAwaitBackgroundBenchmarks {

    private lateinit var db: JcDatabase

    abstract fun JcSettings.configure()

    @Setup(Level.Iteration)
    fun setup() {
        db = runBlocking {
            jacodb {
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
@Fork(1, jvmArgs = ["-Xmx12288m"])
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbJvmBackgroundBenchmarks : JcdbAbstractAwaitBackgroundBenchmarks() {

    override fun JcSettings.configure() {
    }

}

@State(Scope.Benchmark)
@Fork(1, jvmArgs = ["-Xmx12288m"])
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbAllClasspathBackgroundBenchmarks : JcdbAbstractAwaitBackgroundBenchmarks() {

    override fun JcSettings.configure() {
        loadByteCode(allClasspath)
    }

}

@State(Scope.Benchmark)
@Fork(1, jvmArgs = ["-Xmx12288m"])
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbIdeaBackgroundBenchmarks : JcdbAbstractAwaitBackgroundBenchmarks() {

    override fun JcSettings.configure() {
        loadByteCode(allIdeaJars)
        installFeatures(Usages, InMemoryHierarchy)
        persistent(File.createTempFile("jcdb-", "-db").absolutePath)
    }

}
