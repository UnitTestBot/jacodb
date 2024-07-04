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

package org.jacodb.impl.storage.ers.kv

import jetbrains.exodus.core.dataStructures.ConcurrentObjectCache
import jetbrains.exodus.core.dataStructures.ObjectCacheBase
import jetbrains.exodus.core.dataStructures.hash.IntHashMap
import org.jacodb.api.jvm.storage.ers.Binding
import org.jacodb.api.jvm.storage.ers.ERSConflictingTransactionException
import org.jacodb.api.jvm.storage.ers.Entity
import org.jacodb.api.jvm.storage.ers.EntityId
import org.jacodb.api.jvm.storage.ers.EntityIterable
import org.jacodb.api.jvm.storage.ers.InstanceIdCollectionEntityIterable
import org.jacodb.api.jvm.storage.ers.Transaction
import org.jacodb.api.jvm.storage.ers.probablyCompressed
import org.jacodb.api.jvm.storage.kv.Cursor
import org.jacodb.api.jvm.storage.kv.NamedMap
import org.jacodb.api.jvm.storage.kv.asIterable
import org.jacodb.api.jvm.storage.kv.asIterableWithKey
import org.jacodb.impl.storage.kv.xodus.getOrPut

class KVErsTransaction(
    override val ers: KVEntityRelationshipStorage,
    internal val kvTxn: org.jacodb.api.jvm.storage.kv.Transaction
) : Transaction {

    private val isDeletedCache: ObjectCacheBase<EntityId, Boolean> = ConcurrentObjectCache(1_000, 2)
    private val linkTargetTypes = hashMapOf<TypeIdWithName, Int>() // typeId + linkName -> targetTypeId
    private val entityCounters = hashMapOf<Int, Long>() // typeId -> entityCounter
    private val dirtyEntityCounters = sortedMapOf<Int, Long>() // typeId -> entityCounter (modified within transaction)
    private val deletedEntitiesCounts = IntHashMap<Long>() // typeId -> number of deleted entities

    override val isReadonly: Boolean get() = kvTxn.isReadonly

    override val isFinished: Boolean get() = kvTxn.isFinished

    override fun newEntity(type: String): Entity {
        val typeId = ers.getOrAllocateEntityTypeId(type, kvTxn)
        val entityCounter = getEntityCounter(typeId) ?: 0L
        entityCounters[typeId] = entityCounter + 1
        dirtyEntityCounters[typeId] = entityCounter + 1
        return KVEntity(EntityId(typeId, entityCounter), this)
    }

    override fun getEntityOrNull(id: EntityId): Entity? {
        val entityCounter = getEntityCounter(id.typeId) ?: return null
        return if (id.instanceId in 0 until entityCounter && !isEntityDeleted(id)) KVEntity(id, this) else null
    }

    override fun deleteEntity(id: EntityId) {
        // invalidate isDeleted cache
        isDeletedCache.cacheObject(id, true)
        val typeId = id.typeId
        deletedEntitiesCounts[typeId] = getDeletedEntitiesCount(typeId) + 1L
        kvTxn.put(
            ers.deletedEntitiesMap(typeId, kvTxn),
            ers.longBinding.getBytesCompressed(id.instanceId),
            byteArrayOf(0)
        )
    }

    override fun isEntityDeleted(id: EntityId): Boolean {
        return getDeletedEntitiesCount(id.typeId) > 0L && isDeletedCache.getOrPut(id) {
            isDeleted(ers.deletedEntitiesMap(id.typeId, kvTxn), ers.longBinding, id.instanceId)
        }
    }

    override fun getTypeId(type: String): Int {
        return ers.getEntityTypeId(type, kvTxn) ?: -1
    }

    override fun all(type: String): EntityIterable {
        val typeId = ers.getEntityTypeId(type, kvTxn) ?: return EntityIterable.EMPTY
        val entityCounter = getEntityCounter(typeId) ?: return EntityIterable.EMPTY
        if (getDeletedEntitiesCount(typeId) == 0L) {
            return InstanceIdCollectionEntityIterable(this, typeId, (0 until entityCounter).toList())
        }
        val deletedMap = ers.deletedEntitiesMap(typeId, kvTxn)
        return InstanceIdCollectionEntityIterable(this, typeId,
            buildList {
                (0 until entityCounter).forEach { instanceId ->
                    if (!isDeleted(deletedMap, ers.longBinding, instanceId)) {
                        add(instanceId)
                    }
                }
            }
        )
    }

    override fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) { valueEntry ->
            asIterableWithKey(valueEntry)
        }
    }

    override fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) { valueEntry ->
            asReversedIterable(valueEntry).asSequence().filter { (key, _) -> !key.contentEquals(valueEntry) }
                .asIterable()
        }
    }

    override fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) { valueEntry ->
            asReversedIterable(valueEntry)
        }
    }

    override fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) { valueEntry ->
            asIterable().asSequence().filter { (key, _) -> !key.contentEquals(valueEntry) }.asIterable()
        }
    }

    override fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable {
        return genericFind(type, propertyName, value) {
            asIterable()
        }
    }

    override fun dropAll() {
        TODO("Not yet implemented")
    }

    override fun commit() {
        if (!isFinished) {
            flushDirty()
            if (!kvTxn.commit()) {
                throw ERSConflictingTransactionException()
            }
            ers.transactionFinished(this)
        }
    }

    override fun abort() {
        if (!isFinished) {
            kvTxn.abort()
            ers.transactionFinished(this)
        }
    }

    private fun isDeleted(deletedMap: NamedMap, longBinding: Binding<Long>, instanceId: Long): Boolean {
        return isDeleted(deletedMap, longBinding.getBytesCompressed(instanceId))
    }

    internal fun isDeleted(deletedMap: NamedMap, instanceIdEntry: ByteArray): Boolean {
        return kvTxn.get(deletedMap, instanceIdEntry) != null
    }

    internal fun getLinkTargetType(typeId: Int, linkName: String): Int? {
        return linkTargetTypes.getOrElse(typeId with linkName) {
            val nameEntry = ers.stringBinding.getBytes(linkName)
            kvTxn.get(ers.linkTargetTypesMap(typeId, kvTxn), nameEntry)?.let { typeIdEntry ->
                ers.intBinding.getObjectCompressed(typeIdEntry)
            }?.also {
                linkTargetTypes[typeId with linkName] = it
            }
        }
    }

    private fun <T : Any> genericFind(
        type: String,
        propertyName: String,
        value: T,
        cursorFun: Cursor.(valueEntry: ByteArray) -> Iterable<Pair<ByteArray, ByteArray>>
    ): EntityIterable {
        val typeId = getTypeId(type)
        if (typeId < 0) {
            return EntityIterable.EMPTY
        }
        val valueEntry = probablyCompressed(value)
        return if (getDeletedEntitiesCount(typeId) == 0L) {
            InstanceIdCollectionEntityIterable(this, typeId,
                buildList {
                    kvTxn.navigateTo(ers.propertiesIndex(typeId, propertyName, kvTxn), valueEntry).use { cursor ->
                        cursor.cursorFun(valueEntry).forEach { (_, instanceIdEntry) ->
                            add(ers.longBinding.getObjectCompressed(instanceIdEntry))
                        }
                    }
                }
            )
        } else {
            val deletedMap = ers.deletedEntitiesMap(typeId, kvTxn)
            InstanceIdCollectionEntityIterable(this, typeId,
                buildList {
                    kvTxn.navigateTo(ers.propertiesIndex(typeId, propertyName, kvTxn), valueEntry).use { cursor ->
                        cursor.cursorFun(valueEntry).forEach { (_, instanceIdEntry) ->
                            if (!isDeleted(deletedMap, instanceIdEntry)) {
                                add(ers.longBinding.getObjectCompressed(instanceIdEntry))
                            }
                        }
                    }
                }
            )
        }
    }

    private fun getEntityCounter(typeId: Int): Long? {
        return entityCounters.getOrElse(typeId) {
            kvTxn.get(ers.entityCountersMap(kvTxn), ers.intBinding.getBytesCompressed(typeId))
                ?.let { entityCounterEntry ->
                    ers.longBinding.getObjectCompressed(entityCounterEntry)
                }?.also {
                    entityCounters[typeId] = it
                }
        }
    }

    private fun getDeletedEntitiesCount(typeId: Int): Long {
        return deletedEntitiesCounts.getOrPut(typeId) {
            ers.deletedEntitiesMap(typeId, kvTxn).size(kvTxn)
        }
    }

    private fun flushDirty() {
        val entityCountersMap = this.ers.entityCountersMap(kvTxn)
        dirtyEntityCounters.forEach { (typeId, entityCounter) ->
            kvTxn.put(
                entityCountersMap,
                ers.intBinding.getBytesCompressed(typeId),
                ers.longBinding.getBytesCompressed(entityCounter)
            )
        }
        dirtyEntityCounters.clear()
        // clear caches
        isDeletedCache.clear()
        linkTargetTypes.clear()
        entityCounters.clear()
    }
}