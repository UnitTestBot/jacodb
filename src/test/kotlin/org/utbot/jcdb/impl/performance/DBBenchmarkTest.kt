package org.utbot.jcdb.impl.performance

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.fs.load
import org.utbot.jcdb.impl.index.ReversedUsagesIndex
import org.utbot.jcdb.impl.tree.ClassTree


class DBBenchmarkTest : LibrariesMixin {

    @Test
    fun `read byte-code benchmark`() {
        val lib = guavaLib
        benchmark(name = "read bytecode") {
            runBlocking {
                lib.asByteCodeLocation().loader()!!.load(ClassTree())
            }
        }
    }

    @Test
    fun `read all classpath byte-code benchmark`() {
        val jars = allJars
        benchmark(5, "reading libraries bytecode") {
            val db = measure("load db") {
                runBlocking {
                    compilationDatabase {
                        installIndexes(ReversedUsagesIndex)
                        useProcessJavaRuntime()
                    }
                }
            }
            measure("load jars") {
                runBlocking {
                    db.load(jars)
                }
            }
            db.close()
        }
    }

    @Test
    fun `read jre libraries benchmark`() {
        benchmark(5, "reading jvm bytecode") {
            runBlocking {
                compilationDatabase {
                    useProcessJavaRuntime()

                    installIndexes(ReversedUsagesIndex)
                }
            }
        }
    }

    @Test
    fun `read jre libraries benchmark and await indexing`() {
        benchmark(5, "reading jvm bytecode") {
            val db = runBlocking {
                compilationDatabase {
                    useProcessJavaRuntime()
                    installIndexes(ReversedUsagesIndex)
                }
            }
            measure("awaiting") {
                runBlocking {
                    db.awaitBackgroundJobs()
                    db.close()
                }
            }
        }
    }

    private fun benchmark(repeats: Int = 10, name: String, action: () -> Unit) {
        // warmup
        repeat(repeats / 2) {
            println("warmup $it")
            action()
        }

        // let's count
        repeat(repeats) {
            measure("$name $it", action)
            Thread.sleep(1_000)
        }
    }


    private fun <T> measure(name: String, action: () -> T): T {
        val start = System.currentTimeMillis()
        val result = action()
        val end = System.currentTimeMillis()
        println("$name: took: ${end - start}ms")
        return result
    }
}
