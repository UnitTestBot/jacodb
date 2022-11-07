package org.utbot.jcdb.impl.index

import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.api.findMethodOrNull
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.fs.ClassSourceImpl
import org.utbot.jcdb.impl.storage.SQLitePersistenceImpl

class HierarchyExtensionImpl(private val cp: JcClasspath) : HierarchyExtension {

    companion object {
        private fun allHierarchyQuery(locationIds: String) = """
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
             WHERE location_id in ($locationIds)
        """.trimIndent()

        private fun directSubClassesQuery(locationIds: String) = """
            SELECT Classes.id, Classes.location_id, SymbolsName.name as name_name, Classes.bytecode FROM ClassHierarchies
                JOIN Symbols ON Symbols.id = ClassHierarchies.super_id
                JOIN Symbols as SymbolsName ON SymbolsName.id = Classes.name
                JOIN Classes ON Classes.id = ClassHierarchies.class_id
            WHERE Symbols.name = ? AND Classes.location_id in ($locationIds)
        """.trimIndent()

    }

    private val create = (cp.db.persistence as SQLitePersistenceImpl).create

    override fun findSubClasses(name: String, allHierarchy: Boolean): List<JcClassOrInterface> {
        val classId = cp.findClassOrNull(name) ?: return emptyList()
        return findSubClasses(classId, allHierarchy)
    }

    override fun findSubClasses(classId: JcClassOrInterface, allHierarchy: Boolean): List<JcClassOrInterface> {
        val name = classId.name

        return cp.subClasses(name, allHierarchy).map { record ->
            JcClassOrInterfaceImpl(
                cp, ClassSourceImpl(
                    location = cp.registeredLocations.first { it.id == record.locationId },
                    className = record.name,
                    byteCode = record.byteCode
                )
            )
        }
    }

    override fun findOverrides(methodId: JcMethod): List<JcMethod> {
        val desc = methodId.description
        val name = methodId.name
        val subClasses = findSubClasses(methodId.enclosingClass, allHierarchy = true)
        return subClasses.mapNotNull {
            it.findMethodOrNull(name, desc) // todo this is wrong
        }
    }

    private fun JcClasspath.subClasses(name: String, allHierarchy: Boolean): List<ClassRecord> {
        val locationIds = registeredLocations.joinToString(", ") { it.id.toString() }
        return db.persistence.read {
            val query = if (allHierarchy) allHierarchyQuery(locationIds) else directSubClassesQuery(locationIds)

            create.fetch(query).map {
                ClassRecord(
                    byteCode = it.get("bytecode") as ByteArray,
                    locationId = it.get("location_id") as Long,
                    name = it.get("name_name") as String
                )
            }
        }
    }
}

private class ClassRecord(
    val byteCode: ByteArray,
    val locationId: Long,
    val name: String
)

suspend fun JcClasspath.hierarchyExt(): HierarchyExtensionImpl {
    db.awaitBackgroundJobs()
    return HierarchyExtensionImpl(this)
}