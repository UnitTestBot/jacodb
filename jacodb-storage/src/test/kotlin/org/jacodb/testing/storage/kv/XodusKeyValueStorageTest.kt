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

package org.jacodb.testing.storage.kv

import jetbrains.exodus.io.DataReaderWriterProvider
import org.jacodb.impl.JcXodusErsSettings
import org.jacodb.impl.storage.kv.xodus.XODUS_KEY_VALUE_STORAGE_SPI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.Long.getLong

class XodusKeyValueStorageTest : PluggableKeyValueStorageTest() {

    override val kvStorageId = XODUS_KEY_VALUE_STORAGE_SPI

    @Test
    fun `test shared usage of the same db`() {
        val settings = JcXodusErsSettings {
            logDataReaderWriterProvider = DataReaderWriterProvider.WATCHING_READER_WRITER_PROVIDER
        }
        val roStorage = kvStorageSpi.newStorage(location = location, settings = settings)
        roStorage.transactional { txn ->
            assertTrue(txn.isReadonly)
            assertNull(txn.get("a map", "key".asByteArray))
        }
        putGet()
        Thread.sleep(getLong("jetbrains.exodus.io.watching.forceCheckEach", 3000L) + 500)
        roStorage.transactional { txn ->
            val got = txn.get("a map", "key".asByteArray)
            assertNotNull(got)
            assertEquals("value", got?.asString)
        }
    }
}