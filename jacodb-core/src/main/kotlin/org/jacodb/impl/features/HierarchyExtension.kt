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

package org.jacodb.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.api.ext.JAVA_OBJECT
import org.jacodb.api.ext.findDeclaredMethodOrNull
import org.jacodb.impl.fs.PersistenceClassSource
import org.jacodb.impl.storage.BatchedSequence
import org.jacodb.impl.storage.defaultBatchSize
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.CLASSHIERARCHIES
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jooq.Condition
import org.jooq.Record3
import org.jooq.SelectConditionStep
import org.jooq.impl.DSL
import java.util.concurrent.Future

@Suppress("SqlResolve")
class HierarchyExtensionImpl(private val cp: JcClasspath) : HierarchyExtension {

    companion object {
        private fun allHierarchyQuery(locationIds: String, sinceId: Long?) = """
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

        private fun directSubClassesQuery(locationIds: String, sinceId: Long?) = """
            SELECT Classes.id, Classes.location_id, SymbolsName.name as name_name, Classes.bytecode FROM ClassHierarchies
                JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                JOIN Symbols as SymbolsName ON SymbolsName.id = Classes.name
                JOIN Classes ON Classes.id = ClassHierarchies.class_id
            WHERE Symbols.name = ? and ($sinceId is null or ClassHierarchies.class_id > $sinceId) AND Classes.location_id in ($locationIds)
            ORDER BY Classes.id
        """.trimIndent()

    }

    override fun findSubClasses(name: String, allHierarchy: Boolean, includeOwn: Boolean): Sequence<JcClassOrInterface> {
        val jcClass = cp.findClassOrNull(name) ?: return emptySequence()
        return when {
            jcClass.isFinal -> emptySequence()
            cp.db.isInstalled(InMemoryHierarchy) -> cp.findSubclassesInMemory(name, allHierarchy, false)
            else -> findSubClasses(jcClass, allHierarchy, false)
        }.appendOwn(jcClass, includeOwn)
    }

    private fun Sequence<JcClassOrInterface>.appendOwn(root: JcClassOrInterface, includeOwn: Boolean): Sequence<JcClassOrInterface> {
        return if(includeOwn) sequenceOf(root) + this else this
    }

    override fun findSubClasses(jcClass: JcClassOrInterface, allHierarchy: Boolean, includeOwn: Boolean): Sequence<JcClassOrInterface> {
        if (jcClass.isFinal) {
            return emptySequence<JcClassOrInterface>().appendOwn(jcClass, includeOwn)
        }
        return when {
            jcClass.isFinal -> emptySequence()
            else -> explicitSubClasses(jcClass, allHierarchy, false)
        }.appendOwn(jcClass, includeOwn)
    }

    override fun findOverrides(jcMethod: JcMethod, includeAbstract: Boolean): Sequence<JcMethod> {
        if (jcMethod.isFinal || jcMethod.isConstructor || jcMethod.isStatic || jcMethod.isClassInitializer) {
            return emptySequence()
        }
        val desc = jcMethod.description
        val name = jcMethod.name
        return explicitSubClasses(jcMethod.enclosingClass, allHierarchy = true, true)
            .mapNotNull { it.findDeclaredMethodOrNull(name, desc) }
            .filter { !it.isPrivate }
    }

    private fun explicitSubClasses(
        jcClass: JcClassOrInterface,
        allHierarchy: Boolean,
        full: Boolean
    ): Sequence<JcClassOrInterface> {
        if (cp.db.isInstalled(InMemoryHierarchy)) {
            return cp.findSubclassesInMemory(jcClass.name, allHierarchy, full)
        }
        val name = jcClass.name

        return cp.subClasses(name, allHierarchy).map { cp.toJcClass(it) }
    }


    private fun JcClasspath.subClasses(
        name: String,
        allHierarchy: Boolean
    ): Sequence<PersistenceClassSource> {
        val locationIds = registeredLocations.joinToString(", ") { it.id.toString() }
        if (name == JAVA_OBJECT) {
            return allClassesExceptObject(!allHierarchy)
        }
        return BatchedSequence(defaultBatchSize) { offset, batchSize ->
            val query = when {
                allHierarchy -> allHierarchyQuery(locationIds, offset)
                else -> directSubClassesQuery(locationIds, offset)
            }
            db.persistence.read {
                val cursor = it.fetchLazy(query, name)
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

suspend fun JcClasspath.hierarchyExt(): HierarchyExtensionImpl {
    db.awaitBackgroundJobs()
    return HierarchyExtensionImpl(this)
}

fun JcClasspath.asyncHierarchy(): Future<HierarchyExtension> = GlobalScope.future { hierarchyExt() }

private fun SelectConditionStep<Record3<Long?, String?, Long?>>.batchingProcess(cp: JcClasspath, batchSize: Int): List<Pair<Long, PersistenceClassSource>>{
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

private fun List<Long>.where(offset: Long?): Condition {
    return when (offset) {
        null -> CLASSES.LOCATION_ID.`in`(this)
        else -> CLASSES.LOCATION_ID.`in`(this).and(CLASSES.ID.greaterThan(offset))
    }
}

internal fun JcClasspath.allClassesExceptObject(direct: Boolean): Sequence<PersistenceClassSource> {
    val locationIds = registeredLocations.map { it.id }
    if (direct) {
        return BatchedSequence(defaultBatchSize) { offset, batchSize ->
            db.persistence.read { jooq ->
                val whereCondition = locationIds.where(offset)
                jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                    .from(CLASSES)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                    .where(
                        whereCondition
                            .and(DSL.notExists(
                                jooq.select(CLASSHIERARCHIES.ID).from(CLASSHIERARCHIES)
                                .where(CLASSHIERARCHIES.CLASS_ID.eq(CLASSES.ID).and(CLASSHIERARCHIES.IS_CLASS_REF.eq(true)))
                            ))
                            .and(SYMBOLS.NAME.notEqual(JAVA_OBJECT))
                    )
                    .batchingProcess(this, batchSize)
                }
            }
        }
        return BatchedSequence(defaultBatchSize) { offset, batchSize ->
            db.persistence.read { jooq ->
                val whereCondition = locationIds.where(offset)

                jooq.select(CLASSES.ID, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                    .from(CLASSES)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                    .where(whereCondition.and(SYMBOLS.NAME.notEqual(JAVA_OBJECT)))
                    .batchingProcess(this, batchSize)
            }
        }
    }
