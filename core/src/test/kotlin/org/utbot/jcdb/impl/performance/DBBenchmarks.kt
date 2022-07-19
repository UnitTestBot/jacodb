package org.utbot.jcdb.impl.performance


import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.*
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.fs.load
import org.utbot.jcdb.impl.index.ReversedUsages
import org.utbot.jcdb.impl.tree.ClassTree
import java.util.concurrent.TimeUnit


@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class DBBenchmarks : LibrariesMixin {

    private var db: CompilationDatabase? = null

    @Benchmark
    fun readBytecode() {
        val lib = guavaLib
        runBlocking {
            lib.asByteCodeLocation().loader()!!.load(ClassTree())
        }
    }

    @Benchmark
    fun readingJVMbytecode() {
        db = runBlocking {
            compilationDatabase {
                useProcessJavaRuntime()

                installFeatures(ReversedUsages)
            }
        }
    }

    @Benchmark
    fun readingJVMbytecodeWithProjectClasspath() {
        db = runBlocking {
            compilationDatabase {
                useProcessJavaRuntime()
                predefinedDirOrJars = allJars
                installFeatures(ReversedUsages)
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