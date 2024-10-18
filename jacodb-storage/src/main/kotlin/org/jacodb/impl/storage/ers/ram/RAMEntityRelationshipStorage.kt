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

import org.jacodb.api.storage.ers.Binding
import org.jacodb.api.storage.ers.EntityRelationshipStorage
import org.jacodb.impl.storage.ers.decorators.withAllDecorators
import org.jacodb.impl.storage.ers.getBinding
import java.util.concurrent.atomic.AtomicReference

internal class RAMEntityRelationshipStorage(dataContainer: RAMDataContainer = RAMDataContainerMutable()) :
    EntityRelationshipStorage {

    private val data: AtomicReference<RAMDataContainer> = AtomicReference(dataContainer)

    override val isInRam: Boolean get() = true

    override fun beginTransaction(readonly: Boolean) = RAMTransaction(this).withAllDecorators()

    override val asReadonly: EntityRelationshipStorage
        get() = if (dataContainer is RAMDataContainerImmutable) this else RAMEntityRelationshipStorage(dataContainer.toImmutable())

    override fun <T : Any> getBinding(clazz: Class<T>): Binding<T> = clazz.getBinding()

    override fun close() {
        data.set(RAMDataContainerMutable())
    }

    internal var dataContainer: RAMDataContainer
        get() = data.get()
        set(value) {
            data.set(value)
        }

    internal fun compareAndSetDataContainer(
        expected: RAMDataContainer,
        newOne: RAMDataContainer
    ): Boolean = data.compareAndSet(expected, newOne)
}