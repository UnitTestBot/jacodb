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
import org.jacodb.impl.jacodb
import org.jacodb.testing.allClasspath
import org.openjdk.jmh.annotations.*
import java.nio.file.Files
import java.util.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class RestoreJcdbBenchmark {

    companion object {
        private val jdbcLocation = Files.createTempDirectory("jdbc-${UUID.randomUUID()}").toFile().absolutePath
    }

    var db: JcDatabase? = null

    @Setup
    fun setup() {
        val tempDb = newDB()
        tempDb.close()
    }

    @Benchmark
    fun restore() {
        db = newDB()
    }

    @TearDown(Level.Iteration)
    fun clean() {
        db?.close()
        db = null
    }

    private fun newDB(): JcDatabase {
        return runBlocking {
            jacodb {
                persistent(jdbcLocation)
                loadByteCode(allClasspath)
                useProcessJavaRuntime()
            }.also {
                it.awaitBackgroundJobs()
            }
        }
    }

}