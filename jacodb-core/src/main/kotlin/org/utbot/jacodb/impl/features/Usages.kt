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

package org.utbot.jacodb.impl.features

import org.jooq.DSLContext
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.utbot.jacodb.api.ByteCodeIndexer
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcDatabase
import org.utbot.jacodb.api.JcDatabasePersistence
import org.utbot.jacodb.api.JcFeature
import org.utbot.jacodb.api.JcSignal
import org.utbot.jacodb.api.RegisteredLocation
import org.utbot.jacodb.impl.fs.PersistenceClassSource
import org.utbot.jacodb.impl.fs.className
import org.utbot.jacodb.impl.storage.BatchedSequence
import org.utbot.jacodb.impl.storage.eqOrNull
import org.utbot.jacodb.impl.storage.executeQueries
import org.utbot.jacodb.impl.storage.jooq.tables.references.CALLS
import org.utbot.jacodb.impl.storage.jooq.tables.references.CLASSES
import org.utbot.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.utbot.jacodb.impl.storage.longHash
import org.utbot.jacodb.impl.storage.runBatch
import org.utbot.jacodb.impl.storage.setNullableLong
import org.utbot.jacodb.impl.storage.withoutAutocommit


private class MethodMap(size: Int) {

    private val ticks = BooleanArray(size)
    private val array = ShortArray(size)
    private var position = 0

    fun tick(index: Int) {
        if (!ticks[index]) {
            array[position] = index.toShort()
            ticks[index] = true
            position++
        }
    }

    fun result(): ByteArray {
        return array.sliceArray(0 until position).toByteArray()
    }

    private fun ShortArray.toByteArray(): ByteArray {
        var short_index: Int
        val iterations = size
        val buffer = ByteArray(size * 2)
        var byte_index: Int = 0
        short_index = byte_index
        while ( /*NOP*/short_index != iterations /*NOP*/) {
            buffer[byte_index] = (this[short_index].toInt() and 0x00FF).toByte()
            buffer[byte_index + 1] = (this[short_index].toInt() and 0xFF00 shr 8).toByte()
            ++short_index
            byte_index += 2
        }
        return buffer
    }
}

class UsagesIndexer(persistence: JcDatabasePersistence, private val location: RegisteredLocation) :
    ByteCodeIndexer {

    // callee_class -> (callee_name, callee_desc, opcode) -> caller
    private val usages = hashMapOf<String, HashMap<Triple<String, String?, Int>, HashMap<String, MethodMap>>>()
    private val interner = persistence.newSymbolInterner()

    override fun index(classNode: ClassNode) {
        val callerClass = Type.getObjectType(classNode.name).className
        val size = classNode.methods.size
        classNode.methods.forEachIndexed { index, methodNode ->
            methodNode.instructions.forEach {
                var key: Triple<String, String?, Int>? = null
                var callee: String? = null
                when (it) {
                    is FieldInsnNode -> {
                        callee = it.owner
                        key = Triple(it.name, null, it.opcode)
                    }

                    is MethodInsnNode -> {
                        callee = it.owner
                        key = Triple(it.name, it.desc, it.opcode)
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

    override fun flush(jooq: DSLContext) {
        jooq.connection { conn ->
            conn.withoutAutocommit {
                conn.runBatch(CALLS) {
                    usages.forEach { (calleeClass, calleeEntry) ->
                        val calleeId = calleeClass.className.symbolId
                        calleeEntry.forEach { (info, callers) ->
                            val (calleeName, calleeDesc, opcode) = info
                            callers.forEach { (caller, offsets) ->
                                val callerId = if (calleeClass == caller) calleeId else caller.symbolId
                                setLong(1, calleeId)
                                setLong(2, calleeName.symbolId)
                                setNullableLong(3, calleeDesc?.longHash)
                                setInt(4, opcode)
                                setLong(5, callerId)
                                setBytes(6, offsets.result())
                                setLong(7, location.id)
                                addBatch()
                            }
                        }
                    }
                    interner.flush(conn)
                }
            }
        }
    }

    private inline val String.symbolId get() = interner.findOrNew(this)
}


object Usages : JcFeature<UsageFeatureRequest, UsageFeatureResponse> {

    override fun onSignal(signal: JcSignal) {
        val jcdb = signal.jcdb
        when (signal) {
            is JcSignal.BeforeIndexing -> {
                jcdb.persistence.write {
                    if (signal.clearOnStart) {
                        it.executeQueries(jcdb.persistence.getScript("usages/drop-schema.sql"))
                    }
                    it.executeQueries(jcdb.persistence.getScript("usages/create-schema.sql"))
                }
            }

            is JcSignal.LocationRemoved -> {
                jcdb.persistence.write {
                    it.deleteFrom(CALLS).where(CALLS.LOCATION_ID.eq(signal.location.id)).execute()
                }
            }

            is JcSignal.AfterIndexing -> {
                jcdb.persistence.write {
                    it.executeQueries(jcdb.persistence.getScript("usages/add-indexes.sql"))
                }
            }

            is JcSignal.Drop -> {
                jcdb.persistence.write {
                    it.deleteFrom(CALLS).execute()
                }
            }

            else -> Unit
        }
    }

    override suspend fun query(classpath: JcClasspath, req: UsageFeatureRequest): Sequence<UsageFeatureResponse> {
        return syncQuery(classpath, req)
    }

    fun syncQuery(classpath: JcClasspath, req: UsageFeatureRequest): Sequence<UsageFeatureResponse> {
        val locationIds = classpath.registeredLocations.map { it.id }
        val persistence = classpath.db.persistence
        val name = (req.methodName ?: req.field).let { persistence.findSymbolId(it!!) }
        val desc = req.description?.longHash
        val className = req.className.map { persistence.findSymbolId(it) }

        val calls = persistence.read { jooq ->
            jooq.select(CLASSES.ID, CALLS.CALLER_METHOD_OFFSETS, SYMBOLS.NAME, CLASSES.LOCATION_ID)
                .from(CALLS)
                .join(SYMBOLS).on(SYMBOLS.ID.eq(CLASSES.NAME))
                .join(CLASSES).on(CLASSES.NAME.eq(CALLS.CALLER_CLASS_SYMBOL_ID))
                .where(
                    CALLS.CALLEE_CLASS_SYMBOL_ID.`in`(className)
                        .and(CALLS.CALLEE_NAME_SYMBOL_ID.eq(name))
                        .and(CALLS.CALLEE_DESC_HASH.eqOrNull(desc))
                        .and(CALLS.OPCODE.`in`(req.opcodes))
                        .and(CALLS.LOCATION_ID.`in`(locationIds))
                ).fetch().mapNotNull { (classId, offset, className, locationId) ->
                    PersistenceClassSource(
                        classpath,
                        className!!,
                        classId = classId!!,
                        locationId = locationId!!
                    ) to offset!!.toShortArray()
                }
        }

        return BatchedSequence(50) { offset, batchSize ->
            var position = offset ?: 0
            val classes = calls.drop(position.toInt()).take(batchSize)
            val classIds = classes.map { it.first.classId }.toSet()
            val byteCodes = persistence.read { jooq ->
                jooq.select(CLASSES.ID, CLASSES.BYTECODE).from(CLASSES)
                    .where(CLASSES.ID.`in`(classIds))
                    .fetch()
                    .map { (classId, byteArray) ->
                        classId!! to byteArray!!
                    }.toMap()
            }
            classes.map { (source, offsets) ->
                position++ to UsageFeatureResponse(
                    source = source.bind(byteCodes[source.classId]),
                    offsets = offsets
                )
            }
        }
    }

    override fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation) = UsagesIndexer(jcdb.persistence, location)


    private fun ByteArray.toShortArray(): ShortArray {
        val byteArray = this
        val shortArray = ShortArray(byteArray.size / 2) {
            (byteArray[it * 2].toUByte().toInt() + (byteArray[(it * 2) + 1].toInt() shl 8)).toShort()
        }
        return shortArray // [211, 24]
    }
}