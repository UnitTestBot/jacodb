package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.JcByteCodeLocation
import java.io.File


abstract class AbstractByteCodeLocation(override val jarOrFolder: File) : JcByteCodeLocation {

    override val path: String
        get() = jarOrFolder.absolutePath

    abstract fun currentHash(): String

    override fun isChanged(): Boolean {
        return hash != currentHash()
    }

}