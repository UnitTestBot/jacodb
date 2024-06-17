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

import org.jacodb.api.jvm.storage.ers.ERSNonExistingEntityException
import org.jacodb.api.jvm.storage.ers.Entity
import org.jacodb.api.jvm.storage.ers.EntityId
import org.jacodb.api.jvm.storage.ers.EntityIterable
import org.jacodb.api.jvm.storage.ers.EntityRelationshipStorage
import org.jacodb.api.jvm.storage.ers.Transaction

fun Transaction.withChecks(): Transaction =
    CheckedTransaction(uncheckedDelegate = this).decorateDeeply(
        entityIterableWrapper = { CheckedEntityIterable(it, ownerTxn = this) },
        entityWrapper = { CheckedEntity(it, ownerTxn = this) }
    )

private class CheckedTransaction(
    private val uncheckedDelegate: Transaction,
) : AbstractTransactionDecorator() {
    override val delegate: Transaction
        get() {
            uncheckedDelegate.checkIsNotFinished()
            return uncheckedDelegate
        }

    // NOTE: properties bypass checks
    override val ers: EntityRelationshipStorage
        get() = uncheckedDelegate.ers
    override val isFinished: Boolean
        get() = uncheckedDelegate.isFinished
}


private class CheckedEntityIterable(
    private val uncheckedDelegate: EntityIterable,
    private val ownerTxn: Transaction
) : AbstractEntityIterableDecorator() {
    override val delegate: EntityIterable
        get() {
            ownerTxn.checkIsNotFinished()
            return uncheckedDelegate
        }

    override fun unwrapOther(other: EntityIterable): EntityIterable {
        require((other as CheckedEntityIterable).ownerTxn === ownerTxn) {
            "Cannot combine EntityIterables from different transactions"
        }
        return super.unwrapOther(other)
    }
}

private class CheckedEntity(
    private val uncheckedDelegate: Entity,
    private val ownerTxn: Transaction
) : AbstractEntityDecorator() {
    override val delegate: Entity
        get() {
            ownerTxn.checkIsNotFinished()
            uncheckedDelegate.id.checkExists()
            return uncheckedDelegate
        }

    // NOTE: properties bypass checks
    override val id: EntityId get() = uncheckedDelegate.id
    override val txn: Transaction get() = uncheckedDelegate.txn

    override fun addLink(name: String, targetId: EntityId): Boolean {
        targetId.checkExists()
        return super.addLink(name, targetId)
    }

    override fun deleteLink(name: String, targetId: EntityId): Boolean {
        targetId.checkExists()
        return super.deleteLink(name, targetId)
    }

    // TODO add ERS API endpoint for faster exists checks
    private fun EntityId.checkExists() {
        if (txn.unwrap.isEntityDeleted(this)) {
            throw ERSNonExistingEntityException()
        }
    }
}
