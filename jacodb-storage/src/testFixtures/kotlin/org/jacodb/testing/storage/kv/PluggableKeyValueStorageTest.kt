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

import org.jacodb.api.storage.kv.PluggableKeyValueStorage
import org.jacodb.api.storage.kv.PluggableKeyValueStorageSPI
import org.jacodb.api.storage.kv.asIterable
import org.jacodb.impl.storage.ers.BuiltInBindingProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

abstract class PluggableKeyValueStorageTest {

    abstract val kvStorageId: String

    private val kvStorageSpi by lazy(LazyThreadSafetyMode.NONE) {
        PluggableKeyValueStorageSPI.getProvider(kvStorageId)
    }
    protected lateinit var storage: PluggableKeyValueStorage

    @Test
    fun putGet() {
        storage.transactional { txn ->
            txn.put("a map", "key".asByteArray, "value".asByteArray)
            val got = txn.get("a map", "key".asByteArray)
            assertNotNull(got)
            assertEquals("value", got?.asString)
        }
    }

    @Test
    fun putGetAutoCommit() {
        assertNull(storage.get("a map", "key".asByteArray))
        assertTrue(storage.put("a map", "key".asByteArray, "value".asByteArray))
        val got = storage.get("a map", "key".asByteArray)
        assertNotNull(got)
        assertEquals("value", got?.asString)
    }

    @Test
    fun putGetDeleteAutoCommit() {
        putGetAutoCommit()
        storage.delete("a map", "key".asByteArray)
        assertNull(storage.get("a map", "key".asByteArray))
        assertEquals(0L, storage.mapSize("a map"))
    }

    @Test
    fun putGetCursor() {
        putGetAutoCommit()
        assertTrue(storage.put("a map", "key1".asByteArray, "value1".asByteArray))
        storage.transactional { txn ->
            txn.navigateTo("a map").use { cursor ->
                assertTrue(cursor.moveNext())
                assertEquals("key", cursor.key.asString)
                assertEquals("value", cursor.value.asString)
            }
        }
    }

    @Test
    fun putGetCursorReversed() {
        putGetAutoCommit()
        assertTrue(storage.put("a map", "value".asByteArray, "key".asByteArray))
        storage.transactional { txn ->
            txn.navigateTo("a map").use { cursor ->
                assertTrue(cursor.movePrev())
                assertEquals("value", cursor.key.asString)
                assertEquals("key", cursor.value.asString)
            }
        }
    }

    @Test
    fun cursorNavigateWithDuplicates() {
        storage.transactional { txn ->
            assertTrue(txn.put("map#withDuplicates", "key0".asByteArray, "value0".asByteArray))
            assertTrue(txn.put("map#withDuplicates", "key00".asByteArray, "value00".asByteArray))
            assertTrue(txn.put("map#withDuplicates", "key1".asByteArray, "value1".asByteArray))
            assertTrue(txn.put("map#withDuplicates", "key1".asByteArray, "value10".asByteArray))
            assertFalse(txn.put("map#withDuplicates", "key1".asByteArray, "value10".asByteArray))
            assertTrue(txn.put("map#withDuplicates", "key1".asByteArray, "value11".asByteArray))
            assertTrue(txn.put("map#withDuplicates", "key3".asByteArray, "value2".asByteArray))
            assertTrue(txn.put("map#withDuplicates", "key3".asByteArray, "value1".asByteArray))
        }
        storage.readonlyTransactional { txn ->
            txn.navigateTo("map#withDuplicates").use { cursor ->
                val pairs = cursor.asIterable().map { it.first.asString to it.second.asString }
                assertEquals(7, pairs.size)
            }
            txn.navigateTo("map#withDuplicates", "key".asByteArray).use { cursor ->
                val pairs = cursor.asIterable().map { it.first.asString to it.second.asString }
                assertEquals(7, pairs.size)
            }
            txn.navigateTo("map#withDuplicates", "key ".asByteArray).use { cursor ->
                val pairs = cursor.asIterable().map { it.first.asString to it.second.asString }
                assertEquals(7, pairs.size)
            }
            txn.navigateTo("map#withDuplicates", "key0".asByteArray).use { cursor ->
                val pairs = cursor.asIterable().map { it.first.asString to it.second.asString }
                assertEquals(7, pairs.size)
            }
            txn.navigateTo("map#withDuplicates", "key1".asByteArray).use { cursor ->
                val pairs = cursor.asIterable().map { it.first.asString to it.second.asString }
                assertEquals(5, pairs.size)
            }
            txn.navigateTo("map#withDuplicates", "key3".asByteArray).use { cursor ->
                val pairs = cursor.asIterable().map { it.first.asString to it.second.asString }
                assertEquals(2, pairs.size)
                assertEquals("key3", pairs[0].first)
                assertEquals("key3", pairs[1].first)
                assertEquals("value1", pairs[0].second)
                assertEquals("value2", pairs[1].second)
            }
            txn.navigateTo("map#withDuplicates", "key2".asByteArray).use { cursor ->
                val pairs = cursor.asIterable().map { it.first.asString to it.second.asString }
                assertEquals(2, pairs.size)
                assertEquals("key3", pairs[0].first)
                assertEquals("key3", pairs[1].first)
                assertEquals("value1", pairs[0].second)
                assertEquals("value2", pairs[1].second)
            }
        }
    }

    @BeforeEach
    fun setUp() {
        storage = kvStorageSpi.newStorage(null).apply {
            isMapWithKeyDuplicates = { mapName -> mapName.endsWith("withDuplicates") }
        }
    }

    @AfterEach
    fun tearDown() {
        storage.close()
    }

    protected val String.asByteArray: ByteArray
        get() = BuiltInBindingProvider.getBinding(String::class.java).getBytes(this)

    protected val ByteArray.asString: String
        get() = BuiltInBindingProvider.getBinding(String::class.java).getObject(this)
}