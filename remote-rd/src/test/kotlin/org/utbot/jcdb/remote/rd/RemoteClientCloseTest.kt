package org.utbot.jcdb.remote.rd

import com.jetbrains.rd.framework.util.NetUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.utbot.jcdb.impl.LibrariesMixin
import org.utbot.jcdb.jcdb

class RemoteClientCloseTest : LibrariesMixin {

    private val port = NetUtils.findFreePort(8080)

    @Test
    fun `closing client implies closing server`() = runBlocking {
        jcdb {
            useProcessJavaRuntime()
            exposeRd(port)
        }
        val remoteDB = remoteRdClient(port)
        remoteDB.close()
        delay(1000)
        assertTrue(NetUtils.findFreePort(port) == port)
    }

    @Test
    fun `closing db is not preventing stopping client`() = runBlocking {
        val db = jcdb {
            useProcessJavaRuntime()
            exposeRd(port)
        }
        val remoteDB = remoteRdClient(port)
        remoteDB.close()
        db.close()
        assertTrue(NetUtils.findFreePort(port) == port)
    }

}