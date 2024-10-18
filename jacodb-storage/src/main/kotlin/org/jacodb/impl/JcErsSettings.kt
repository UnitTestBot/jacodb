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

package org.jacodb.impl

import org.jacodb.api.storage.ers.ErsSettings
import org.jacodb.impl.storage.kv.lmdb.LMDB_KEY_VALUE_STORAGE_SPI

/**
 * Id of pluggable K/V storage being passed for [org.jacodb.impl.storage.ers.kv.KVEntityRelationshipStorageSPI].
 */
open class JcKvErsSettings(val kvId: String) : ErsSettings

// by default, mapSize is 1Gb
class JcLmdbErsSettings(val mapSize: Long = 0x40_00_00_00) : JcKvErsSettings(LMDB_KEY_VALUE_STORAGE_SPI)

