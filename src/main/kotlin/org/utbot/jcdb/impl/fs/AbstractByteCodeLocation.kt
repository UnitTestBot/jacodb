package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ByteCodeLocation
import java.io.File
import java.net.URL

abstract class AbstractByteCodeLocation(protected val file: File) : ByteCodeLocation {

    override val id: String by lazy(LazyThreadSafetyMode.NONE) {
        getCurrentId()
    }

    override val locationURL: URL
        get() = file.toURI().toURL()

    abstract fun getCurrentId(): String

    override fun isChanged(): Boolean {
        return id != getCurrentId()
    }
}