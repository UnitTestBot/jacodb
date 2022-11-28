package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.JcClasspath
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.api.throwClassNotFound
import org.utbot.jcdb.impl.vfs.PersistentByteCodeLocation

class ClassSourceImpl(
    override val location: RegisteredLocation,
    override val className: String,
    override val byteCode: ByteArray
) : ClassSource

class LazyClassSourceImpl(
    override val location: RegisteredLocation,
    override val className: String
) : ClassSource {

    override val byteCode by lazy {
        location.jcLocation?.resolve(className) ?: className.throwClassNotFound()
    }
}

class PersistenceClassSource(
    private val classpath: JcClasspath,
    override val className: String,
    val classId: Long,
    val locationId: Long
) : ClassSource {

    override val location = PersistentByteCodeLocation(classpath, locationId)

    override val byteCode by lazy {
        classpath.db.persistence.findBytecode(classId)
    }
}
