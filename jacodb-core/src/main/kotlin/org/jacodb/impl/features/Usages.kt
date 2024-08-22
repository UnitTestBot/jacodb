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

package org.jacodb.impl.features

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.storage.ers.compressed
import org.jacodb.api.jvm.storage.ers.links
import org.jacodb.api.jvm.storage.ers.nonSearchable
import org.jacodb.impl.asSymbolId
import org.jacodb.impl.fs.PersistenceClassSource
import org.jacodb.impl.fs.className
import org.jacodb.impl.storage.BatchedSequence
import org.jacodb.impl.storage.defaultBatchSize
import org.jacodb.impl.storage.dslContext
import org.jacodb.impl.storage.eqOrNull
import org.jacodb.impl.storage.execute
import org.jacodb.impl.storage.executeQueries
import org.jacodb.impl.storage.isSqlContext
import org.jacodb.impl.storage.jooq.tables.references.CALLS
import org.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jacodb.impl.storage.longHash
import org.jacodb.impl.storage.runBatch
import org.jacodb.impl.storage.setNullableLong
import org.jacodb.impl.storage.sqlScript
import org.jacodb.impl.storage.withoutAutoCommit
import org.jacodb.impl.util.interned
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.experimental.or


private class MethodMap(size: Int) {

    private val ticks = ByteArray((size - 1) / 8 + 1)
    private val array = ShortArray(size)
    private var position = 0

    fun tick(index: Int) {
        val arrayIndex = index shr 3
        if ((ticks[arrayIndex] and (1 shl (index and 7)).toByte()) == 0.toByte()) {
            array[position] = index.toShort()
            ticks[arrayIndex] = ticks[arrayIndex] or (1 shl (index and 7)).toByte()
            position++
        }
    }

    fun result(): ByteArray {
        val pos = position
        return ByteBuffer.allocate(pos * 2).also { it.asShortBuffer().put(array, 0, pos) }.array()
    }
}

class UsagesIndexer(private val jcdb: JcDatabase, private val location: RegisteredLocation) : ByteCodeIndexer {

    // callee_class -> (callee_name, callee_desc, opcode) -> caller
    private val usages = hashMapOf<String, HashMap<Triple<String, String?, Int>, HashMap<String, MethodMap>>>()

    override fun index(classNode: ClassNode) {
        val callerClass = Type.getObjectType(classNode.name).className
        val size = classNode.methods.size
        classNode.methods.forEachIndexed { index, methodNode ->
            methodNode.instructions.forEach {
                var key: Triple<String, String?, Int>? = null
                var callee: String? = null
                when (it) {
                    is FieldInsnNode -> {
                        callee = it.owner.interned
                        key = Triple(it.name.interned, null, it.opcode)
                    }

                    is MethodInsnNode -> {
                        callee = it.owner.interned
                        key = Triple(it.name.interned, it.desc.interned, it.opcode)
                    }
                }
                if (key != null && callee != null) {
                    usages.getOrPut(callee) { hashMapOf() }
                        .getOrPut(key) { hashMapOf() }
                        .getOrPut(callerClass) { MethodMap(size) }.tick(index)
                }
            }
        }
    }

    override fun flush(context: JCDBContext) {
        context.execute(
            sqlAction = { jooq ->
                jooq.withoutAutoCommit { conn ->
                    conn.runBatch(CALLS) {
                        usages.forEach { (calleeClass, calleeEntry) ->
                            val calleeId = calleeClass.className
                            calleeEntry.forEach { (info, callers) ->
                                val (calleeName, calleeDesc, opcode) = info
                                callers.forEach { (caller, offsets) ->
                                    setString(1, calleeId)
                                    setString(2, calleeName)
                                    setNullableLong(3, calleeDesc?.longHash)
                                    setInt(4, opcode)
                                    setString(5, caller)
                                    setBytes(6, offsets.result())
                                    setLong(7, location.id)
                                    addBatch()
                                }
                            }
                        }
                    }
                }
            },
            noSqlAction = { txn ->
                val locationValue = location.id.compressed
                val symbolInterner = jcdb.persistence.symbolInterner
                usages.forEach { (calleeClass, calleeEntry) ->
                    val calleeClassId = calleeClass.className.asSymbolId(symbolInterner).compressed.nonSearchable
                    calleeEntry.forEach { (info, callers) ->
                        val (calleeName, calleeDesc, opcode) = info
                        val calleeNameId = calleeName.asSymbolId(symbolInterner).compressed
                        val calleeDescValue = calleeDesc?.longHash?.nonSearchable
                        val opcodeValue = opcode.compressed.nonSearchable
                        val callee = txn.newEntity("Callee").also { callee ->
                            callee["locationId"] = locationValue
                            callee["calleeClassId"] = calleeClassId
                            callee["calleeNameId"] = calleeNameId
                            if (calleeDescValue != null) {
                                callee["calleeDesc"] = calleeDescValue
                            }
                            callee["opcode"] = opcodeValue
                        }
                        val calls = links(callee, "calls")
                        callers.forEach { (callerClass, offsets) ->
                            txn.newEntity("Call").also { call ->
                                calls += call
                                call["callerId"] =
                                    callerClass.className.asSymbolId(symbolInterner).compressed.nonSearchable
                                call.setRawBlob("offsets", offsets.result())
                            }
                        }
                    }
                }
            }
        )
    }
}

object Usages : JcFeature<UsageFeatureRequest, UsageFeatureResponse> {

    fun create(context: JCDBContext, drop: Boolean) {
        if (context.isSqlContext) {
            val jooq = context.dslContext
            if (drop) {
                jooq.executeQueries("usages/drop-schema.sql".sqlScript())
            }
            jooq.executeQueries("usages/create-schema.sql".sqlScript())
        }
    }

    override fun onSignal(signal: JcSignal) {
        val jcdb = signal.jcdb
        when (signal) {
            is JcSignal.BeforeIndexing -> jcdb.persistence.write { context ->
                create(context, signal.clearOnStart)
            }

            is JcSignal.LocationRemoved -> jcdb.persistence.write { context ->
                removeLocation(context, signal.location.id)
            }

            is JcSignal.AfterIndexing -> jcdb.persistence.write { context ->
                if (context.isSqlContext) {
                    context.dslContext.executeQueries("usages/add-indexes.sql".sqlScript())
                }
            }

            is JcSignal.Drop -> jcdb.persistence.write { context -> drop(context) }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JcClasspath, req: UsageFeatureRequest): Sequence<UsageFeatureResponse> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JcClasspath, req: UsageFeatureRequest): Sequence<UsageFeatureResponse> {
        val locationIds = classpath.registeredLocationIds
        val persistence = classpath.db.persistence
        val symbolInterner = persistence.symbolInterner
        val name = req.methodName ?: req.field ?: throw IllegalArgumentException("Callee name should be specified")
        val desc = req.description?.longHash
        val classNames = req.className

        return persistence.read { context ->
            context.execute(
                sqlAction = { jooq ->
                    val calls = jooq.select(CLASSES.ID, CALLS.CALLER_METHOD_OFFSETS, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                        .from(CALLS)
                        .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                        .join(CLASSES)
                        .on(SYMBOLS.NAME.eq(CALLS.CALLER_CLASS_NAME).and(CLASSES.LOCATION_ID.eq(CALLS.LOCATION_ID)))
                        .where(
                            CALLS.CALLEE_CLASS_NAME.`in`(classNames)
                                .and(CALLS.CALLEE_NAME.eq(name))
                                .and(CALLS.CALLEE_DESC_HASH.eqOrNull(desc))
                                .and(CALLS.OPCODE.`in`(req.opcodes))
                                .and(CALLS.LOCATION_ID.`in`(locationIds))
                        ).fetch().mapNotNull { (classId, offset, className, locationId) ->
                            PersistenceClassSource(
                                classpath.db,
                                className!!,
                                classId = classId!!,
                                locationId = locationId!!
                            ) to offset!!.toShortArray()
                        }
                    if (calls.isEmpty()) {
                        emptySequence()
                    } else {
                        BatchedSequence(defaultBatchSize) { offset, batchSize ->
                            var position = offset ?: 0
                            val classes = calls.drop(position.toInt()).take(batchSize)
                            val classIds = classes.map { it.first.classId }.toSet()
                            val byteCodes = jooq.select(CLASSES.ID, CLASSES.BYTECODE).from(CLASSES)
                                .where(CLASSES.ID.`in`(classIds))
                                .fetch()
                                .map { (classId, byteArray) ->
                                    classId!! to byteArray!!
                                }.toMap()
                            classes.map { (source, offsets) ->
                                position++ to UsageFeatureResponse(
                                    source = source.bind(byteCodes[source.classId]),
                                    offsets = offsets
                                )
                            }
                        }
                    }
                },
                noSqlAction = { txn ->
                    val classNameIds =
                        classNames.mapTo(mutableSetOf()) { className -> className.asSymbolId(symbolInterner) }
                    txn.find("Callee", "calleeNameId", name.asSymbolId(symbolInterner).compressed)
                        .filter { callee ->
                            callee.getCompressedBlob<Long>("calleeClassId") in classNameIds &&
                                    callee.getCompressed<Long>("locationId")!! in locationIds &&
                                    callee.getCompressedBlob<Int>("opcode") in req.opcodes &&
                                    callee.getBlob<Long>("calleeDesc").let { it == null || it == desc }
                        }
                        .flatMap { callee ->
                            val locationId = callee.getCompressed<Long>("locationId")!!
                            callee.getLinks("calls").map { it to locationId }
                        }
                        .map { (call, locationId) ->
                            val callerId = call.getCompressedBlob<Long>("callerId")!!
                            val caller = symbolInterner.findSymbolName(callerId)!!
                            val classId = txn.find("Class", "nameId", callerId.compressed).first().id.instanceId
                            UsageFeatureResponse(
                                source = PersistenceClassSource(
                                    db = classpath.db,
                                    className = caller,
                                    classId = classId,
                                    locationId = locationId,
                                    cachedByteCode = persistence.findBytecode(classId)
                                ),
                                offsets = call.getRawBlob("offsets")!!.toShortArray()
                            )
                        }.toList().asSequence()
                }
            )
        }
    }

    override fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation) = UsagesIndexer(jcdb, location)

    private fun drop(context: JCDBContext) {
        context.execute(
            sqlAction = { jooq ->
                jooq.deleteFrom(CALLS).execute()
            },
            noSqlAction = { txn ->
                txn.all("Callee").deleteAll()
                txn.all("Call").deleteAll()
            }
        )
    }

    private fun removeLocation(context: JCDBContext, locationId: Long) {
        context.execute(
            sqlAction = { jooq ->
                jooq.deleteFrom(CALLS).where(CALLS.LOCATION_ID.eq(locationId)).execute()
            },
            noSqlAction = { txn ->
                txn.find("Callee", "locationId", locationId.compressed).forEach { callee ->
                    callee.getLinks("calls").deleteAll()
                    callee.delete()
                }
            }
        )
    }

    private fun ByteArray.toShortArray(): ShortArray {
        return ShortArray(size / 2).also {
            ByteBuffer.wrap(this).asShortBuffer().get(it)
        }
    }
}
