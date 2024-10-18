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

import org.jacodb.api.storage.ers.ErsSettings
import org.jacodb.api.storage.ers.EntityRelationshipStorage
import org.jacodb.api.storage.ers.EntityRelationshipStorageSPI

const val RAM_ERS_SPI = "org.jacodb.impl.storage.ers.ram.RAMEntityRelationshipStorageSPI"

class RAMEntityRelationshipStorageSPI : EntityRelationshipStorageSPI {

    override val id = RAM_ERS_SPI

    override fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage {
        require(persistenceLocation == null) { "RAM ERS can't be persisted" }
        return RAMEntityRelationshipStorage()
    }
}