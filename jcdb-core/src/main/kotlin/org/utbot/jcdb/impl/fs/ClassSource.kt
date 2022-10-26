package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.api.throwClassNotFound

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
        location.jcLocation.resolve(className) ?: className.throwClassNotFound()
    }
}
