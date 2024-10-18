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

package org.jacodb.api.jvm.storage.ers.typed

import org.jacodb.api.storage.ers.Entity
import org.jacodb.api.storage.ers.EntityId
import org.jacodb.api.storage.ers.EntityIterable
import org.jacodb.api.storage.ers.Transaction
import org.jacodb.api.storage.ers.getBinding

internal class TypedEntityIterableImpl<ENTITY_TYPE : ErsType>(
    override val untypedIterable: EntityIterable
) : TypedEntityIterable<ENTITY_TYPE> {
    override fun iterator(): Iterator<TypedEntity<ENTITY_TYPE>> =
        untypedIterable.iterator()
            .asSequence()
            .map { TypedEntityImpl<ENTITY_TYPE>(it) }
            .iterator()

    override val size get() = untypedIterable.size
    override val isEmpty: Boolean get() = untypedIterable.isEmpty

    override fun contains(entity: TypedEntity<ENTITY_TYPE>): Boolean =
        untypedIterable.contains(entity.untypedEntity)

    override fun plus(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE> =
        TypedEntityIterableImpl(untypedIterable + other.untypedIterable)

    override fun minus(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE> =
        TypedEntityIterableImpl(untypedIterable - other.untypedIterable)

    override fun intersect(other: TypedEntityIterable<ENTITY_TYPE>): TypedEntityIterable<ENTITY_TYPE> =
        TypedEntityIterableImpl(untypedIterable * other.untypedIterable)

    override fun deleteAll() = untypedIterable.deleteAll()
}

internal data class TypedEntityImpl<ENTITY_TYPE : ErsType>(
    override val untypedEntity: Entity
) : TypedEntity<ENTITY_TYPE> {
    override val txn: Transaction get() = untypedEntity.txn
    override val id: TypedEntityId<ENTITY_TYPE> get() = TypedEntityIdImpl(untypedEntity.id)

    override fun compareTo(other: TypedEntity<*>): Int = untypedEntity.compareTo(other.untypedEntity)

    override fun <VALUE : Any> set(property: ErsProperty<ENTITY_TYPE, VALUE, *>, value: VALUE?) {
        val bytes = value?.let { untypedEntity.getBinding(property.valueClass).getBytes(value) }
        when (property.searchability) {
            is ErsSearchability.Searchable -> untypedEntity.setRawProperty(property.name, bytes)
            is ErsSearchability.NonSearchable -> untypedEntity.setRawBlob(property.name, bytes)
        }
    }

    override fun <VALUE : Any> get(property: ErsProperty<ENTITY_TYPE, VALUE, *>): VALUE? {
        val bytes = when (property.searchability) {
            is ErsSearchability.Searchable -> untypedEntity.getRawProperty(property.name)
            is ErsSearchability.NonSearchable -> untypedEntity.getRawBlob(property.name)
        }
        return bytes?.let { untypedEntity.getBinding(property.valueClass).getObject(bytes) }
    }

    override fun <TARGET : ErsType> get(link: ErsLink<ENTITY_TYPE, TARGET>): TypedEntityIterable<TARGET> =
        TypedEntityIterableImpl(untypedEntity.getLinks(link.name))

    override fun <TARGET : ErsType> addLink(
        link: ErsLink<ENTITY_TYPE, TARGET>,
        target: TypedEntity<TARGET>
    ): Boolean = untypedEntity.addLink(link.name, target.untypedEntity)

    override fun <TARGET : ErsType> deleteLink(
        link: ErsLink<ENTITY_TYPE, TARGET>,
        target: TypedEntity<TARGET>
    ): Boolean = untypedEntity.deleteLink(link.name, target.untypedEntity)

    override fun delete() = untypedEntity.delete()
}

internal data class TypeIdImpl<ENTITY_TYPE : ErsType>(
    override val typeId: Int
) : TypeId<ENTITY_TYPE> {
    override fun toEntityId(instanceId: Long): TypedEntityId<ENTITY_TYPE> =
        TypedEntityIdImpl(
            EntityId(
                typeId = typeId,
                instanceId = instanceId
            )
        )
}

internal data class TypedEntityIdImpl<ENTITY_TYPE : ErsType>(
    override val entityId: EntityId
) : TypedEntityId<ENTITY_TYPE> {
    override val typeId: TypeId<ENTITY_TYPE> get() = TypeIdImpl(entityId.typeId)
}
