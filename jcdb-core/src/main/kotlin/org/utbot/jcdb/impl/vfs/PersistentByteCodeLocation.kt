package org.utbot.jcdb.impl.vfs

import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.utbot.jcdb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

class PersistentByteCodeLocation(
    private val jcdb: JCDB,
    override val id: Long,
    private val location: JcByteCodeLocation? = null
) : RegisteredLocation {

    override val jcLocation: JcByteCodeLocation?
        get() {
            return location ?: jcdb.persistence.read { jooq ->
                jooq.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!.toJcLocation()
            }
        }


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
            val newOne = File(path!!).asByteCodeLocation(jcdb.runtimeVersion, isRuntime = runtime!!)
            if (newOne.hash != hash!!) {
                return null
            }
            return newOne
        } catch (e: Exception) {
            return null
        }
    }
}

