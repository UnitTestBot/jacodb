package org.utbot.jcdb.impl.fs

import org.utbot.jcdb.api.ByteCodeLocation

abstract class AbstractByteCodeLocation : ByteCodeLocation {

    override val id: String by lazy(LazyThreadSafetyMode.NONE) {
        getCurrentId()
    }

    abstract fun getCurrentId(): String

    override fun isChanged(): Boolean {
        return id != getCurrentId()
    }
}