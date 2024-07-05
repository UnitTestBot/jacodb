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

package org.jacodb.impl.storage.kv.lmdb

import org.jacodb.api.jvm.storage.ers.ErsSettings
import org.jacodb.api.jvm.storage.kv.PluggableKeyValueStorage
import org.jacodb.api.jvm.storage.kv.PluggableKeyValueStorageSPI
import org.jacodb.impl.JcLmdbErsSettings
import kotlin.io.path.createTempDirectory

const val LMDB_KEY_VALUE_STORAGE_SPI = "org.jacodb.impl.storage.kv.lmdb.LmdbKeyValueStorageSPI"

class LmdbKeyValueStorageSPI : PluggableKeyValueStorageSPI {

    override val id = LMDB_KEY_VALUE_STORAGE_SPI

    override fun newStorage(location: String?, settings: ErsSettings): PluggableKeyValueStorage {
        return LmdbKeyValueStorage(
            location ?: createTempDirectory(prefix = "lmdbKeyValueStorage").toString(),
            if (settings is JcLmdbErsSettings) settings else JcLmdbErsSettings()
        )
    }
}