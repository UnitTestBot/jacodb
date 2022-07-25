package org.utbot.jcdb.remote.rd.client

import com.jetbrains.rd.framework.impl.RdCall
import org.utbot.jcdb.api.GlobalIdsStore

class RemoteGlobalIdsStore(
    private val getName: RdCall<Int, String?>,
    private val getId: RdCall<String, Int>
) : GlobalIdsStore {

    override suspend fun getId(name: String): Int {
        return getId.startSuspending(name)
    }

    override suspend fun getName(id: Int): String? {
        return getName.startSuspending(id)
    }
}