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
package org.utbot.jacodb.impl.features

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.*
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.ext.HierarchyExtension
import org.utbot.jacodb.api.ext.findMethodOrNull
import org.utbot.jacodb.api.ext.isClassInitializer
import org.utbot.jacodb.api.ext.isConstructor
import org.utbot.jacodb.api.ext.isFinal
import org.utbot.jacodb.api.ext.isPrivate
import org.utbot.jacodb.api.ext.isStatic
import org.utbot.jacodb.impl.fs.PersistenceClassSource
import org.utbot.jacodb.impl.storage.BatchedSequence
import org.utbot.jacodb.impl.storage.jooq.tables.references.CLASSES
import java.util.concurrent.Future

@Suppress("SqlResolve")
class HierarchyExtensionImpl(private val cp: JcClasspath) : HierarchyExtension {

    companion object {
        private fun allHierarchyQuery(locationIds: String, sinceId: Long?) = """
            WITH RECURSIVE Hierarchy(class_name_id, class_id) AS (
                SELECT Classes.name, ClassHierarchies.class_id FROM ClassHierarchies
                    JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                    JOIN Classes ON Classes.id = ClassHierarchies.class_id
                    WHERE Symbols.name = ? and ($sinceId is null or ClassHierarchies.class_id > $sinceId)
                UNION ALL
                SELECT Classes.name, ClassHierarchies.class_id FROM ClassHierarchies
                    JOIN Classes ON Classes.id = ClassHierarchies.class_id
                    JOIN Hierarchy ON Hierarchy.class_name_id = ClassHierarchies.super_id
                    WHERE $sinceId is null or ClassHierarchies.id > $sinceId)
            SELECT DISTINCT Classes.id, Classes.location_id,  Symbols.name as name_name, Classes.bytecode from Hierarchy
                JOIN Classes ON Classes.id = hierarchy.class_id
                JOIN Symbols ON Symbols.id = Classes.name
             WHERE location_id in ($locationIds)
        """.trimIndent()

        private fun directSubClassesQuery(locationIds: String, sinceId: Long?) = """
            SELECT Classes.id, Classes.location_id, SymbolsName.name as name_name, Classes.bytecode FROM ClassHierarchies
                JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                JOIN Symbols as SymbolsName ON SymbolsName.id = Classes.name
                JOIN Classes ON Classes.id = ClassHierarchies.class_id
            WHERE Symbols.name = ? and ($sinceId is null or ClassHierarchies.class_id > $sinceId) AND Classes.location_id in ($locationIds) 
        """.trimIndent()

    }

    override fun findSubClasses(name: String, allHierarchy: Boolean): Sequence<JcClassOrInterface> {
        val jcClass = cp.findClassOrNull(name) ?: return emptySequence()
        if (jcClass.isFinal) {
            return emptySequence()
        }
        if (cp.db.isInstalled(InMemoryHierarchy)) {
            return cp.findSubclassesInMemory(name, allHierarchy, false)
        }
        return findSubClasses(jcClass, allHierarchy)
    }

    override fun findSubClasses(jcClass: JcClassOrInterface, allHierarchy: Boolean): Sequence<JcClassOrInterface> {
        if (jcClass.isFinal) {
            return emptySequence()
        }
        return findSubClasses(jcClass, allHierarchy, false)
    }

    override fun findOverrides(jcMethod: JcMethod, includeAbstract: Boolean): Sequence<JcMethod> {
        if (jcMethod.isFinal || jcMethod.isConstructor || jcMethod.isStatic || jcMethod.isClassInitializer) {
            return emptySequence()
        }
        val desc = jcMethod.description
        val name = jcMethod.name
        return findSubClasses(jcMethod.enclosingClass, allHierarchy = true, true)
            .mapNotNull { it.findMethodOrNull(name, desc) }
            .filter { !it.isPrivate }
    }

    private fun findSubClasses(
        jcClass: JcClassOrInterface,
        allHierarchy: Boolean,
        full: Boolean
    ): Sequence<JcClassOrInterface> {
        if (cp.db.isInstalled(InMemoryHierarchy)) {
            return cp.findSubclassesInMemory(jcClass.name, allHierarchy, full)
        }
        val name = jcClass.name

        return cp.subClasses(name, allHierarchy, full).map { record ->
            cp.toJcClass(
                PersistenceClassSource(
                    classpath = cp,
                    locationId = record.locationId,
                    classId = record.id,
                    className = record.name
                ).bind(record.byteCode),
                withCaching = true
            )
        }
    }


    private fun JcClasspath.subClasses(name: String, allHierarchy: Boolean, full: Boolean): Sequence<ClassRecord> {
        val locationIds = registeredLocations.joinToString(", ") { it.id.toString() }
        return BatchedSequence(50) { offset, batchSize ->
            val query = when {
                allHierarchy -> allHierarchyQuery(locationIds, offset)
                else -> directSubClassesQuery(locationIds, offset)
            }
            db.persistence.read {
                val cursor = it.fetchLazy(query, name)
                cursor.fetchNext(batchSize).map { record ->
                    val id = record.get(CLASSES.ID)!!
                    id to ClassRecord(
                        id = record.get(CLASSES.ID)!!,
                        name = record.get("name_name") as String,
                        locationId = record.get(CLASSES.LOCATION_ID)!!,
                        byteCode = if (full) record.get(CLASSES.BYTECODE) else null
                    )
                }.also {
                    cursor.close()
                }
            }
        }

    }
}

private class ClassRecord(
    val id: Long,
    val name: String,
    val locationId: Long,
    val byteCode: ByteArray? = null
)


suspend fun JcClasspath.hierarchyExt(): HierarchyExtensionImpl {
    db.awaitBackgroundJobs()
    return HierarchyExtensionImpl(this)
}

fun JcClasspath.asyncHierarchy(): Future<HierarchyExtension> = GlobalScope.future { hierarchyExt() }

