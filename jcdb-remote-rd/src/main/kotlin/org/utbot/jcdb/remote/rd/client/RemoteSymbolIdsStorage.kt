package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.impl.RdCall
import org.utbot.jcdb.api.SymbolIdsStorage

class RemoteSymbolIdsStorage(
    private val getName: RdCall<Int, String?>,
    private val getId: RdCall<String, Int>
) : SymbolIdsStorage {

    override suspend fun findOrNewId(name: String): Int {
        return getId.startSuspending(name)
    }

    override suspend fun findNameOrNull(id: Int): String? {
        return getName.startSuspending(id)
    }
}