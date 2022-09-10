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
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.fs.load
import org.utbot.jcdb.impl.index.Usages
import org.utbot.jcdb.jcdb
import java.util.concurrent.TimeUnit


@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class DBBenchmarks : LibrariesMixin {

    private var db: JCDB? = null

    @Benchmark
    fun readBytecode() {
        val lib = guavaLib
        runBlocking {
            lib.asByteCodeLocation().loader()!!.load()
        }
    }

    @Benchmark
    fun readingJVMbytecode() {
        db = runBlocking {
            jcdb {
                useProcessJavaRuntime()

                installFeatures(Usages)
            }
        }
    }

    @Benchmark
    fun readingJVMbytecodeWithProjectClasspath() {
        db = runBlocking {
            jcdb {
                useProcessJavaRuntime()
                predefinedDirOrJars = allJars
                installFeatures(Usages)
            }
        }
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        db?.let {
            runBlocking {
                it.awaitBackgroundJobs()
                it.close()
            }
        }
    }
}