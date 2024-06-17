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

interface EntityIterable : Iterable<Entity> {

    val size: Long

    val isEmpty: Boolean

    val isNotEmpty: Boolean get() = !isEmpty

    operator fun contains(e: Entity): Boolean

    operator fun plus(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> this
        else -> CollectionEntityIterable(union(other))
    }

    operator fun times(other: EntityIterable): EntityIterable = when (other) {
        EMPTY -> EMPTY
        else -> CollectionEntityIterable(intersect(other))
    }

    operator fun minus(other: EntityIterable): EntityIterable = when(other) {
        EMPTY -> this
        else -> CollectionEntityIterable(subtract(other))
    }

    fun deleteAll() = forEach { entity -> entity.delete() }

    companion object {

        val EMPTY: EntityIterable = object : EntityIterable {

            override val size = 0L

            override val isEmpty = true

            override fun contains(e: Entity) = false

            override fun iterator(): Iterator<Entity> = emptyList<Entity>().iterator()

            override fun plus(other: EntityIterable): EntityIterable = other

            override fun minus(other: EntityIterable): EntityIterable = this

            override fun times(other: EntityIterable): EntityIterable = this
        }
    }
}

class CollectionEntityIterable(private val set: Collection<Entity>) : EntityIterable {

    override val size = set.size.toLong()

    override val isEmpty = set.isEmpty()

    override fun contains(e: Entity) = e in set

    override fun iterator() = set.iterator()
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