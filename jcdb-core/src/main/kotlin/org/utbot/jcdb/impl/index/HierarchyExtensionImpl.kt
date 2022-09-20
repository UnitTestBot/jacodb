package org.utbot.jcdb.impl.index

import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.api.findMethodOrNull
import org.utbot.jcdb.impl.storage.Classes
import org.utbot.jcdb.impl.storage.SymbolEntity
import org.utbot.jcdb.impl.storage.Symbols

class HierarchyExtensionImpl(private val db: JCDB, private val cp: JcClasspath) : HierarchyExtension {

    override suspend fun findSubClasses(name: String, allHierarchy: Boolean): List<JcClassOrInterface> {
        val classId = cp.findClassOrNull(name) ?: return emptyList()
        return findSubClasses(classId, allHierarchy)
    }

    override suspend fun findSubClasses(classId: JcClassOrInterface, allHierarchy: Boolean): List<JcClassOrInterface> {
        db.awaitBackgroundJobs()
        val name = classId.name

        return subClasses(name, allHierarchy).mapNotNull { cp.findClassOrNull(it) }
    }

    override suspend fun findOverrides(methodId: JcMethod): List<JcMethod> {
        val desc = methodId.description
        val name = methodId.name
        val subClasses = cp.findSubClasses(methodId.jcClass, allHierarchy = true)
        return subClasses.mapNotNull {
            it.findMethodOrNull(name, desc) // todo this is wrong
        }
    }

    private fun subClasses(name: String, allHierarchy: Boolean): List<String> {
        val subTypes = transaction {
            val nameEntity = SymbolEntity.find { Symbols.name eq name }.firstOrNull()
            if (nameEntity == null) {
                emptyList()
            } else {
                Classes.join(
                    Symbols,
                    joinType = JoinType.INNER,
                    onColumn = Classes.superClass,
                    otherColumn = Symbols.id
                ).slice(Classes.name).select {
                    Classes.superClass eq nameEntity.id
                }.map { it[Symbols.name] }.toHashSet()
            }

        }
        if (allHierarchy) {
            return (subTypes + subTypes.flatMap { subClasses(it, true) }).toPersistentList()
        }
        return subTypes.toPersistentList()
    }

}


val JcClasspath.hierarchyExt: HierarchyExtensionImpl
    get() {
        return HierarchyExtensionImpl(db, this)
    }