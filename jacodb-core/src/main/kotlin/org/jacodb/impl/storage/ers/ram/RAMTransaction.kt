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

package org.jacodb.impl.storage.ers.ram

import org.jacodb.api.jvm.storage.ers.ERSConflictingTransactionException
import org.jacodb.api.jvm.storage.ers.ERSException
import org.jacodb.api.jvm.storage.ers.Entity
import org.jacodb.api.jvm.storage.ers.EntityId
import org.jacodb.api.jvm.storage.ers.EntityIterable
import org.jacodb.api.jvm.storage.ers.Transaction
import org.jacodb.api.jvm.storage.ers.probablyCompressed

internal class RAMTransaction(override val ers: RAMEntityRelationshipStorage) : Transaction {

    private var originContainer = ers.dataContainer
    private var dataContainer: RAMPersistentDataContainer? = originContainer

    override val isReadonly = false

    override val isFinished: Boolean
        get() = dataContainer == null

    override fun newEntity(type: String): Entity {
        val typeId = getOrAllocateTypeId(type)
        val instanceId = allocateInstanceId(typeId)
        return RAMEntity(this, EntityId(typeId, instanceId))
    }

    override fun getEntityOrNull(id: EntityId): Entity? =
        if (dataContainerChecked.entityExists(id)) RAMEntity(this, id) else null

    override fun deleteEntity(id: EntityId) {
        dataContainer = dataContainerChecked.deleteEntity(id)
    }

    override fun getTypeId(type: String): Int = dataContainerChecked.getTypeId(type)

    override fun all(type: String): EntityIterable = dataContainerChecked.all(this, type)

    override fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesWithPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesLtPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesEqOrLtPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesGtPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable {
        val (newDataContainer, result) = dataContainerChecked.getEntitiesEqOrGtPropertyValue(
            this, type, propertyName, probablyCompressed(value)
        )
        newDataContainer?.let { dataContainer = it }
        return result
    }

    override fun dropAll() {
        dataContainer = RAMPersistentDataContainer()
    }

    override fun commit() {
        val originContainer = originContainer
        val resultContainer = dataContainer?.commit()
        // if transaction wasn't read-only
        if (resultContainer != null && originContainer !== resultContainer) {
            if (!ers.compareAndSetDataContainer(originContainer, resultContainer)) {
                throw ERSConflictingTransactionException(
                    "Cannot commit transaction since a parallel one has been committed in between"
                )
            }
        }
        dataContainer = null
    }

    override fun abort() {
        dataContainer = null
    }

    internal fun getRawProperty(id: EntityId, name: String): ByteArray? = dataContainerChecked.getRawProperty(id, name)

    internal fun setRawProperty(id: EntityId, name: String, value: ByteArray?) {
        dataContainer = dataContainerChecked.setRawProperty(id, name, value)
    }

    internal fun getBlob(id: EntityId, name: String): ByteArray? {
        return dataContainerChecked.getBlob(id, name)
    }

    internal fun setBlob(id: EntityId, name: String, value: ByteArray?) {
        dataContainer = dataContainerChecked.setBlob(id, name, value)
    }

    internal fun getLinks(id: EntityId, name: String): EntityIterable {
        return dataContainerChecked.getLinks(this, id, name)
    }

    internal fun addLink(id: EntityId, name: String, targetId: EntityId): Boolean {
        dataContainerChecked.let { dataContainer ->
            val newDataContainer = dataContainer.addLink(id, name, targetId)
            return (newDataContainer !== dataContainer).also { this.dataContainer = newDataContainer }
        }
    }

    internal fun deleteLink(id: EntityId, name: String, targetId: EntityId): Boolean {
        dataContainerChecked.let { dataContainer ->
            val newDataContainer = dataContainer.deleteLink(id, name, targetId)
            return (newDataContainer !== dataContainer).also { this.dataContainer = newDataContainer }
        }
    }

    private val dataContainerChecked: RAMPersistentDataContainer
        get() = dataContainer ?: throw ERSException("Transaction has been already finished")

    private fun getOrAllocateTypeId(type: String): Int {
        val (newContainer, typeId) = dataContainerChecked.getOrAllocateTypeId(type)
        dataContainer = newContainer
        return typeId
    }

    private fun allocateInstanceId(typeId: Int): Long {
        val (newContainer, instanceId) = dataContainerChecked.allocateInstanceId(typeId)
        dataContainer = newContainer
        return instanceId
    }
}