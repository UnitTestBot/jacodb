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

package org.jacodb.impl.storage.ers.kv

import org.jacodb.api.storage.ers.EntityRelationshipStorage
import org.jacodb.api.storage.ers.EntityRelationshipStorageSPI
import org.jacodb.api.storage.ers.ErsSettings
import org.jacodb.api.storage.kv.PluggableKeyValueStorageSPI
import org.jacodb.impl.JcKvErsSettings

const val KV_ERS_SPI = "org.jacodb.impl.storage.ers.kv.KVEntityRelationshipStorageSPI"

/**
 * Service provider interface for creating instances of [org.jacodb.api.storage.ers.EntityRelationshipStorage]
 * running atop of an instance of [org.jacodb.api.storage.kv.PluggableKeyValueStorage] identified by its id.
 */
class KVEntityRelationshipStorageSPI : EntityRelationshipStorageSPI {

    override val id = KV_ERS_SPI

    override fun newStorage(persistenceLocation: String?, settings: ErsSettings): EntityRelationshipStorage {
        settings as JcKvErsSettings
        val kvSpi = PluggableKeyValueStorageSPI.getProvider(settings.kvId)
        val kvStorage = kvSpi.newStorage(persistenceLocation, settings)
        kvStorage.isMapWithKeyDuplicates = { mapName -> mapName.isMapWithKeyDuplicates }
        return KVEntityRelationshipStorage(kvStorage)
    }
}