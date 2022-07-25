package org.utbot.jcdb.impl.performance

import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.impl.index.ReversedUsages
import org.utbot.jcdb.jcdb
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class DBLifeCycleBenchmarks : LibrariesMixin {

    private lateinit var db: JCDB

    @Setup(Level.Iteration)
    fun setup() {
        db = runBlocking {
            jcdb {
                installFeatures(ReversedUsages)
                useProcessJavaRuntime()
            }
        }
    }

    @Benchmark
    fun loadAdditionalJars() {
        val jars = allJars
        runBlocking {
            db.load(jars)
        }
    }

    @Benchmark
    fun awaitIndexing() {
        runBlocking {
            db.awaitBackgroundJobs()
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        runBlocking {
            db.awaitBackgroundJobs()
            db.close()
        }
    }
}
