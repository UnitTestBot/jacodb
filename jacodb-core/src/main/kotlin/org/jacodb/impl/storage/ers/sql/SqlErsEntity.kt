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

package org.jacodb.impl.storage.ers.sql

import org.jacodb.api.storage.ers.Entity
import org.jacodb.api.storage.ers.EntityId
import org.jacodb.api.storage.ers.EntityIterable
import org.jacodb.impl.storage.ers.sql.SqlErsNames.BLOB_VALUE_FIELD
import org.jacodb.impl.storage.ers.sql.SqlErsNames.ENTITY_ID_FIELD
import org.jacodb.impl.storage.ers.sql.SqlErsNames.LINK_SOURCE_ENTITY_ID_FIELD
import org.jacodb.impl.storage.ers.sql.SqlErsNames.LINK_TARGET_ENTITY_ID_FIELD
import org.jacodb.impl.storage.ers.sql.SqlErsNames.PROPERTY_VALUE_FIELD
import org.jooq.DSLContext

class SqlErsEntity(
    override val id: EntityId,
    override val txn: SqlErsTransaction,
) : Entity() {
    private val jooq: DSLContext get() = txn.jooq

    override fun getRawProperty(name: String): ByteArray? {
        val propertyTable = txn.getPropertyTableOrNull(id.typeId, name) ?: return null

        return jooq.select(PROPERTY_VALUE_FIELD)
            .from(propertyTable)
            .where(ENTITY_ID_FIELD.eq(id.instanceId))
            .fetchOne()
            ?.value1()
    }

    override fun setRawProperty(name: String, value: ByteArray?) {
        val propertyTable = txn.getOrCreatePropertyTable(id.typeId, name)

        if (value == null) {
            jooq.deleteFrom(propertyTable)
                .where(ENTITY_ID_FIELD.eq(id.instanceId))
                .execute()
            return
        }

        jooq.insertInto(propertyTable)
            .set(ENTITY_ID_FIELD, id.instanceId)
            .set(PROPERTY_VALUE_FIELD, value)
            .onDuplicateKeyUpdate()
            .set(PROPERTY_VALUE_FIELD, value)
            .execute()
    }

    override fun getRawBlob(name: String): ByteArray? {
        val blobTable = txn.getBlobTableOrNull(id.typeId, name) ?: return null

        return jooq.select(BLOB_VALUE_FIELD)
            .from(blobTable)
            .where(ENTITY_ID_FIELD.eq(id.instanceId))
            .fetchOne()
            ?.value1()
    }

    override fun setRawBlob(name: String, blob: ByteArray?) {
        val blobTable = txn.getOrCreateBlobTable(id.typeId, name)

        if (blob == null) {
            jooq.deleteFrom(blobTable)
                .where(ENTITY_ID_FIELD.eq(id.instanceId))
                .execute()
        } else {
            jooq.insertInto(blobTable)
                .set(ENTITY_ID_FIELD, id.instanceId)
                .set(BLOB_VALUE_FIELD, blob)
                .onDuplicateKeyUpdate()
                .set(BLOB_VALUE_FIELD, blob)
                .execute()
        }
    }

    override fun getLinks(name: String): EntityIterable {
        return txn.getLinkTables(id.typeId, name).fold(EntityIterable.EMPTY) { acc, (linkTable, linkTableMetaData) ->
            acc + SqlErsEntityIterable(
                condition = linkTable.field(LINK_SOURCE_ENTITY_ID_FIELD)!!.eq(id.instanceId),
                fromTables = listOf(linkTable),
                entityIdField = linkTable.field(LINK_TARGET_ENTITY_ID_FIELD)!!,
                typeId = linkTableMetaData.targetTypeId,
                txn = txn
            )
        }
    }

    override fun addLink(name: String, targetId: EntityId): Boolean {
        val linkTable = txn.getOrCreateLinkTable(
            LinkTableMetaData(
                sourceTypeId = id.typeId,
                targetTypeId = targetId.typeId,
                linkName = name,
            )
        )

        jooq.select().from(txn.getEntityTableByTypeIdOrNull(id.typeId)!!).fetch()

        return jooq.insertInto(linkTable)
            .set(LINK_SOURCE_ENTITY_ID_FIELD, id.instanceId)
            .set(LINK_TARGET_ENTITY_ID_FIELD, targetId.instanceId)
            .onDuplicateKeyIgnore()
            .execute() > 0
    }

    override fun deleteLink(name: String, targetId: EntityId): Boolean {
        val linkTable = txn.getOrCreateLinkTable(
            LinkTableMetaData(
                sourceTypeId = id.typeId,
                targetTypeId = targetId.typeId,
                linkName = name
            )
        )
        return jooq.deleteFrom(linkTable)
            .where(
                LINK_SOURCE_ENTITY_ID_FIELD.eq(id.instanceId)
                    .and(LINK_TARGET_ENTITY_ID_FIELD.eq(targetId.instanceId))
            )
            .execute() > 0
    }
}
