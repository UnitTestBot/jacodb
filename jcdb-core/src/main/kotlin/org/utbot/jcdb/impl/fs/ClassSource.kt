package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ClassSource
import org.utbot.jcdb.api.RegisteredLocation

class ClassSourceImpl(
    override val location: RegisteredLocation,
    override val className: String,
    override val byteCode: ByteArray
): ClassSource