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

package org.jacodb.typesolver

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.jacodb.api.*
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.fs.className
import org.jacodb.impl.jacodb
import org.jacodb.impl.types.signature.*
import org.jacodb.impl.types.typeParameters
import org.jacodb.testing.allClasspath
import org.jacodb.testing.guavaLib
import org.jooq.DSLContext
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private typealias ClassesCache = MutableSet<ClassNode>

private class ClassesTableCollectorTask : JcClassProcessingTask {
    private val _classes: MutableList<JcClassOrInterface> = mutableListOf()

    val classes: List<JcClassOrInterface> = _classes

    override fun process(clazz: JcClassOrInterface) {
        _classes += clazz
    }
}

data class JsonClassRepresentation(
    val name: String,
    val typeParameters: Array<String>,
    val superClass: JsonClassRepresentation?,
    val interfaces: Array<JsonClassRepresentation>,
) {
    constructor(jcClassOrInterface: JcClassOrInterface) : this(
        jcClassOrInterface.name,
        jcClassOrInterface.typeParameters.map { it.toJson() }.toTypedArray(),
        jcClassOrInterface.superClass?.let { JsonClassRepresentation(it) },
        jcClassOrInterface.interfaces.map { JsonClassRepresentation(it) }.toTypedArray()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JsonClassRepresentation

        if (name != other.name) return false
        if (!typeParameters.contentEquals(other.typeParameters)) return false
        if (superClass != other.superClass) return false
        return interfaces.contentEquals(other.interfaces)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + typeParameters.contentHashCode()
        result = 31 * result + (superClass?.hashCode() ?: 0)
        result = 31 * result + interfaces.contentHashCode()
        return result
    }
}

private class CollectorIndexer(
    private val persistence: JcDatabasePersistence,
    private val location: RegisteredLocation,
    private val classesCache: ClassesCache
) : ByteCodeIndexer {
    override fun index(classNode: ClassNode) {
        persistence.findSymbolId(classNode.name.className) ?: return

        classesCache += classNode
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
        CollectorIndexer(jcdb.persistence, location, classesTable.getOrPut(jcdb) { ConcurrentHashMap.newKeySet() })

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
//                signal.jcdb.persistence.write {
//                    val id = signal.location.id
//                    classesTable[signal.jcdb]?.remove(id)
//                }
            }

            is JcSignal.Drop -> {
                classesTable[signal.jcdb]?.clear()
            }

            is JcSignal.AfterIndexing -> println(classesTable[signal.jcdb]?.size)
            is JcSignal.Closed -> Unit
        }
    }
}

private fun JvmTypeParameterDeclaration.toJson(): String {
    return toString()
}

/*suspend fun extractClassesTable(): List<JcClassOrInterface> {
    val jars = allClasspath

    val database = jacodb {
        useProcessJavaRuntime()
        installFeatures(CollectorFeature)
        loadByteCode(jars)
    }
    database.awaitBackgroundJobs()

    return emptyList()
//    val classesTableCollectorTask = ClassesTableCollectorTask()
//    database.classpath(jars).execute(classesTableCollectorTask)
//
//    return classesTableCollectorTask.classes
}

fun main() {
    runBlocking {
        extractClassesTable()
    }

    println("=".repeat(10))
    CollectorFeature.classesTable.entries.forEach {
        println(it.value.size)
    }
//    println(classes.size)
//    val gson = Gson()
//    val json = gson.toJson(classes.map { clazz ->
//        JsonClassRepresentation(clazz).let {
//            gson.toJson(it)
//        }
//    })
//
//    File("guava.json").bufferedWriter().write(json)
}*/
