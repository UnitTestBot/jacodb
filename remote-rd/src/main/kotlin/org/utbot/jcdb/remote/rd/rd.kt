package org.utbot.jcdb.remote.rd

import org.utbot.jcdb.CompilationDatabaseSettings
import org.utbot.jcdb.api.CompilationDatabase

fun CompilationDatabaseSettings.rd(port: Int) {
    withHook {
        RDServer(port, it)
    }
}

fun remoteRdDatabase(port: Int): CompilationDatabase {
    return RDClient(port)
}