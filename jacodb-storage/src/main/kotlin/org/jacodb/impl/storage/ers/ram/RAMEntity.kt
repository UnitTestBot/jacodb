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

import org.jacodb.api.storage.ers.Entity
import org.jacodb.api.storage.ers.EntityId
import org.jacodb.api.storage.ers.EntityIterable

internal class RAMEntity(override val txn: RAMTransaction, override val id: EntityId) : Entity() {

    override fun getRawProperty(name: String): ByteArray? = txn.getRawProperty(id, name)

    override fun setRawProperty(name: String, value: ByteArray?) {
        txn.setRawProperty(id, name, value)
    }

    override fun getRawBlob(name: String): ByteArray? = txn.getBlob(id, name)

    override fun setRawBlob(name: String, blob: ByteArray?) {
        txn.setBlob(id, name, blob)
    }

    override fun getLinks(name: String): EntityIterable {
        return txn.getLinks(id, name)
    }

    override fun addLink(name: String, targetId: EntityId): Boolean {
        return txn.addLink(id, name, targetId)
    }

    override fun deleteLink(name: String, targetId: EntityId): Boolean {
        return txn.deleteLink(id, name, targetId)
    }
}