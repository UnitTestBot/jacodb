package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ByteCodeLocation
import java.io.File

abstract class AbstractByteCodeLocation(protected val file: File) : ByteCodeLocation {

    override val id: String by lazy(LazyThreadSafetyMode.NONE) {
        getCurrentId()
    }

    override val path: String
        get() = file.absolutePath

    abstract fun getCurrentId(): String

    override fun isChanged(): Boolean {
        return id != getCurrentId()
    }
}