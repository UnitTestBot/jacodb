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
import org.jacodb.api.*
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.fs.className
import org.jacodb.impl.jacodb
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.jacodb.testing.allClasspath
import org.jooq.DSLContext
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.openjdk.jmh.annotations.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private typealias ClassesCache = ConcurrentHashMap<Long, MutableSet<ClassNode>>

private val objectJvmName = Type.getInternalName(Any::class.java)

@State(Scope.Benchmark)
@Fork(1, jvmArgs = ["-Xmx12288m"])
@Warmup(iterations = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class JcdbBenchmarks  {

    private var db: JcDatabase? = null

//    @Benchmark
//    fun jvmRuntime() {
//        db = runBlocking {
//            jacodb {
//                useProcessJavaRuntime()
//            }
//        }
//    }
//
//    @Benchmark
//    fun jvmRuntimeWithUsages() {
//        db = runBlocking {
//            jacodb {
//                useProcessJavaRuntime()
//                installFeatures(Usages)
//            }
//        }
//    }
//
//    @Benchmark
//    fun jvmRuntimeWithAllClasspath() {
//        db = runBlocking {
//            jacodb {
//                useProcessJavaRuntime()
//                loadByteCode(allClasspath)
//            }
//        }
//    }

    private class ClassesTableCollectorTask : JcClassProcessingTask {
        private val _classes: MutableList<JcClassOrInterface> = mutableListOf()

        val classes: List<JcClassOrInterface> = _classes

        override fun process(clazz: JcClassOrInterface) {
            _classes += clazz
        }
    }


    private class CollectorIndexer(
        private val persistence: JcDatabasePersistence,
        private val location: RegisteredLocation,
        private val classesCache: ClassesCache
    ) : ByteCodeIndexer {
        override fun index(classNode: ClassNode) {
            persistence.findSymbolId(classNode.name.className) ?: return

            classesCache.getOrPut(location.id) { ConcurrentHashMap.newKeySet() }.add(classNode)
        }

        override fun flush(jooq: DSLContext) {
            // TODO do nothing
        }
    }


    private object CollectorFeature : JcFeature<Nothing, Nothing> {
        val classesTable = ConcurrentHashMap<JcDatabase, ClassesCache>()

        override suspend fun query(classpath: JcClasspath, req: Nothing): Sequence<Nothing> {
            TODO("Not yet implemented")
        }

        override fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation): ByteCodeIndexer =
            CollectorIndexer(jcdb.persistence, location, classesTable.getOrPut(jcdb) { ConcurrentHashMap() })

        override fun onSignal(signal: JcSignal) {
            when (signal) {
                is JcSignal.BeforeIndexing -> {
                    signal.jcdb.persistence.read { jooq ->
//                        val cache = ClassesCache().also {
//                            classesTable[signal.jcdb] = it
//                        }

//                        jooq.select(CLASSES.NAME, CLASSHIERARCHIES.SUPER_ID, CLASSES.LOCATION_ID)
//                            .from(CLASSHIERARCHIES)
//                            .join(CLASSES).on(CLASSHIERARCHIES.CLASS_ID.eq(CLASSES.ID))
//                            .fetch().forEach { (classSymbolId, superClassId, locationId) ->
//                                cache.getOrPut(superClassId!!) { ConcurrentHashMap() }
//                                    .getOrPut(locationId!!) { ConcurrentHashMap.newKeySet() }
//                                    .add(classSymbolId!!)
//                            }
                    }
                }

                is JcSignal.LocationRemoved -> {
                    signal.jcdb.persistence.write {
                        val id = signal.location.id
                        classesTable[signal.jcdb]?.remove(id)
                    }
                }

                is JcSignal.Drop -> {
                    classesTable[signal.jcdb]?.clear()
                }

                is JcSignal.AfterIndexing -> println(classesTable[signal.jcdb]?.size)
                is JcSignal.Closed -> Unit
            }
        }
    }


    private suspend fun getSuperClasses() {
        db = jacodb {
            useProcessJavaRuntime()
            loadByteCode(allClasspath)
            installFeatures(CollectorFeature)
        }

//        val classesTableCollectorTask = ClassesTableCollectorTask()
//        db!!.classpath(allClasspath).execute(classesTableCollectorTask)
//
//        val failedWithSuperClasses = mutableListOf<JcClassOrInterface>()
//        classesTableCollectorTask.classes.forEach { clazz ->
//            runCatching { clazz.superClass }.onFailure {
//                failedWithSuperClasses += clazz
//            }
//        }
//
//        require(failedWithSuperClasses.isEmpty()) {
//            "Cannot retrieve superclasses for ${failedWithSuperClasses.size} classes:\n" +
//                    failedWithSuperClasses.joinToString("\n") {
//                        "\t${it.name}"
//                    }
//        }
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspathWithUsages() {
        runBlocking { getSuperClasses() }

        println("=".repeat(10))
        CollectorFeature.classesTable.entries.forEach {
            println(it.value.size)
        }
//        db = runBlocking {
//            jacodb {
//                useProcessJavaRuntime()
//                loadByteCode(allClasspath)
//                installFeatures(Usages)
//            }
//        }
//
//        runBlocking {
//            val classesTableCollectorTask = ClassesTableCollectorTask()
//            db!!.classpath(allClasspath).execute(classesTableCollectorTask)
//
//            classesTableCollectorTask.classes.forEach {
//                it.superClass
//            }
//        }
    }

//    @Benchmark
//    fun jvmRuntimeWithGuava() {
//        db = runBlocking {
//            jacodb {
//                useProcessJavaRuntime()
//                loadByteCode(listOf(guavaLib))
//            }
//        }
//    }
//
//    @Benchmark
//    fun jvmRuntimeWithGuavaWithUsages() {
//        db = runBlocking {
//            jacodb {
//                useProcessJavaRuntime()
//                loadByteCode(listOf(guavaLib))
//                installFeatures(Usages)
//            }
//        }
//    }
//
//    @Benchmark
//    fun jvmRuntimeWithIdeaCommunity() {
//        db = runBlocking {
//            jacodb {
//                useProcessJavaRuntime()
//                persistent(File.createTempFile("jcdb-", "-db").absolutePath)
//                loadByteCode(allIdeaJars)
//            }
//        }
//    }
//
//    @Benchmark
//    fun jvmRuntimeIdeaCommunityWithUsages() {
//        db = runBlocking {
//            jacodb {
//                useProcessJavaRuntime()
//                loadByteCode(allIdeaJars)
//                persistent(File.createTempFile("jcdb-", "-db").absolutePath)
//                installFeatures(Usages)
//            }
//        }
//    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        db?.close()
    }
}
