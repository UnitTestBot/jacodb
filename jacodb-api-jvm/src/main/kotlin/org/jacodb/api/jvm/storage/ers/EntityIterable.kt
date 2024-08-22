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

package org.jacodb.api.jvm.storage.ers

interface EntityIterable : Sequence<Entity> {

    val size: Long get() = toEntityIdSet().size.toLong()

    val isEmpty: Boolean get() = size == 0L

    val isNotEmpty: Boolean get() = !isEmpty

    operator fun contains(e: Entity): Boolean = toEntityIdSet().contains(e.id)

    operator fun plus(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> this
        else -> this.union(other)
    }

    operator fun times(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> EMPTY
        else -> this.intersect(other)
    }

    operator fun minus(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> this
        else -> this.subtract(other)
    }

    fun deleteAll() = forEach { entity -> entity.delete() }

    companion object {

        val EMPTY: EntityIterable = object : EntityIterable {

            override val size = 0L

            override fun contains(e: Entity) = false

            override fun iterator(): Iterator<Entity> = emptyList<Entity>().iterator()

            override fun plus(other: EntityIterable): EntityIterable = other

            override fun minus(other: EntityIterable): EntityIterable = this

            override fun times(other: EntityIterable): EntityIterable = this
        }
    }
}

class CollectionEntityIterable(private val c: Collection<Entity>) : EntityIterable {

    override val size = c.size.toLong()

    override val isEmpty = c.isEmpty()

    override fun contains(e: Entity) = e in c

    override fun iterator() = c.iterator()
}

class EntityIdCollectionEntityIterable(
    private val txn: Transaction,
    private val set: Collection<EntityId>
) : EntityIterable {

    override val size = set.size.toLong()

    override val isEmpty = set.isEmpty()

    override fun contains(e: Entity) = e.id in set

    override fun iterator() = buildList {
        set.forEach { id -> txn.getEntityOrNull(id)?.let { e -> add(e) } }
    }.iterator()
}

class InstanceIdCollectionEntityIterable(
    private val txn: Transaction,
    private val typeId: Int,
    private val set: Collection<Long>
) : EntityIterable {

    override val size = set.size.toLong()

    override val isEmpty = set.isEmpty()

    override fun contains(e: Entity) = e.id.typeId == typeId && e.id.instanceId in set

    override fun iterator() = buildList {
        set.forEach { instanceId -> txn.getEntityOrNull(EntityId(typeId, instanceId))?.let { e -> add(e) } }
    }.iterator()
}