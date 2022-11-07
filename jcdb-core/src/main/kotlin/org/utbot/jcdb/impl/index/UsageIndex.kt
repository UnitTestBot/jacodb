package org.utbot.jcdb.impl.index

import kotlinx.serialization.Serializable
import org.jooq.DSLContext
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.utbot.jcdb.api.ByteCodeIndexer
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcFeature
import org.utbot.jcdb.api.JcSignal
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.storage.SQLitePersistenceImpl
import org.utbot.jcdb.impl.storage.insertElements
import org.utbot.jcdb.impl.storage.jooq.tables.references.CALLS
import org.utbot.jcdb.impl.storage.jooq.tables.references.SYMBOLS
import org.utbot.jcdb.impl.storage.longHash
import java.sql.Types


class UsagesIndexer(private val location: RegisteredLocation, private val jcdb: JCDB) : ByteCodeIndexer {

    // class method -> usages of methods|fields
    private val fieldsUsages = hashMapOf<Pair<String, String>, HashSet<String>>()
    private val methodsUsages = hashMapOf<Pair<String, String>, HashSet<String>>()

    override fun index(classNode: ClassNode) {
    }

    override fun index(classNode: ClassNode, methodNode: MethodNode) {
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

    override fun flush(jooq: DSLContext) {
        jooq.connection { conn ->
            conn.insertElements(CALLS, fieldsUsages.flatme()) {
                val (calleeClass, calleeField, caller) = it
                setLong(1, calleeClass.longHash)
                setLong(2, calleeField.longHash)
                setNull(3, Types.BIGINT)
                setLong(4, caller.longHash)
                setLong(5, location.id)
            }
            conn.insertElements(CALLS, methodsUsages.flatme()) {
                val (calleeClass, calleeMethod, caller) = it
                setLong(1, calleeClass.longHash)
                setNull(2, Types.BIGINT)
                setLong(3, calleeMethod.longHash)
                setLong(4, caller.longHash)
                setLong(5, location.id)
            }
        }
    }

    private fun Map<Pair<String, String>, HashSet<String>>.flatme(): List<Triple<String, String, String>> {
        return flatMap { entry ->
            entry.value.map { Triple(entry.key.first, entry.key.second, it) }
        }
    }
}

@Serializable
data class UsageIndexRequest(
    val method: String?,
    val field: String?,
    val className: String
) : java.io.Serializable


object Usages : JcFeature<UsageIndexRequest, String> {

    private val createIndexes = """
        CREATE INDEX IF NOT EXISTS 'Calls methods' ON Calls(callee_class_hash, callee_method_hash, location_id)
        WHERE callee_field_hash IS NULL;

        CREATE INDEX IF NOT EXISTS 'Calls fields' ON Calls(callee_class_hash, callee_field_hash, location_id)
        WHERE callee_method_hash IS NULL;
    """.trimIndent()

    override fun onSignal(signal: JcSignal) {
        when (signal) {
            is JcSignal.BeforeIndexing -> {
//                if (signal.clearOnStart) {
//                    SchemaUtils.drop(Calls)
//                }
//                SchemaUtils.create(Calls)
            }

            is JcSignal.LocationRemoved -> {
                signal.jcdb.persistence.write {
                    val create = (signal.jcdb.persistence as SQLitePersistenceImpl).jooq
                    create.delete(CALLS).where(CALLS.LOCATION_ID.eq(signal.location.id)).execute()
                }
            }

            is JcSignal.AfterIndexing -> {
                signal.jcdb.persistence.write {
                    val create = (signal.jcdb.persistence as SQLitePersistenceImpl).jooq
                    create.execute(createIndexes)

                }
            }

            is JcSignal.Drop -> {
                signal.jcdb.persistence.write {
                    val create = (signal.jcdb.persistence as SQLitePersistenceImpl).jooq
                    create.delete(CALLS).execute()
                }
            }

            else -> Unit
        }
    }

    override suspend fun query(jcdb: JCDB, req: UsageIndexRequest): Sequence<String> {
        val (method, field, className) = req
        return jcdb.persistence.read {
            val create = (jcdb.persistence as SQLitePersistenceImpl).jooq

            val classHashes: List<Long> = if (method != null) {
                create.select(CALLS.CALLER_CLASS_HASH).from(CALLS)
                    .where(
                        CALLS.CALLEE_CLASS_HASH.eq(className.longHash).and(CALLS.CALLEE_METHOD_HASH.eq(method.longHash))
                    ).fetch().mapNotNull { it.component1() }
            } else if (field != null) {
                create.select(CALLS.CALLER_CLASS_HASH).from(CALLS)
                    .where(
                        CALLS.CALLEE_CLASS_HASH.eq(className.longHash).and(CALLS.CALLEE_FIELD_HASH.eq(field.longHash))
                    ).fetch().mapNotNull { it.component1() }
            } else {
                emptyList()
            }
            create.select(SYMBOLS.NAME).from(SYMBOLS)
                .where(SYMBOLS.HASH.`in`(classHashes))
                .fetch()
                .mapNotNull { it.component1() }.asSequence()
        }
    }

    override fun newIndexer(jcdb: JCDB, location: RegisteredLocation) = UsagesIndexer(location, jcdb)

}

//
//object Calls : Table() {
//
//    val calleeClassHash = long("callee_class_hash")
//    val calleeFieldHash = long("callee_field_hash").nullable()
//    val calleeMethodHash = long("callee_method_hash").nullable()
//    val callerClassHash = long("caller_class_hash")
//    val locationId = long("location_id")
//
//}
