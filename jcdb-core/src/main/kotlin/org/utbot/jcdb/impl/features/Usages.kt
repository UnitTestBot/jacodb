package org.utbot.jcdb.impl.features

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
import org.utbot.jcdb.impl.storage.executeQueries
import org.utbot.jcdb.impl.storage.insertElements
import org.utbot.jcdb.impl.storage.jooq.tables.references.CALLS
import org.utbot.jcdb.impl.storage.jooq.tables.references.SYMBOLS
import org.utbot.jcdb.impl.storage.longHash
import java.sql.Types


class UsagesIndexer(private val location: RegisteredLocation) : ByteCodeIndexer {

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
            conn.insertElements(CALLS, fieldsUsages.flatten()) {
                val (calleeClass, calleeField, caller) = it
                setLong(1, calleeClass.longHash)
                setLong(2, calleeField.longHash)
                setNull(3, Types.BIGINT)
                setLong(4, caller.longHash)
                setLong(5, location.id)
            }
            conn.insertElements(CALLS, methodsUsages.flatten()) {
                val (calleeClass, calleeMethod, caller) = it
                setLong(1, calleeClass.longHash)
                setNull(2, Types.BIGINT)
                setLong(3, calleeMethod.longHash)
                setLong(4, caller.longHash)
                setLong(5, location.id)
            }
        }
    }

    private fun Map<Pair<String, String>, HashSet<String>>.flatten(): List<Triple<String, String, String>> {
        return flatMap { entry ->
            entry.value.map { Triple(entry.key.first, entry.key.second, it) }
        }
    }
}


object Usages : JcFeature<UsageFeatureRequest, String> {

    private val createScheme = """
        CREATE TABLE IF NOT EXISTS "Calls"(
            "callee_class_hash"  BIGINT NOT NULL,
            "callee_field_hash"  BIGINT,
            "callee_method_hash" BIGINT,
            "caller_class_hash"  BIGINT NOT NULL,
            "location_id"        BIGINT NOT NULL
        );
        
        CREATE INDEX IF NOT EXISTS 'Calls methods' ON Calls(callee_class_hash, callee_method_hash, location_id)
        WHERE callee_field_hash IS NULL;

        CREATE INDEX IF NOT EXISTS 'Calls fields' ON Calls(callee_class_hash, callee_field_hash, location_id)
        WHERE callee_method_hash IS NULL;
    """.trimIndent()

    private val dropScheme = """
        DROP TABLE IF EXISTS "Calls";
        DROP INDEX IF EXISTS "Calls methods";
        DROP INDEX IF EXISTS "Calls fields";
    """.trimIndent()

    override fun onSignal(signal: JcSignal) {
        when (signal) {
            is JcSignal.BeforeIndexing -> {
                signal.jcdb.persistence.write {
                    if (signal.clearOnStart) {
                        it.executeQueries(dropScheme)
                    }
                    it.executeQueries(createScheme)
                }
            }

            is JcSignal.LocationRemoved -> {
                signal.jcdb.persistence.write {
                    it.delete(CALLS).where(CALLS.LOCATION_ID.eq(signal.location.id)).execute()
                }
            }

            is JcSignal.AfterIndexing -> {
                signal.jcdb.persistence.write {
                    it.execute(createScheme)
                }
            }

            is JcSignal.Drop -> {
                signal.jcdb.persistence.write {
                    it.delete(CALLS).execute()
                }
            }
            else -> Unit
        }
    }

    override suspend fun query(jcdb: JCDB, req: UsageFeatureRequest): Sequence<String> {
        val (method, field, className) = req
        return jcdb.persistence.read { jooq ->
            val classHashes: List<Long> = if (method != null) {
                jooq.select(CALLS.CALLER_CLASS_HASH).from(CALLS)
                    .where(
                        CALLS.CALLEE_CLASS_HASH.eq(className.longHash).and(CALLS.CALLEE_METHOD_HASH.eq(method.longHash))
                    ).fetch().mapNotNull { it.component1() }
            } else if (field != null) {
                jooq.select(CALLS.CALLER_CLASS_HASH).from(CALLS)
                    .where(
                        CALLS.CALLEE_CLASS_HASH.eq(className.longHash).and(CALLS.CALLEE_FIELD_HASH.eq(field.longHash))
                    ).fetch().mapNotNull { it.component1() }
            } else {
                emptyList()
            }
            jooq.select(SYMBOLS.NAME).from(SYMBOLS)
                .where(SYMBOLS.HASH.`in`(classHashes))
                .fetch()
                .mapNotNull { it.component1() }.asSequence()
        }
    }

    override fun newIndexer(jcdb: JCDB, location: RegisteredLocation) = UsagesIndexer(location)

}