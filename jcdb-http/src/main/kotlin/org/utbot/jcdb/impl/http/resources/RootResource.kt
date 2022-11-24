package org.utbot.jcdb.impl.http.resources

import org.springframework.stereotype.Service
import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.JCDB
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType


@Service
@Produces(MediaType.APPLICATION_JSON)
@Path("/")
class RootResource(val jcdbSettings: JCDBSettings, val jcdb: JCDB) {

    @GET
    fun getInfo() = JCDBEntity(
        jvmRuntime = JCDBRuntimeEntity(
            version = jcdb.runtimeVersion.majorVersion,
            path = jcdbSettings.jre.absolutePath
        ),
        locations = jcdb.locations.map {
            LocationEntity(
                id = it.id,
                path = it.path,
                runtime = it.runtime
            )
        }
    )
}


data class JCDBEntity(
    val jvmRuntime: JCDBRuntimeEntity,
    val locations: List<LocationEntity>
)

data class JCDBRuntimeEntity(
    val version: Int,
    val path: String
)

data class LocationEntity(val id: Long, val path: String, val runtime: Boolean)