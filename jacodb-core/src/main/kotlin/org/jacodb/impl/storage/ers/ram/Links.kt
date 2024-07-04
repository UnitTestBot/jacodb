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

import jetbrains.exodus.core.dataStructures.persistent.PersistentLong23TreeMap
import jetbrains.exodus.core.dataStructures.persistent.PersistentLongMap
import org.jacodb.api.jvm.storage.ers.EntityId
import org.jacodb.api.jvm.storage.ers.EntityIdCollectionEntityIterable
import org.jacodb.api.jvm.storage.ers.EntityIterable

internal class Links(private val links: PersistentLongMap<EntityIdSet> = PersistentLong23TreeMap()) {

    internal fun getLinks(txn: RAMTransaction, instanceId: Long): EntityIterable {
        return EntityIdCollectionEntityIterable(txn, (links[instanceId]?.toList() ?: return EntityIterable.EMPTY))
    }

    internal fun addLink(instanceId: Long, targetId: EntityId): Links {
        val idSet = links[instanceId] ?: EntityIdSet()
        val newIdSet = idSet.add(targetId)
        return if (idSet === newIdSet) this else Links(links.write { put(instanceId, newIdSet) }.second)
    }

    fun deleteLink(instanceId: Long, targetId: EntityId): Links {
        val idSet = links[instanceId] ?: return this
        val newIdSet = idSet.remove(targetId)
        return if (idSet === newIdSet) {
            this
        } else {
            Links(
                links.write {
                    if (newIdSet.isEmpty) {
                        remove(instanceId)
                    } else {
                        put(instanceId, newIdSet)
                    }
                }.second
            )
        }
    }
}