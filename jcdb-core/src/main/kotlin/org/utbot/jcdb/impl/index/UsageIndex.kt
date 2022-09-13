package org.utbot.jcdb.impl.index

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.utbot.jcdb.api.ByteCodeIndexer
import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.Feature
import org.utbot.jcdb.api.FeaturePersistence
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JCDBFeature
import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.impl.storage.BytecodeLocationEntity.Companion.findOrNew
import org.utbot.jcdb.impl.storage.BytecodeLocationEntity.Companion.findOrNull
import org.utbot.jcdb.impl.storage.SQLitePersistenceImpl
import org.utbot.jcdb.impl.storage.Symbols
import org.utbot.jcdb.impl.storage.longHash


class ReversedUsageIndexer(private val jcdb: JCDB, private val location: ByteCodeLocation) : ByteCodeIndexer {

    // class method -> usages of methods|fields
    private val fieldsUsages = hashMapOf<Pair<Long, Long>, HashSet<Long>>()
    private val methodsUsages = hashMapOf<Pair<Long, Long>, HashSet<Long>>()

    override suspend fun index(classNode: ClassNode) {
    }

    override suspend fun index(classNode: ClassNode, methodNode: MethodNode) {
        if (methodNode.access and Opcodes.ACC_PRIVATE != 0) {
            return
        }
        val pureName = Type.getObjectType(classNode.name).className
        val id = pureName.longHash
        methodNode.instructions.forEach {
            when (it) {
                is FieldInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = owner.longHash to it.name.longHash
                    fieldsUsages.getOrPut(key) { hashSetOf() }.add(id)
                }

                is MethodInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = owner.longHash to it.name.longHash
                    methodsUsages.getOrPut(key) { hashSetOf() }.add(id)
                }
            }
        }
    }

    override fun flush() {
        jcdb.persistence as SQLitePersistenceImpl
        transaction((jcdb.persistence as SQLitePersistenceImpl).db) {
            val locationEntity = location.findOrNew()
            Calls.batchInsert(
                fieldsUsages.entries.flatMap { entry -> entry.value.map { Pair(entry.key, it) } },
                shouldReturnGeneratedValues = false
            ) {
                this[Calls.callerClassHash] = it.first.first
                this[Calls.callerFieldHash] = it.first.second
                this[Calls.ownerClassHash] = it.second
                this[Calls.locationId] = locationEntity.id.value
            }
            Calls.batchInsert(
                methodsUsages.entries.flatMap { entry -> entry.value.map { Pair(entry.key, it) } },
                shouldReturnGeneratedValues = false
            ) {
                this[Calls.callerClassHash] = it.first.first
                this[Calls.callerMethodHash] = it.first.second
                this[Calls.ownerClassHash] = it.second
                this[Calls.locationId] = locationEntity.id.value
            }
        }
    }

}

data class UsageIndexRequest(
    val method: String?,
    val field: String?,
    val className: String
)


private class JCDBUsageFeature(override val jcdb: JCDB) : JCDBFeature<UsageIndexRequest, String> {

    override val persistence = object : FeaturePersistence {

        override val jcdbPersistence: JCDBPersistence
            get() = jcdb.persistence ?: throw IllegalStateException("JCDB persistence is required for using feature persistence")

        override fun beforeIndexing(clearOnStart: Boolean) {
            if (clearOnStart) {
                SchemaUtils.drop(Calls)
            }
            SchemaUtils.create(Calls)
        }

        override fun onBatchLoadingEnd() {
            TODO("Not yet implemented")
        }
    }

    override val key: String
        get() = Usages.key

    override suspend fun query(req: UsageIndexRequest): Sequence<String> {
        val (method, field, className) = req
        return transaction {
            val classHashes: List<Long> = if (method != null) {
                Calls.select {
                    (Calls.callerClassHash eq className.longHash) and (Calls.callerMethodHash eq method.longHash)
                }.map { it[Calls.ownerClassHash] }
            } else if (field != null) {
                Calls.select {
                    (Calls.callerClassHash eq className.longHash) and (Calls.callerFieldHash eq field.longHash)
                }.map { it[Calls.ownerClassHash] }
            } else {
                emptyList()
            }
            Symbols.select { Symbols.hash inList classHashes }.map { it[Symbols.name] }.asSequence()
        }

    }

    override fun newIndexer(location: ByteCodeLocation) = ReversedUsageIndexer(jcdb, location)

    override fun onLocationRemoved(location: ByteCodeLocation) {
        jcdb.persistence?.write {
            val loc = location.findOrNull()
            if (loc != null) {
                Calls.deleteWhere { Calls.locationId eq loc.id.value }
            }
        }

    }
}


object Usages : Feature<UsageIndexRequest, String> {

    override val key = "usages"

    override fun featureOf(jcdb: JCDB): JCDBFeature<UsageIndexRequest, String> = JCDBUsageFeature(jcdb)
}

object Calls : Table() {

    val callerClassHash = long("caller_class_hash")
    val callerFieldHash = long("caller_field_hash").nullable()
    val callerMethodHash = long("caller_method_hash").nullable()
    val ownerClassHash = long("owner_class_hash")
    val locationId = integer("location_id")

}


