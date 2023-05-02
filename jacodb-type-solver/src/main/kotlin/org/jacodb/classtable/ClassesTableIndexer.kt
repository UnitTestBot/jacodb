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

package org.jacodb.classtable

import org.jacodb.api.*
import org.jacodb.impl.fs.className
import org.jacodb.impl.jacodb
import org.jooq.DSLContext
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.concurrent.ConcurrentHashMap

typealias ClassesCache = ConcurrentHashMap<Long, MutableSet<ClassNode>>

internal class ClassesTableIndexer(
    private val persistence: JcDatabasePersistence,
    private val location: RegisteredLocation,
    private val classesCache: ClassesCache
) : ByteCodeIndexer {
    override fun index(classNode: ClassNode) {
        if (persistence.findSymbolId(classNode.name.className) == null) {
            return
        }

        classesCache.getOrPut(location.id) { ConcurrentHashMap.newKeySet() } += classNode
    }

    override fun flush(jooq: DSLContext) {
        // Do nothing
    }
}

private object ClassesTableFeature : JcFeature<Nothing, Nothing> { // TODO use proper generics here
    val classesTable = ConcurrentHashMap<JcDatabase, ClassesCache>()

    override suspend fun query(classpath: JcClasspath, req: Nothing): Sequence<Nothing> {
        TODO("Not yet implemented")
    }

    override fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation): ByteCodeIndexer =
        ClassesTableIndexer(jcdb.persistence, location, classesTable.getOrPut(jcdb) { ConcurrentHashMap() })

    override fun onSignal(signal: JcSignal) {
        when (signal) {
            is JcSignal.BeforeIndexing -> {
                signal.jcdb.persistence.read { _ ->
                    // TODO process table here

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
            else -> Unit
        }
    }
}

suspend fun extractClassNodesTable(classPath: List<File>): List<ClassNode> {
    val db = jacodb {
        useProcessJavaRuntime()
        installFeatures(ClassesTableFeature)
        loadByteCode(classPath)
    }
    db.awaitBackgroundJobs()

    return ClassesTableFeature.classesTable[db]!!.values.flatten()
}
