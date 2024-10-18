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
import org.jacodb.api.storage.ers.Transaction
import org.jacodb.api.storage.ers.probablyCompressed
import org.jacodb.impl.storage.ers.sql.SqlErsNames.ENTITY_ID_FIELD
import org.jacodb.impl.storage.ers.sql.SqlErsNames.PROPERTY_VALUE_FIELD
import org.jacodb.impl.storage.executeQueriesFrom
import org.jooq.impl.DSL

interface SqlErsTransaction : Transaction, SqlErsContext

class SqlErsTransactionImpl(
    ersContext: SqlErsContext
) : SqlErsTransaction, SqlErsContext by ersContext {
    init {
        connection.autoCommit = false
    }

    override val isReadonly = false

    override val isFinished: Boolean
        get() = connection.isClosed

    override fun newEntity(type: String): Entity {
        val entityId = nextEntityId()
        val typeId = getOrCreateTypeId(type)
        val entityTable = getOrCreateEntityTableByTypeId(typeId)
        jooq.insertInto(entityTable)
            .set(ENTITY_ID_FIELD, entityId)
            .execute()
        return SqlErsEntity(
            EntityId(
                typeId = typeId,
                instanceId = entityId
            ), txn = this
        )
    }

    override fun getEntityOrNull(id: EntityId): Entity? {
        val entityTable = getEntityTableByTypeIdOrNull(id.typeId) ?: return null
        return jooq.select()
            .from(entityTable)
            .where(ENTITY_ID_FIELD.eq(id.instanceId))
            .fetchOne()
            ?.let { SqlErsEntity(id, txn = this) }
    }

    override fun getEntityUnsafe(id: EntityId): Entity = SqlErsEntity(id, txn = this)

    override fun deleteEntity(id: EntityId) {
        val entityTable = getEntityTableByTypeIdOrNull(id.typeId) ?: return
        jooq.deleteFrom(entityTable)
            .where(ENTITY_ID_FIELD.eq(id.instanceId))
            .execute()
    }

    override fun getTypeId(type: String): Int {
        return getTypeIdOrNull(type) ?: return -1
    }

    override fun getPropertyNames(type: String): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getBlobNames(type: String): Set<String> {
        TODO("Not yet implemented")
    }

    override fun getLinkNames(type: String): Set<String> {
        TODO("Not yet implemented")
    }

    override fun all(type: String): EntityIterable {
        val typeId = getTypeIdOrNull(type) ?: return EntityIterable.EMPTY
        val entityTable = getEntityTableByTypeIdOrNull(typeId) ?: return EntityIterable.EMPTY
        return SqlErsEntityIterable(
            condition = DSL.trueCondition(),
            fromTables = listOf(entityTable),
            entityIdField = entityTable.field(ENTITY_ID_FIELD)!!,
            typeId = typeId,
            txn = this
        )
    }

    override fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable {
        val typeId = getTypeIdOrNull(type) ?: return EntityIterable.EMPTY
        val propertyTable = getPropertyTableOrNull(typeId, propertyName) ?: return EntityIterable.EMPTY
        return SqlErsEntityIterable(
            condition = propertyTable.field(PROPERTY_VALUE_FIELD)!!.eq(probablyCompressed(value)),
            fromTables = listOf(propertyTable),
            entityIdField = propertyTable.field(ENTITY_ID_FIELD)!!,
            typeId = typeId,
            txn = this
        )
    }

    override fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable {
        TODO("Not yet implemented")
    }

    override fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable {
        TODO("Not yet implemented")
    }

    override fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable {
        TODO("Not yet implemented")
    }

    override fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable {
        TODO("Not yet implemented")
    }

    override fun dropAll() {
        val dynamicTables = jooq.meta().tables.filter { table ->
            SqlErsNames.DYNAMIC_TABLE_PREFIXES.any { prefix -> table.name.startsWith(prefix) }
        }
        dynamicTables.forEach { table ->
            jooq.execute(DSL.dropTable(DSL.name(table.name)).toString())
        }
        jooq.executeQueriesFrom("ers/sqlite/drop-schema.sql")
        jooq.executeQueriesFrom("ers/sqlite/create-schema.sql")
    }

    override fun commit() {
        connection.commit()
        connection.close()
    }

    override fun abort() {
        connection.rollback()
        connection.close()
    }
}
