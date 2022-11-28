package org.utbot.jcdb.impl.vfs

import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.api.JavaVersion
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.utbot.jcdb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

class PersistentByteCodeLocation(
    private val persistence: JCDBPersistence,
    private val runtimeVersion: JavaVersion,
    override val id: Long,
    private val cachedRecord: BytecodelocationsRecord? = null,
    private val cachedLocation: JcByteCodeLocation? = null
) : RegisteredLocation {

    constructor(jcdb: JCDB, record: BytecodelocationsRecord, location: JcByteCodeLocation? = null) : this(
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

