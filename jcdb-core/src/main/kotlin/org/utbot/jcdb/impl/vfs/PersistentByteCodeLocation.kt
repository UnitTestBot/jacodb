package org.utbot.jcdb.impl.vfs

import org.utbot.jcdb.api.JCDBPersistence
import org.utbot.jcdb.api.JavaVersion
import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.LocationType
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.fs.asByteCodeLocation
import org.utbot.jcdb.impl.storage.jooq.tables.records.BytecodelocationsRecord
import org.utbot.jcdb.impl.storage.jooq.tables.references.BYTECODELOCATIONS
import java.io.File

class PersistentByteCodeLocation(
    override val id: Long,
    override val jcLocation: JcByteCodeLocation
) : RegisteredLocation {

    constructor(entity: BytecodelocationsRecord, javaVersion: JavaVersion) : this(entity.id!!, entity.toJcLocation(javaVersion))

}

class LazyPersistentByteCodeLocation(
    private val persistence: JCDBPersistence,
    override val id: Long,
    private val runtimeVersion: JavaVersion
) :
    RegisteredLocation {

    override val jcLocation: JcByteCodeLocation
        get() {
            return persistence.read {
                it.fetchOne(BYTECODELOCATIONS, BYTECODELOCATIONS.ID.eq(id))!!.toJcLocation(runtimeVersion)
            }
        }

}


class RestoredJcByteCodeLocation(
    override val path: String,
    override val type: LocationType,
    override val hash: String,
    private val runtimeVersion: JavaVersion
) : JcByteCodeLocation {

    override val jarOrFolder: File
        get() = File(path)

    override fun isChanged(): Boolean {
        val actual = createRefreshed() ?: return true
        return actual.hash != hash
    }

    override fun createRefreshed(): JcByteCodeLocation? {
        if (!jarOrFolder.exists()) {
            return null
        }
        return jarOrFolder.asByteCodeLocation(runtimeVersion, type == LocationType.RUNTIME)
    }

    override fun resolve(classFullName: String) = null

    override val classNames: Set<String>
        get() = emptySet()

    override val classes: Map<String, ByteArray>?
        get() = null
}


fun BytecodelocationsRecord.toJcLocation(runtimeVersion: JavaVersion) = RestoredJcByteCodeLocation(
    path!!,
    LocationType.RUNTIME.takeIf { runtime!! } ?: LocationType.APP,
    hash!!,
    runtimeVersion)