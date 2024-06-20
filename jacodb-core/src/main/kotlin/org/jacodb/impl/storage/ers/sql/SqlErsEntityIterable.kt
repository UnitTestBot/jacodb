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

import org.jacodb.api.jvm.storage.ers.Entity
import org.jacodb.api.jvm.storage.ers.EntityId
import org.jacodb.api.jvm.storage.ers.EntityIterable
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Table

// TODO optimize implementation
class SqlErsEntityIterable(
    private val condition: Condition,
    private val fromTables: List<Table<*>>,
    private val entityIdField: Field<Long>,
    private val typeId: Int,
    private val txn: SqlErsTransaction
) : EntityIterable {
    private val jooq get() = txn.jooq

    override val size: Long
        get() = iterator().asSequence().count().toLong()

    override val isEmpty: Boolean get() = !iterator().hasNext()

    override fun contains(e: Entity): Boolean = iterator().asSequence().contains(e)

    override fun plus(other: EntityIterable): EntityIterable {
        return super.plus(other)
    }

    override fun times(other: EntityIterable): EntityIterable {
        return super.times(other)
    }

    override fun minus(other: EntityIterable): EntityIterable {
        return super.minus(other)
    }

    override fun deleteAll() {
        super.deleteAll()
    }

    override fun iterator(): Iterator<Entity> {
        return jooq.select(entityIdField)
            .from(fromTables)
            .where(condition)
            .fetch()
            .map {
                SqlErsEntity(EntityId(typeId, it.value1()), txn)
            }
            .iterator()
    }
}
