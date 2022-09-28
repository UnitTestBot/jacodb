package org.utbot.jcdb.impl.index

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.utbot.jcdb.api.ByteCodeIndexer
import org.utbot.jcdb.api.Feature
import org.utbot.jcdb.api.FeaturePersistence
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JCDBFeature
import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.storage.Symbols
import org.utbot.jcdb.impl.storage.longHash


class UsagesIndexer(private val jcdb: JCDB, private val location: RegisteredLocation) : ByteCodeIndexer {

    // class method -> usages of methods|fields
    private val fieldsUsages = hashMapOf<Pair<String, String>, HashSet<String>>()
    private val methodsUsages = hashMapOf<Pair<String, String>, HashSet<String>>()

    override suspend fun index(classNode: ClassNode) {
    }

    override suspend fun index(classNode: ClassNode, methodNode: MethodNode) {
        val callerClass = Type.getObjectType(classNode.name).className
        methodNode.instructions.forEach {
            when (it) {
                is FieldInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = owner to it.name
                    fieldsUsages.getOrPut(key) { hashSetOf() }.add(callerClass)
                }

                is MethodInsnNode -> {
                    val owner = Type.getObjectType(it.owner).className
                    val key = owner to it.name
                    methodsUsages.getOrPut(key) { hashSetOf() }.add(callerClass)
                }
            }
        }
    }

    override fun flush() {
        jcdb.persistence.write {
            Calls.batchInsert(
                fieldsUsages.entries.flatMap { entry -> entry.value.map { Triple(entry.key.first, entry.key.second, it) } },
                shouldReturnGeneratedValues = false
            ) {
                this[Calls.ownerClassHash] = it.first.longHash
                this[Calls.ownerFieldHash] = it.second.longHash
                this[Calls.callerClassHash] = it.third.longHash
                this[Calls.locationId] = location.id
            }
            Calls.batchInsert(
                methodsUsages.entries.flatMap { entry -> entry.value.map { Triple(entry.key.first, entry.key.second, it) } },
                shouldReturnGeneratedValues = false
            ) {
                this[Calls.ownerClassHash] = it.first.longHash
                this[Calls.ownerMethodHash] = it.second.longHash
                this[Calls.callerClassHash] = it.third.longHash
                this[Calls.locationId] = location.id
            }
        }
    }

}

@Serializable
data class UsageIndexRequest(
    val method: String?,
    val field: String?,
    val className: String
): java.io.Serializable


private class JCDBUsageFeature(override val jcdb: JCDB) : JCDBFeature<UsageIndexRequest, String> {

    override val persistence = object : FeaturePersistence {

        override val jcdbPersistence: JCDBPersistence
            get() = jcdb.persistence

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
        return jcdb.persistence.read {
            val classHashes: List<Long> = if (method != null) {
                Calls.select {
                    (Calls.ownerClassHash eq className.longHash) and (Calls.ownerMethodHash eq method.longHash)
                }.map { it[Calls.callerClassHash] }
            } else if (field != null) {
                Calls.select {
                    (Calls.ownerClassHash eq className.longHash) and (Calls.ownerFieldHash eq field.longHash)
                }.map { it[Calls.callerClassHash] }
            } else {
                emptyList()
            }
            Symbols.select { Symbols.hash inList classHashes }.map { it[Symbols.name] }.asSequence()
        }

    }

    override fun newIndexer(location: RegisteredLocation) = UsagesIndexer(jcdb, location)

    override fun onLocationRemoved(location: RegisteredLocation) {
        jcdb.persistence.write {
            Calls.deleteWhere { Calls.locationId eq location.id }
        }
    }
}


object Usages : Feature<UsageIndexRequest, String> {

    override val key = "usages"

    override fun featureOf(jcdb: JCDB): JCDBFeature<UsageIndexRequest, String> = JCDBUsageFeature(jcdb)
}

object Calls : Table() {

    val ownerClassHash = long("owner_class_hash")
    val ownerFieldHash = long("owner_field_hash").nullable()
    val ownerMethodHash = long("owner_method_hash").nullable()
    val callerClassHash = long("caller_class_hash")
    val locationId = long("location_id")

}


