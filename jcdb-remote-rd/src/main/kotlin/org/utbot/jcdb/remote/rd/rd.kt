package org.utbot.jcdb.remote.rd

import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.JCDB
import org.utbot.jcdb.remote.rd.client.RemoteJCDB

fun JCDBSettings.exposeRd(port: Int) {
    withHook {
        RemoteRdServer(port, it)
    }
}

fun remoteRdClient(port: Int): JCDB {
    return RemoteJCDB(port)
}