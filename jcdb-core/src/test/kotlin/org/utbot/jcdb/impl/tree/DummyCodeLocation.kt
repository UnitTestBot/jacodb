package org.utbot.jcdb.impl.tree

import org.utbot.jcdb.api.JcByteCodeLocation
import org.utbot.jcdb.api.LocationType
import org.utbot.jcdb.api.RegisteredLocation
import org.utbot.jcdb.impl.storage.longHash
import java.io.File

open class DummyCodeLocation(private val name: String) : JcByteCodeLocation, RegisteredLocation {

    override val id: Long
        get() = name.longHash

    override val hash: String
        get() = name

    override val jcLocation: JcByteCodeLocation
        get() = this


    override val type = LocationType.APP

    override suspend fun classes() = null

    override val jarOrFolder: File
        get() = TODO("Not yet implemented")
    override val path: String
        get() = TODO("")

    override fun isChanged() = false

    override fun createRefreshed() = this

    override suspend fun resolve(classFullName: String) = null

}

