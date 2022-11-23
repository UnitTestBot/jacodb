package org.utbot.jcdb.impl.http

import org.glassfish.jersey.server.ResourceConfig
import org.springframework.stereotype.Component
import org.utbot.jcdb.impl.http.resources.RootResource

@Component
class JerseyConfig : ResourceConfig() {

    init {
        registerEndpoints()
    }

    private fun registerEndpoints() {
        register(RootResource::class.java)
    }
}