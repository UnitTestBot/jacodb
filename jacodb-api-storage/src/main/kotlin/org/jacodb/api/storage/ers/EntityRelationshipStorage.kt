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

package org.jacodb.api.storage.ers

import org.jacodb.api.spi.CommonSPI
import org.jacodb.api.spi.SPILoader
import java.io.Closeable
import java.util.*

interface EntityRelationshipStorage : Closeable, BindingProvider {

    val isInRam: Boolean get() = false

    @Throws(ERSConflictingTransactionException::class)
    fun beginTransaction(readonly: Boolean = false): Transaction

    @Throws(ERSConflictingTransactionException::class)
    fun <T> transactional(readonly: Boolean = false, action: (Transaction) -> T): T {
        beginTransaction(readonly = readonly).use { txn ->
            return action(txn)
        }
    }

    @Throws(ERSConflictingTransactionException::class)
    fun <T> transactionalOptimistic(attempts: Int = 5, action: (Transaction) -> T): T {
        repeat(attempts) {
            try {
                beginTransaction(readonly = false).use { txn ->
                    return action(txn)
                }
            } catch (_: ERSConflictingTransactionException) {
            }
        }
        throw ERSConflictingTransactionException("Failed to commit transaction after $attempts optimistic attempts")
    }

    /**
     * Returns a read-only storage holding the latest available snapshot of data.
     */
    val asReadonly: EntityRelationshipStorage get() = this
}

interface EntityRelationshipStorageSPI : CommonSPI {

    fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage

    companion object : SPILoader() {

        @JvmStatic
        fun getProvider(id: String): EntityRelationshipStorageSPI {
            return loadSPI(id) ?: throw ERSException("No EntityRelationshipStorageSPI implementation found by id = $id")
        }
    }
}

interface ErsSettings
object EmptyErsSettings : ErsSettings

open class ERSException(
    message: String? = null,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ERSConflictingTransactionException(
    message: String? = null,
    cause: Throwable? = null
) : ERSException(message, cause)

class ERSNonExistingEntityException(
    message: String? = "Cannot perform an operation, because entity doesn't exist",
    cause: Throwable? = null
) : ERSException(message, cause)

class ERSTransactionFinishedException(
    message: String? = "Cannot perform an operation, because transaction has been finished",
    cause: Throwable? = null
) : ERSException(message, cause)
