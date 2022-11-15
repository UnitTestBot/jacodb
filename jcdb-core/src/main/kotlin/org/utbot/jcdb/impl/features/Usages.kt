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
import org.utbot.jcdb.impl.fs.ClassSourceImpl
import org.utbot.jcdb.impl.storage.BatchedSequence
import org.utbot.jcdb.impl.storage.eqOrNull
import org.utbot.jcdb.impl.storage.executeQueries
import org.utbot.jcdb.impl.storage.jooq.tables.references.CALLS
import org.utbot.jcdb.impl.storage.jooq.tables.references.CLASSES
import org.utbot.jcdb.impl.storage.jooq.tables.references.SYMBOLS
import org.utbot.jcdb.impl.storage.longHash
import org.utbot.jcdb.impl.storage.runBatch
import org.utbot.jcdb.impl.storage.setNullableLong
import org.utbot.jcdb.impl.vfs.LazyPersistentByteCodeLocation


class UsagesIndexer(private val location: RegisteredLocation) : ByteCodeIndexer {

    // callee_class -> (callee_name, callee_desc, opcode) -> caller
    private val usages = hashMapOf<String, HashMap<Triple<String, String?, Int>, HashMap<String, HashSet<Int>>>>()

    override fun index(classNode: ClassNode) {
        val callerClass = Type.getObjectType(classNode.name).className
        var callerMethodOffset = 0
        classNode.methods.forEach { methodNode ->
            methodNode.instructions.forEach {
                var key: Triple<String, String?, Int>? = null
                var callee: String? = null
                when (it) {
                    is FieldInsnNode -> {
                        callee = Type.getObjectType(it.owner).className
                        key = Triple(it.name, null, it.opcode)
                    }

                    is MethodInsnNode -> {
                        callee = Type.getObjectType(it.owner).className
                        key = Triple(it.name, it.desc, it.opcode)
                    }
                }
                if (key != null && callee != null) {
                    usages.getOrPut(callee) { hashMapOf() }
                        .getOrPut(key) { hashMapOf() }
                        .getOrPut(callerClass) { hashSetOf() }
                        .add(callerMethodOffset)
                }
            }
            callerMethodOffset++
        }
    }

    override fun index(classNode: ClassNode, methodNode: MethodNode) {
    }

    override fun flush(jooq: DSLContext) {
        jooq.connection { conn ->
            conn.runBatch(CALLS) {
                usages.forEach { (calleeClass, calleeEntry) ->
                    calleeEntry.forEach { (info, callers) ->
                        val (calleeName, calleeDesc, opcode) = info
                        callers.forEach { (caller, offsets) ->
                            setLong(1, calleeClass.longHash)
                            setString(2, calleeName)
                            setNullableLong(3, calleeDesc?.longHash)
                            setInt(4, opcode)
                            setLong(5, caller.longHash)
                            setString(6, offsets.joinToString(","))
                            setLong(7, location.id)
                            addBatch()
                        }
                    }
                }
            }
        }
    }
}


object Usages : JcFeature<UsageFeatureRequest, UsageFeatureResponse> {

    private val createScheme = """
        CREATE TABLE IF NOT EXISTS "Calls"(
            "callee_class_hash"      BIGINT NOT NULL,
            "callee_name"            VARCHAR(256),
            "callee_desc_hash"       BIGINT,
            "opcode"                 INTEGER,
            "caller_class_hash"      BIGINT NOT NULL,
            "caller_method_offsets"  VARCHAR(256),
            "location_id"            BIGINT NOT NULL
        );
        
        CREATE INDEX IF NOT EXISTS 'Calls search' ON Calls(location_id, opcode, callee_class_hash, callee_name, callee_desc_hash)
    """.trimIndent()

    private val dropScheme = """
        DROP TABLE IF EXISTS "Calls";
        DROP INDEX IF EXISTS "Calls search";
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

    override suspend fun query(jcdb: JCDB, req: UsageFeatureRequest): Sequence<UsageFeatureResponse> {
        val name = req.methodName ?: req.field
        val desc = req.methodDesc
        val className = req.className
        return BatchedSequence(50) {
            jcdb.persistence.read { jooq ->
                var position = it ?: 0
                val classHashes: Map<Long, List<Int>> =
                    jooq.select(CALLS.CALLER_CLASS_HASH, CALLS.CALLER_METHOD_OFFSETS).from(CALLS)
                        .where(
                            CALLS.CALLEE_CLASS_HASH.eq(className.longHash)
                                .and(CALLS.CALLEE_NAME.eq(name))
                                .and(CALLS.CALLEE_DESC_HASH.eqOrNull(desc?.longHash))
                                .and(CALLS.OPCODE.`in`(req.opcodes))
                        )
                        .orderBy(CALLS.LOCATION_ID)
                        .limit(50).offset(position)
                        .fetch()
                        .map { it.component1()!! to it.component2()!!.split(",").map { it.toInt() } }.toMap()
                jooq.select(SYMBOLS.NAME, CLASSES.BYTECODE, CLASSES.LOCATION_ID, SYMBOLS.HASH)
                    .from(CLASSES)
                    .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                    .where(SYMBOLS.HASH.`in`(classHashes.keys))
                    .fetch()
                    .mapNotNull { (className, byteCode, locationId, symbolHash) ->
                        position++ to
                                UsageFeatureResponse(
                                    source = ClassSourceImpl(
                                        LazyPersistentByteCodeLocation(jcdb.persistence, locationId!!),
                                        className!!, byteCode!!
                                    ),
                                    offsets = classHashes[symbolHash!!].orEmpty()
                                )
                    }
            }
        }

    }

    override fun newIndexer(jcdb: JCDB, location: RegisteredLocation) = UsagesIndexer(location)

}