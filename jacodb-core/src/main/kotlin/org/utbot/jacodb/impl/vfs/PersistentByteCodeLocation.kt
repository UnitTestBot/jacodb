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

package org.utbot.jacodb.impl.vfs

import org.utbot.jacodb.api.JavaVersion
import org.utbot.jacodb.api.JcByteCodeLocation
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcDatabase
import org.utbot.jacodb.api.JcDatabasePersistence
import org.utbot.jacodb.api.RegisteredLocation
import org.utbot.jacodb.impl.fs.asByteCodeLocation
import org.utbot.jacodb.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.utbot.jacodb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

class PersistentByteCodeLocation(
    private val persistence: JcDatabasePersistence,
    private val runtimeVersion: JavaVersion,
    override val id: Long,
    private val cachedRecord: BytecodelocationsRecord? = null,
    private val cachedLocation: JcByteCodeLocation? = null
) : RegisteredLocation {

    constructor(jcdb: JcDatabase, record: BytecodelocationsRecord, location: JcByteCodeLocation? = null) : this(
        jcdb.persistence,
        jcdb.runtimeVersion,
        record.id!!,
        record,
        location
    )

    constructor(cp: JcClasspath, locationId: Long) : this(
        cp.db.persistence,
        cp.db.runtimeVersion,
        locationId,
        null,
        null
    )

    val record by lazy {
        cachedRecord ?: persistence.read { jooq ->
            jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!
        }
    }

    override val jcLocation: JcByteCodeLocation?
        get() {
            return cachedLocation ?: record.toJcLocation()
        }

    override val path: String
        get() = record.path!!

    override val runtime: Boolean
        get() = record.runtime!!

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegisteredLocation

        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    private fun BytecodelocationsRecord.toJcLocation(): JcByteCodeLocation? {
        try {
            val newOne = File(path!!).asByteCodeLocation(runtimeVersion, isRuntime = runtime!!)
            if (newOne.fsId != uniqueid!!) {
                return null
            }
            return newOne
        } catch (e: Exception) {
            return null
        }
    }
}

