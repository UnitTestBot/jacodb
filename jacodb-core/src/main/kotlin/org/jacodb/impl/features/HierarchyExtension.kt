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

@file:JvmName("JcHierarchies")
@file:Suppress("SqlResolve", "SqlSourceToSinkFlow")

package org.jacodb.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JCDBContext
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.HierarchyExtension
import org.jacodb.api.jvm.ext.JAVA_OBJECT
import org.jacodb.api.jvm.ext.findDeclaredMethodOrNull
import org.jacodb.api.storage.ers.CollectionEntityIterable
import org.jacodb.api.storage.ers.Entity
import org.jacodb.api.storage.ers.EntityIterable
import org.jacodb.api.storage.ers.Transaction
import org.jacodb.api.storage.ers.compressed
import org.jacodb.impl.asSymbolId
import org.jacodb.impl.fs.PersistenceClassSource
import org.jacodb.impl.storage.BatchedSequence
import org.jacodb.impl.storage.defaultBatchSize
import org.jacodb.impl.storage.dslContext
import org.jacodb.impl.storage.ers.toClassSourceSequence
import org.jacodb.impl.storage.execute
import org.jacodb.impl.storage.isSqlContext
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jacodb.impl.storage.txn
import org.jooq.Condition
import org.jooq.Record3
import org.jooq.SelectConditionStep
import org.jooq.impl.DSL
import java.util.concurrent.Future
import org.jacodb.impl.util.Sequence as Sequence

suspend fun JcClasspath.hierarchyExt(): HierarchyExtension {
    db.awaitBackgroundJobs()
    val isSqlDb = db.persistence.read { context ->
        context.isSqlContext
    }
    return if (isSqlDb) HierarchyExtensionSQL(this) else HierarchyExtensionERS(this)
}

fun JcClasspath.asyncHierarchyExt(): Future<HierarchyExtension> = GlobalScope.future { hierarchyExt() }

internal fun JcClasspath.allClassesExceptObject(context: JCDBContext, direct: Boolean): Sequence<ClassSource> {
    val locationIds = registeredLocationIds
    return context.execute(
        sqlAction = { jooq ->
            if (direct) {
                BatchedSequence(defaultBatchSize) { offset, batchSize ->
                    jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                        .from(CLASSES)
                        .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                        .where(
                            locationIds.greaterThan(offset).and(
                                DSL.notExists(
                                    jooq.select(CLASSHIERARCHIES.ID).from(CLASSHIERARCHIES)
                                        .where(
                                            CLASSHIERARCHIES.CLASS_ID.eq(CLASSES.ID)
                                                .and(CLASSHIERARCHIES.IS_CLASS_REF.eq(true))
                                        )
                                )
                            ).and(SYMBOLS.NAME.notEqual(JAVA_OBJECT))
                        )
                        .batchingProcess(this, batchSize)
                }
            } else {
                BatchedSequence(defaultBatchSize) { offset, batchSize ->
                    jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                        .from(CLASSES)
                        .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                        .where(locationIds.greaterThan(offset).and(SYMBOLS.NAME.notEqual(JAVA_OBJECT)))
                        .batchingProcess(this, batchSize)
                }
            }
        },
        noSqlAction = { txn ->
            val objectNameId = db.persistence.findSymbolId(JAVA_OBJECT)
            txn.all("Class").filter { clazz ->
                (!direct || clazz.getCompressed<Long>("inherits") == null) &&
                        clazz.getCompressed<Long>("locationId") in locationIds &&
                        clazz.getCompressed<Long>("nameId") != objectNameId
            }.toClassSourceSequence(db).toList().asSequence()
        }
    )
}

private abstract class HierarchyExtensionBase(protected val cp: JcClasspath) : HierarchyExtension {

    override fun findSubClasses(
        name: String,
        entireHierarchy: Boolean,
        includeOwn: Boolean
    ): Sequence<JcClassOrInterface> {
        return findSubClasses(cp.findClassOrNull(name) ?: return emptySequence(), entireHierarchy, includeOwn)
    }

    override fun findSubClasses(
        jcClass: JcClassOrInterface,
        entireHierarchy: Boolean,
        includeOwn: Boolean
    ): Sequence<JcClassOrInterface> {
        return when {
            jcClass.isFinal -> emptySequence()
            else -> explicitSubClasses(jcClass, entireHierarchy, false)
        }.appendOwn(jcClass, includeOwn)
    }

    override fun findOverrides(jcMethod: JcMethod, includeAbstract: Boolean): Sequence<JcMethod> {
        if (jcMethod.isFinal || jcMethod.isConstructor || jcMethod.isStatic || jcMethod.isClassInitializer) {
            return emptySequence()
        }
        val desc = jcMethod.description
        val name = jcMethod.name
        return explicitSubClasses(jcMethod.enclosingClass, entireHierarchy = true, true)
            .mapNotNull { it.findDeclaredMethodOrNull(name, desc) }
            .filter { !it.isPrivate }
    }

    protected abstract fun explicitSubClasses(
        jcClass: JcClassOrInterface,
        entireHierarchy: Boolean,
        full: Boolean
    ): Sequence<JcClassOrInterface>
}

private class HierarchyExtensionERS(cp: JcClasspath) : HierarchyExtensionBase(cp) {
    override fun explicitSubClasses(
        jcClass: JcClassOrInterface,
        entireHierarchy: Boolean,
        full: Boolean
    ): Sequence<JcClassOrInterface> {
        val name = jcClass.name
        val db = cp.db
        if (db.isInstalled(InMemoryHierarchy)) {
            return cp.findSubclassesInMemory(name, entireHierarchy, full)
        }
        return Sequence {
            val persistence = db.persistence
            persistence.read { context ->
                val txn = context.txn
                if (name == JAVA_OBJECT) {
                    cp.allClassesExceptObject(context, !entireHierarchy)
                } else {
                    val locationIds = cp.registeredLocationIds
                    val nameId = name.asSymbolId(persistence.symbolInterner)
                    if (entireHierarchy) {
                        entireHierarchy(txn, nameId, mutableSetOf())
                    } else {
                        directSubClasses(txn, nameId)
                    }.filter { clazz -> clazz.getCompressed<Long>("locationId") in locationIds }
                        .toClassSourceSequence(db)
                }.mapTo(mutableListOf()) { cp.toJcClass(it) }
            }
        }
    }

    private fun entireHierarchy(txn: Transaction, nameId: Long, result: MutableSet<Entity>): EntityIterable {
        val subClasses = directSubClasses(txn, nameId)
        if (subClasses.isNotEmpty) {
            result += subClasses
            subClasses.forEach { clazz ->
                entireHierarchy(txn, clazz.getCompressed<Long>("nameId")!!, result)
            }
        }
        return CollectionEntityIterable(result)
    }

    private fun directSubClasses(txn: Transaction, nameId: Long): EntityIterable {
        val nameIdCompressed = nameId.compressed
        txn.find("Interface", "nameId", nameIdCompressed).firstOrNull()?.let { i ->
            return i.getLinks("implementedBy")
        }
        return txn.find("Class", "inherits", nameIdCompressed)
    }
}

private class HierarchyExtensionSQL(cp: JcClasspath) : HierarchyExtensionBase(cp) {

    companion object {
        fun entireHierarchyQuery(locationIds: String, sinceId: Long?) = """
            WITH RECURSIVE Hierarchy(class_name_id, class_id) AS (
                SELECT Classes.name, ClassHierarchies.class_id FROM ClassHierarchies
                    JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                    JOIN Classes ON Classes.id = ClassHierarchies.class_id
                    WHERE Symbols.name = ?
                UNION ALL
                SELECT Classes.name, ClassHierarchies.class_id FROM ClassHierarchies
                    JOIN Classes ON Classes.id = ClassHierarchies.class_id
                    JOIN Hierarchy ON Hierarchy.class_name_id = ClassHierarchies.super_id)
            SELECT DISTINCT Classes.id, Classes.location_id,  Symbols.name as name_name, Classes.bytecode from Hierarchy
                JOIN Classes ON Classes.id = hierarchy.class_id
                JOIN Symbols ON Symbols.id = Classes.name
             WHERE location_id in ($locationIds) and ($sinceId is null or Hierarchy.class_id > $sinceId)
             ORDER BY Classes.id
        """.trimIndent()

        fun directSubClassesQuery(locationIds: String, sinceId: Long?) = """
            SELECT Classes.id, Classes.location_id, SymbolsName.name as name_name, Classes.bytecode FROM ClassHierarchies
                JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                JOIN Symbols as SymbolsName ON SymbolsName.id = Classes.name
                JOIN Classes ON Classes.id = ClassHierarchies.class_id
            WHERE Symbols.name = ? and ($sinceId is null or ClassHierarchies.class_id > $sinceId) AND Classes.location_id in ($locationIds)
            ORDER BY Classes.id
        """.trimIndent()

    }

    override fun explicitSubClasses(
        jcClass: JcClassOrInterface,
        entireHierarchy: Boolean,
        full: Boolean
    ): Sequence<JcClassOrInterface> {
        val name = jcClass.name
        if (cp.db.isInstalled(InMemoryHierarchy)) {
            return cp.findSubclassesInMemory(name, entireHierarchy, full)
        }
        return cp.subClasses(name, entireHierarchy).map { cp.toJcClass(it) }
    }
}

private fun Sequence<JcClassOrInterface>.appendOwn(
    root: JcClassOrInterface,
    includeOwn: Boolean
): Sequence<JcClassOrInterface> {
    return if (includeOwn) sequenceOf(root) + this else this
}

private fun JcClasspath.subClasses(name: String, entireHierarchy: Boolean): Sequence<ClassSource> {
    return db.persistence.read { context ->
        if (name == JAVA_OBJECT) {
            allClassesExceptObject(context, !entireHierarchy)
        } else {
            val locationIds = registeredLocationIds.joinToString(", ") { it.toString() }
            val dslContext = context.dslContext
            BatchedSequence(defaultBatchSize) { offset, batchSize ->
                val query = when {
                    entireHierarchy -> HierarchyExtensionSQL.entireHierarchyQuery(locationIds, offset)
                    else -> HierarchyExtensionSQL.directSubClassesQuery(locationIds, offset)
                }
                val cursor = dslContext.fetchLazy(query, name)
                cursor.fetchNext(batchSize).map { record ->
                    val id = record.get(CLASSES.ID)!!
                    id to PersistenceClassSource(
                        db = db,
                        classId = record.get(CLASSES.ID)!!,
                        className = record.get("name_name") as String,
                        locationId = record.get(CLASSES.LOCATION_ID)!!
                    ).bind(record.get(CLASSES.BYTECODE))
                }.also {
                    cursor.close()
                }
            }
        }
    }
}

private fun SelectConditionStep<Record3<Long?, String?, Long?>>.batchingProcess(
    cp: JcClasspath,
    batchSize: Int
): List<Pair<Long, PersistenceClassSource>> {
    return orderBy(CLASSES.ID)
        .limit(batchSize)
        .fetch()
        .mapNotNull { (classId, className, locationId) ->
            classId!! to PersistenceClassSource(
                db = cp.db,
                classId = classId,
                className = className!!,
                locationId = locationId!!
            )
        }
}

private fun Collection<Long>.greaterThan(offset: Long?): Condition {
    return when (offset) {
        null -> CLASSES.LOCATION_ID.`in`(this)
        else -> CLASSES.LOCATION_ID.`in`(this).and(CLASSES.ID.greaterThan(offset))
    }
}