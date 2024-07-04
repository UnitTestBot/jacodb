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

import org.jacodb.api.jvm.JavaVersion
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcDatabasePersistence
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.storage.ers.Entity
import org.jacodb.api.jvm.storage.ers.getEntityOrNull
import org.jacodb.impl.fs.asByteCodeLocation
import org.jacodb.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.jacodb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

data class PersistentByteCodeLocationData(
    val id: Long,
    val runtime: Boolean,
    val path: String,
    val fileSystemId: String
) {
    companion object {
        fun fromSqlRecord(record: BytecodelocationsRecord) =
            PersistentByteCodeLocationData(record.id!!, record.runtime!!, record.path!!, record.uniqueid!!)

        fun fromErsEntity(entity: Entity) = PersistentByteCodeLocationData(
            id = entity.id.instanceId,
            runtime = (entity.get<Boolean>(BytecodeLocationEntity.IS_RUNTIME) == true),
            path = entity[BytecodeLocationEntity.PATH]!!,
            fileSystemId = entity[BytecodeLocationEntity.FILE_SYSTEM_ID]!!
        )
    }
}

class PersistentByteCodeLocation(
    private val persistence: JcDatabasePersistence,
    private val runtimeVersion: JavaVersion,
    override val id: Long,
    private val cachedData: PersistentByteCodeLocationData? = null,
    private val cachedLocation: JcByteCodeLocation? = null
) : RegisteredLocation {

    constructor(
        db: JcDatabase,
        data: PersistentByteCodeLocationData,
        location: JcByteCodeLocation? = null
    ) : this(
        db.persistence,
        db.runtimeVersion,
        data.id,
        data,
        location
    )

    constructor(db: JcDatabase, locationId: Long) : this(
        db.persistence,
        db.runtimeVersion,
        locationId,
        null,
        null
    )

    val data by lazy {
        cachedData ?: persistence.read { context ->
            context.execute(
                sqlAction = { jooq ->
                    val record = jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!
                    PersistentByteCodeLocationData.fromSqlRecord(record)
                },
                noSqlAction = { txn ->
                    val entity = txn.getEntityOrNull(BytecodeLocationEntity.BYTECODE_LOCATION_ENTITY_TYPE, id)!!
                    PersistentByteCodeLocationData.fromErsEntity(entity)
                }
            )
        }
    }

    override val jcLocation: JcByteCodeLocation?
        get() {
            return cachedLocation ?: data.toJcLocation()
        }

    override val path: String
        get() = data.path

    override val isRuntime: Boolean
        get() = data.runtime

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

    private fun PersistentByteCodeLocationData.toJcLocation(): JcByteCodeLocation? {
        try {
            val newOne = File(path).asByteCodeLocation(runtimeVersion, isRuntime = runtime)
            if (newOne.fileSystemId != fileSystemId) {
                return null
            }
            return newOne
        } catch (e: Exception) {
            return null
        }
    }
}

