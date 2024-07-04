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

import org.jacodb.api.jvm.storage.ers.EntityId

internal class EntityIdSet(
    private var typeId: Int = -1,
    private val instances: CompactPersistentLongSet = CompactPersistentLongSet()
) {

    val size: Int get() = instances.size

    val isEmpty: Boolean get() = instances.isEmpty()

    fun toList(): List<EntityId> {
        return instances.map { EntityId(typeId, it) }
    }

    fun add(id: EntityId): EntityIdSet {
        val typeId = checkTypeId(id)
        val newInstances = instances.add(id.instanceId)
        return if (newInstances === instances) this else EntityIdSet(typeId, newInstances)
    }

    fun remove(id: EntityId): EntityIdSet {
        val typeId = checkTypeId(id)
        val newInstances = instances.remove(id.instanceId)
        return if (newInstances === instances) this else EntityIdSet(typeId, newInstances)
    }

    private fun checkTypeId(id: EntityId): Int {
        val typeId = id.typeId
        if (this.typeId != -1 && this.typeId != typeId) {
            throw IllegalStateException("EntityIdSet can only store ids of the same typeId")
        }
        return typeId
    }
}