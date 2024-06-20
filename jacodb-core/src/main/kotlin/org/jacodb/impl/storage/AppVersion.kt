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

import mu.KLogging
import org.jacodb.api.jvm.JCDBContext
import org.jacodb.impl.storage.jooq.tables.references.APPLICATIONMETADATA
import java.util.*

data class AppVersion(val major: Int, val minor: Int) : Comparable<AppVersion> {

    companion object : KLogging() {

        val currentAppVersion = current()
        private val defaultVersion = AppVersion(1, 3)

        fun read(context: JCDBContext): AppVersion {
            return try {
                val appVersion = context.execute(
                    sqlAction = { jooq ->
                        jooq.selectFrom(APPLICATIONMETADATA).fetch().firstOrNull()
                    },
                    noSqlAction = { txn ->
                        txn.all("ApplicationMetadata").firstOrNull()?.let { it["version"] }
                    }
                )
                appVersion?.run {
                    logger.info("Restored app version is $version")
                    parse(appVersion.version!!)
                } ?: currentAppVersion
            } catch (e: Exception) {
                logger.info("fail to restore app version. Use [$defaultVersion] as fallback")
                defaultVersion
            }
        }

        private fun current(): AppVersion {
            val clazz = AppVersion::class.java
            val pack = clazz.`package`
            val version = pack.implementationVersion ?: Properties().also {
                it.load(clazz.getResourceAsStream("/jacodb.properties"))
            }.getProperty("jacodb.version")
            val last = version.indexOfLast { it == '.' || it.isDigit() }
            val clearVersion = version.substring(0, last + 1)
            return parse(clearVersion)
        }

        private fun parse(version: String): AppVersion {
            val ints = version.split(".")
            return AppVersion(ints[0].toInt(), ints[1].toInt())
        }
    }

    fun write(context: JCDBContext) {
        context.execute(
            sqlAction = { jooq ->
                jooq.deleteFrom(APPLICATIONMETADATA).execute()
                jooq.insertInto(APPLICATIONMETADATA)
                    .set(APPLICATIONMETADATA.VERSION, "$major.$minor")
                    .execute()
            },
            noSqlAction = { txn ->
                val metadata = txn.all("ApplicationMetadata").firstOrNull()
                    ?: context.txn.newEntity("ApplicationMetadata")
                metadata["version"] = "$major.$minor"
            }
        )
    }

    override fun compareTo(other: AppVersion): Int {
        return when {
            major > other.major -> 1
            major == other.major -> minor - other.minor
            else -> -1
        }
    }

    override fun toString(): String {
        return "[$major.$minor]"
    }
}