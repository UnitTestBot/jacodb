package org.utbot.jcdb.impl.http.resources

import org.springframework.stereotype.Service
import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.JCDB
import javax.ws.rs.GET
import javax.ws.rs.Path


@Service
@Path("/")
class RootResource(val jcdbSettings: JCDBSettings, val jcdb: JCDB) {


    @GET
    fun getInfo() = JCDBEntity(
        jvmRuntime = JCDBRuntimeEntity(
            version = jcdb.runtimeVersion.majorVersion,
            path = jcdbSettings.jre.absolutePath
        )
    )
}


data class JCDBEntity(val jvmRuntime: JCDBRuntimeEntity)

data class JCDBRuntimeEntity(
    val version: Int,
    val path: String
)