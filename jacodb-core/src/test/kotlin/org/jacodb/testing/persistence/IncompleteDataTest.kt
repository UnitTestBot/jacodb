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

package org.jacodb.testing.persistence

import kotlinx.coroutines.runBlocking
import org.jacodb.impl.FeaturesRegistry
import org.jacodb.impl.JcSettings
import org.jacodb.impl.features.Builders
import org.jacodb.impl.features.Usages
import org.jacodb.impl.fs.JavaRuntime
import org.jacodb.impl.jacodb
import org.jacodb.impl.storage.LocationState
import org.jacodb.impl.storage.SQLitePersistenceImpl
import org.jacodb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import org.jacodb.testing.allClasspath
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files


class IncompleteDataTest {

    companion object {
        private val jdbcLocation = Files.createTempFile("jcdb-", null).toFile().absolutePath
        private val javaHome: File = JcSettings().useProcessJavaRuntime().jre
        val db = newDB(true).also {
            it.close()
        }

        private fun newDB(awaitBackground: Boolean) = runBlocking {
            jacodb {
                useProcessJavaRuntime()
                persistent(jdbcLocation)
                installFeatures(Usages, Builders)
                loadByteCode(allClasspath)
            }.also {
                if (awaitBackground) {
                    it.awaitBackgroundJobs()
                }
            }
        }
    }

    @Test
    fun `if runtime is not processed schema should be dropped`() {
        withPersistence { jooq ->
            jooq.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.AWAITING_INDEXING.ordinal)
                .execute()
        }
        val db = newDB(true)
        db.persistence.read {
            val count = it.fetchCount(
                BYTECODELOCATIONS,
                BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal)
            )
            assertEquals(0, count)
        }
    }

    @Test
    fun `if runtime is processed unprocessed libraries should be outdated`() {
        val ids = arrayListOf<Long>()
        withPersistence { jooq ->
            jooq.update(BYTECODELOCATIONS)
                .set(BYTECODELOCATIONS.STATE, LocationState.AWAITING_INDEXING.ordinal)
                .where(BYTECODELOCATIONS.RUNTIME.isFalse)
                .execute()
            jooq.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.RUNTIME.isFalse)
                .fetch {
                    ids.add(it.id!!)
                }
        }
        val db = newDB(true)
        db.persistence.read {
            it.selectFrom(BYTECODELOCATIONS)
                .where(BYTECODELOCATIONS.STATE.notEqual(LocationState.PROCESSED.ordinal))
                .fetch {
                    assertTrue(
                        ids.contains(it.id!!),
                        "expected ${it.path} to be in PROCESSED state buy is in ${LocationState.values()[it.state!!]}"
                    )

                }
        }
    }


    private fun withPersistence(action: (DSLContext) -> Unit) {
        val persistence = SQLitePersistenceImpl(
            JavaRuntime(javaHome), FeaturesRegistry(emptyList()), jdbcLocation, false
        )
        persistence.use {
            it.write {
                action(it)
            }
        }
    }

}