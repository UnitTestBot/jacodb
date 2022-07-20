package org.utbot.jcdb.remote.rd

import org.utbot.jcdb.CompilationDatabaseSettings
import org.utbot.jcdb.api.CompilationDatabase
import org.utbot.jcdb.remote.rd.client.RemoteCompilationDatabase

fun CompilationDatabaseSettings.exposeRd(port: Int) {
    withHook {
        RDServer(port, it)
    }
}

fun rdDatabase(port: Int): CompilationDatabase {
    return RemoteCompilationDatabase(port)
}