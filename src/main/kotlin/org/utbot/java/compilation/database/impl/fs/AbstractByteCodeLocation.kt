package org.utbot.java.compilation.database.impl.fs

import org.utbot.java.compilation.database.api.ByteCodeLocation

abstract class AbstractByteCodeLocation : ByteCodeLocation {

    override val id: String by lazy(LazyThreadSafetyMode.NONE) {
        getCurrentId()
    }

    abstract fun getCurrentId(): String

    override fun isChanged(): Boolean {
        return id != getCurrentId()
    }
}