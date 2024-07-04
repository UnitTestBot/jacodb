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

package org.jacodb.impl.storage

import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.util.*

fun configuredSQLiteDataSource(location: String? = null): SQLiteDataSource {
    val config = SQLiteConfig().also {
        it.setSynchronous(SQLiteConfig.SynchronousMode.OFF)
        it.setJournalMode(SQLiteConfig.JournalMode.OFF)
        it.setPageSize(32_768)
        it.setCacheSize(-8_000)
        it.setSharedCache(true)
    }
    val props = listOfNotNull(
        ("mode" to "memory").takeIf { location == null },
        ("cache" to "shared").takeIf { location == null },
        "rewriteBatchedStatements" to "true",
        "useServerPrepStmts" to "false"
    ).joinToString("&") { "${it.first}=${it.second}" }
    return SQLiteDataSource(config).also {
        it.url = "jdbc:sqlite:file:${location ?: ("jcdb-" + UUID.randomUUID())}?$props"
    }
}