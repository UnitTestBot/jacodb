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

package org.jacodb.impl.storage.ers.decorators

import org.jacodb.api.jvm.storage.ers.Entity
import org.jacodb.api.jvm.storage.ers.EntityId
import org.jacodb.api.jvm.storage.ers.EntityIterable
import org.jacodb.api.jvm.storage.ers.EntityRelationshipStorage
import org.jacodb.api.jvm.storage.ers.Transaction
import org.jacodb.impl.storage.ers.decorators.CombineOperation.MINUS
import org.jacodb.impl.storage.ers.decorators.CombineOperation.PLUS
import org.jacodb.impl.storage.ers.decorators.CombineOperation.TIMES

abstract class AbstractTransactionDecorator : Transaction {
    internal abstract val delegate: Transaction

    override val ers: EntityRelationshipStorage get() = delegate.ers
    override val isReadonly: Boolean get() = delegate.isReadonly
    override val isFinished: Boolean get() = delegate.isFinished
    override fun newEntity(type: String): Entity = delegate.newEntity(type)
    override fun getEntityOrNull(id: EntityId): Entity? = delegate.getEntityOrNull(id)
    override fun deleteEntity(id: EntityId) = delegate.deleteEntity(id)
    override fun getTypeId(type: String): Int = delegate.getTypeId(type)
    override fun all(type: String): EntityIterable = delegate.all(type)
    override fun <T : Any> find(type: String, propertyName: String, value: T): EntityIterable =
        delegate.find(type, propertyName, value)
    override fun <T : Any> findLt(type: String, propertyName: String, value: T): EntityIterable =
        delegate.findLt(type, propertyName, value)
    override fun <T : Any> findEqOrLt(type: String, propertyName: String, value: T): EntityIterable =
        delegate.findEqOrLt(type, propertyName, value)
    override fun <T : Any> findGt(type: String, propertyName: String, value: T): EntityIterable =
        delegate.findGt(type, propertyName, value)
    override fun <T : Any> findEqOrGt(type: String, propertyName: String, value: T): EntityIterable =
        delegate.findEqOrGt(type, propertyName, value)

    override fun dropAll() = delegate.dropAll()
    override fun commit() = delegate.commit()
    override fun abort() = delegate.abort()
}


enum class CombineOperation {
    PLUS,
    TIMES,
    MINUS
}

fun EntityIterable.combine(operation: CombineOperation, other: EntityIterable) = when (operation) {
    PLUS -> plus(other)
    TIMES -> times(other)
    MINUS -> minus(other)
}

abstract class AbstractEntityIterableDecorator : EntityIterable {
    protected abstract val delegate: EntityIterable
    protected open fun combine(operation: CombineOperation, unwrappedOther: EntityIterable): EntityIterable =
        delegate.combine(operation, unwrappedOther)

    protected open fun unwrapOther(other: EntityIterable) = (other as AbstractEntityIterableDecorator).delegate

    override fun iterator(): Iterator<Entity> = delegate.iterator()
    override val size: Long get() = delegate.size
    override val isEmpty: Boolean get() = delegate.isEmpty
    override val isNotEmpty: Boolean get() = delegate.isNotEmpty
    override fun contains(e: Entity): Boolean = delegate.contains(e)
    final override fun plus(other: EntityIterable): EntityIterable = combine(PLUS, unwrapOther(other))
    final override fun times(other: EntityIterable): EntityIterable = combine(TIMES, unwrapOther(other))
    final override fun minus(other: EntityIterable): EntityIterable = combine(MINUS, unwrapOther(other))
    override fun deleteAll() = delegate.deleteAll()
}


abstract class AbstractEntityDecorator : Entity() {
    internal abstract val delegate: Entity

    override val id: EntityId get() = delegate.id
    override val txn: Transaction get() = delegate.txn

    override fun getRawProperty(name: String): ByteArray? = delegate.getRawProperty(name)
    override fun setRawProperty(name: String, value: ByteArray?) = delegate.setRawProperty(name, value)

    override fun getRawBlob(name: String): ByteArray? = delegate.getRawBlob(name)
    override fun setRawBlob(name: String, blob: ByteArray?) = delegate.setRawBlob(name, blob)

    override fun getLinks(name: String): EntityIterable = delegate.getLinks(name)
    override fun addLink(name: String, targetId: EntityId): Boolean = delegate.addLink(name, targetId)
    override fun deleteLink(name: String, targetId: EntityId): Boolean = delegate.deleteLink(name, targetId)

    override fun equals(other: Any?): Boolean = delegate == other
    override fun hashCode(): Int = delegate.hashCode()
    override fun compareTo(other: Entity): Int = delegate.compareTo(other)
    override fun toString(): String = delegate.toString()
}
