package org.utbot.jcdb.impl.tree

import org.utbot.jcdb.api.ByteCodeLocation
import org.utbot.jcdb.api.LocationScope
import org.utbot.jcdb.impl.fs.ByteCodeLoaderImpl

open class DummyCodeLocation(override val id: String) : ByteCodeLocation {

    override val scope: LocationScope
        get() = LocationScope.APP

    override val path: String
        get() = TODO("")

    override fun isChanged() = false

    override fun createRefreshed() = this

    override suspend fun resolve(classFullName: String) = null

    override suspend fun loader() = ByteCodeLoaderImpl(this, emptyMap())
}

