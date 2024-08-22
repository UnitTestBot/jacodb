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

import org.jacodb.api.jvm.JCDBContext
import org.jacodb.api.jvm.JCDBSymbolsInterner
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.jvm.storage.ers.compressed
import org.jacodb.api.jvm.storage.ers.nonSearchable
import org.jacodb.api.jvm.storage.kv.forEach
import org.jacodb.impl.storage.connection
import org.jacodb.impl.storage.ers.BuiltInBindingProvider
import org.jacodb.impl.storage.ers.decorators.unwrap
import org.jacodb.impl.storage.ers.kv.KVErsTransaction
import org.jacodb.impl.storage.execute
import org.jacodb.impl.storage.insertElements
import org.jacodb.impl.storage.jooq.tables.references.SYMBOLS
import org.jacodb.impl.storage.maxId
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

fun String.asSymbolId(symbolInterner: JCDBSymbolsInterner): Long {
    return symbolInterner.findOrNew(this)
}

private const val symbolsMapName = "org.jacodb.impl.Symbols"

class JCDBSymbolsInternerImpl : JCDBSymbolsInterner, Closeable {

    private val symbolsIdGen = AtomicLong()
    private val symbolsCache = ConcurrentHashMap<String, Long>()
    private val idCache = ConcurrentHashMap<Long, String>()
    private val newElements = ConcurrentSkipListMap<String, Long>()

    fun setup(persistence: JcDatabasePersistence) = persistence.read { context ->
        context.execute(
            sqlAction = { jooq ->
                jooq.selectFrom(SYMBOLS).fetch().forEach {
                    val (id, name) = it
                    if (name != null && id != null) {
                        symbolsCache[name] = id
                        idCache[id] = name
                    }
                }
                symbolsIdGen.set(SYMBOLS.ID.maxId(jooq) ?: 0L)
            },
            noSqlAction = { txn ->
                var maxId = -1L
                val unwrapped = txn.unwrap
                if (unwrapped is KVErsTransaction) {
                    val kvTxn = unwrapped.kvTxn
                    val stringBinding = BuiltInBindingProvider.getBinding(String::class.java)
                    val longBinding = BuiltInBindingProvider.getBinding(Long::class.java)
                    kvTxn.navigateTo(symbolsMapName).forEach { idBytes, nameBytes ->
                        val id = longBinding.getObjectCompressed(idBytes)
                        val name = stringBinding.getObject(nameBytes)
                        symbolsCache[name] = id
                        idCache[id] = name
                        maxId = max(maxId, id)
                    }
                } else {
                    val symbols = txn.all("Symbol").toList()
                    symbols.forEach { symbol ->
                        val name: String? = symbol.getBlob("name")
                        val id: Long? = symbol.getCompressedBlob("id")
                        if (name != null && id != null) {
                            symbolsCache[name] = id
                            idCache[id] = name
                            maxId = max(maxId, id)
                        }
                    }
                }
                symbolsIdGen.set(maxId)
            }
        )
    }

    override fun findOrNew(symbol: String): Long {
        return symbolsCache.computeIfAbsent(symbol) {
            symbolsIdGen.incrementAndGet().also {
                newElements[symbol] = it
                idCache[it] = symbol
            }
        }
    }

    override fun findSymbolName(symbolId: Long): String? = idCache[symbolId]

    override fun flush(context: JCDBContext) {
        val entries = newElements.entries.toList()
        if (entries.isNotEmpty()) {
            context.execute(
                sqlAction = {
                    context.connection.insertElements(
                        SYMBOLS,
                        entries,
                        onConflict = "ON CONFLICT(id) DO NOTHING"
                    ) { (value, id) ->
                        setLong(1, id)
                        setString(2, value)
                    }
                },
                noSqlAction = { txn ->
                    val unwrapped = txn.unwrap
                    if (unwrapped is KVErsTransaction) {
                        val kvTxn = unwrapped.kvTxn
                        val symbolsMap = kvTxn.getNamedMap(symbolsMapName, create = true)!!
                        val stringBinding = BuiltInBindingProvider.getBinding(String::class.java)
                        val longBinding = BuiltInBindingProvider.getBinding(Long::class.java)
                        entries.forEach { (name, id) ->
                            kvTxn.put(symbolsMap, longBinding.getBytesCompressed(id), stringBinding.getBytes(name))
                        }
                    } else {
                        entries.forEach { (name, id) ->
                            txn.newEntity("Symbol").also { symbol ->
                                symbol["name"] = name.nonSearchable
                                symbol["id"] = id.compressed.nonSearchable
                            }
                        }
                    }
                }
            )
            entries.forEach {
                newElements.remove(it.key)
            }
        }
    }

    override fun close() {
        symbolsCache.clear()
        newElements.clear()
    }
}