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

package org.jacodb.impl.features

import org.jacodb.api.jvm.ByteCodeIndexer
import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JCDBContext
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.jvm.JcFeature
import org.jacodb.api.jvm.JcSignal
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.ext.JAVA_OBJECT
import org.jacodb.api.jvm.storage.ers.compressed
import org.jacodb.api.jvm.storage.ers.links
import org.jacodb.impl.asSymbolId
import org.jacodb.impl.fs.PersistenceClassSource
import org.jacodb.impl.fs.className
import org.jacodb.impl.storage.BatchedSequence
import org.jacodb.impl.storage.defaultBatchSize
import org.jacodb.impl.storage.execute
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jacodb.impl.storage.toJCDBContext
import org.jacodb.impl.storage.withoutAutoCommit
import org.jooq.impl.DSL
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import org.jacodb.impl.util.Sequence as Sequence

typealias InMemoryHierarchyCache = ConcurrentHashMap<Long, ConcurrentHashMap<Long, MutableSet<Long>>>

private val objectJvmName = Type.getInternalName(Any::class.java)

class InMemoryHierarchyIndexer(
    persistence: JcDatabasePersistence,
    private val location: RegisteredLocation,
    private val hierarchy: InMemoryHierarchyCache
) : ByteCodeIndexer {

    private val interner = persistence.symbolInterner

    override fun index(classNode: ClassNode) {
        val clazzSymbolId = classNode.name.className.asSymbolId(interner)
        val superName = classNode.superName
        val superclasses = when {
            superName != null && superName != objectJvmName -> classNode.interfaces + superName
            else -> classNode.interfaces
        }
        superclasses.map { it.className.asSymbolId(interner) }
            .forEach {
                hierarchy.getOrPut(it) { ConcurrentHashMap() }
                    .getOrPut(location.id) { ConcurrentHashMap.newKeySet() }
                    .add(clazzSymbolId)
            }
    }

    override fun flush(context: JCDBContext) {
        context.execute(
            sqlAction = { jooq ->
                jooq.withoutAutoCommit { conn ->
                    interner.flush(toJCDBContext(jooq, conn))
                }
            },
            noSqlAction = {
                interner.flush(context)
            }
        )
    }
}

data class InMemoryHierarchyReq(val name: String, val allHierarchy: Boolean = true, val full: Boolean = false)

object InMemoryHierarchy : JcFeature<InMemoryHierarchyReq, ClassSource> {

    private val hierarchies = ConcurrentHashMap<JcDatabase, InMemoryHierarchyCache>()

    override fun onSignal(signal: JcSignal) {
        when (signal) {
            is JcSignal.BeforeIndexing -> {
                signal.jcdb.persistence.read { context ->
                    val cache = InMemoryHierarchyCache().also {
                        hierarchies[signal.jcdb] = it
                    }
                    val result = mutableListOf<Triple<Long?, Long?, Long?>>()
                    context.execute(
                        sqlAction = { jooq ->
                            jooq.select(CLASSES.NAME, CLASSHIERARCHIES.SUPER_ID, CLASSES.LOCATION_ID)
                                .from(CLASSHIERARCHIES)
                                .join(CLASSES).on(CLASSHIERARCHIES.CLASS_ID.eq(CLASSES.ID))
                                .fetch().forEach { (classSymbolId, superClassId, locationId) ->
                                    result += (Triple(classSymbolId, superClassId, locationId))
                                }
                        },
                        noSqlAction = { txn ->
                            txn.all("Class").map { clazz ->
                                val locationId: Long? = clazz.getCompressed("locationId")
                                val classSymbolId: Long? = clazz.getCompressed("nameId")
                                val superClasses = mutableListOf<Long>()
                                clazz.getCompressed<Long>("inherits")?.let { nameId -> superClasses += nameId }
                                links(clazz, "implements").asIterable.forEach { anInterface ->
                                    anInterface.getCompressed<Long>("nameId")?.let { nameId -> superClasses += nameId }
                                }
                                superClasses.forEach { nameId ->
                                    result += (Triple(classSymbolId, nameId, locationId))
                                }
                            }
                        }
                    )
                    result.forEach { (classSymbolId, superClassId, locationId) ->
                        cache.getOrPut(superClassId!!) { ConcurrentHashMap() }
                            .getOrPut(locationId!!) { ConcurrentHashMap.newKeySet() }
                            .add(classSymbolId!!)
                    }
                }
            }

            is JcSignal.LocationRemoved -> {
                signal.jcdb.persistence.write {
                    val id = signal.location.id
                    hierarchies[signal.jcdb]?.values?.forEach {
                        it.remove(id)
                    }
                }
            }

            is JcSignal.Drop -> {
                hierarchies[signal.jcdb]?.clear()
            }

            is JcSignal.Closed -> {
                hierarchies.remove(signal.jcdb)
            }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JcClasspath, req: InMemoryHierarchyReq): Sequence<ClassSource> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JcClasspath, req: InMemoryHierarchyReq): Sequence<ClassSource> {
        val persistence = classpath.db.persistence
        if (req.name == JAVA_OBJECT) {
            return persistence.read { classpath.allClassesExceptObject(it, !req.allHierarchy) }
        }
        val hierarchy = hierarchies[classpath.db] ?: return emptySequence()

        fun getSubclasses(
            symbolId: Long,
            locationIds: Set<Long>,
            transitive: Boolean,
            result: HashSet<Long>
        ) {
            val subclasses = hierarchy[symbolId]?.entries?.flatMap {
                when {
                    locationIds.contains(it.key) -> it.value
                    else -> emptyList()
                }
            }.orEmpty().toSet()
            if (subclasses.isNotEmpty()) {
                result.addAll(subclasses)
            }
            if (transitive) {
                subclasses.forEach {
                    getSubclasses(it, locationIds, true, result)
                }
            }
        }

        val locationIds = classpath.registeredLocationIds
        val classSymbolId = persistence.findSymbolId(req.name)

        val allSubclasses = hashSetOf<Long>()
        getSubclasses(classSymbolId, locationIds, req.allHierarchy, allSubclasses)
        if (allSubclasses.isEmpty()) {
            return emptySequence()
        }
        return Sequence {
            persistence.read { context ->
                context.execute(
                    sqlAction = { jooq ->
                        val allIds = allSubclasses.toList()
                        BatchedSequence<ClassSource>(defaultBatchSize) { offset, batchSize ->
                            val index = offset ?: 0
                            val ids = allIds.subList(index.toInt(), min(allIds.size, index.toInt() + batchSize))
                            if (ids.isEmpty()) {
                                emptyList()
                            } else {
                                jooq.select(
                                    SYMBOLS.NAME, CLASSES.ID, CLASSES.LOCATION_ID, when {
                                        req.full -> CLASSES.BYTECODE
                                        else -> DSL.inline(ByteArray(0)).`as`(CLASSES.BYTECODE)
                                    }
                                ).from(CLASSES)
                                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                                    .where(SYMBOLS.ID.`in`(ids).and(CLASSES.LOCATION_ID.`in`(locationIds)))
                                    .fetch()
                                    .mapNotNull { (className, classId, locationId, byteCode) ->
                                        val source = PersistenceClassSource(
                                            db = classpath.db,
                                            classId = classId!!,
                                            className = className!!,
                                            locationId = locationId!!
                                        ).let {
                                            it.bind(byteCode.takeIf { req.full })
                                        }
                                        (batchSize + index) to source
                                    }
                            }
                        }
                    },
                    noSqlAction = { txn ->
                        allSubclasses.asSequence()
                            .flatMap { classNameId ->
                                txn.find("Class", "nameId", classNameId.compressed)
                                    .filter { clazz ->
                                        clazz.getCompressed<Long>("locationId") in locationIds
                                    }
                            }
                            .map { clazz ->
                                val classId: Long = clazz.id.instanceId
                                PersistenceClassSource(
                                    db = classpath.db,
                                    className = persistence.findSymbolName(clazz.getCompressed<Long>("nameId")!!),
                                    classId = classId,
                                    locationId = clazz.getCompressed<Long>("locationId")!!,
                                    cachedByteCode = if (req.full) persistence.findBytecode(classId) else null
                                )
                            }
                    }
                )
                    // Eager evaluation is needed, because all computations must be done within current transaction,
                    // i.e. ERS can't be used outside `persistence.read { ... }`, when sequence is actually iterated
                    .toList()
            }
        }
    }

    override fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation): ByteCodeIndexer {
        return InMemoryHierarchyIndexer(jcdb.persistence, location, hierarchies.getOrPut(jcdb) { ConcurrentHashMap() })
    }

}

internal fun JcClasspath.findSubclassesInMemory(
    name: String,
    allHierarchy: Boolean,
    full: Boolean
): Sequence<JcClassOrInterface> {
    return InMemoryHierarchy.syncQuery(this, InMemoryHierarchyReq(name, allHierarchy, full)).map {
        toJcClass(it)
    }
}
