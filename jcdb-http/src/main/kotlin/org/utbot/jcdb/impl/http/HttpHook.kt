package org.utbot.jcdb.impl.http

import org.springframework.context.ConfigurableApplicationContext
import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.api.JCDB

class HttpHook(private val jcdb: JCDB, private val settings: JCDBSettings) : Hook {

    private lateinit var applicationContext: ConfigurableApplicationContext

    override fun afterStart() {
        applicationContext.beanFactory.registerSingleton("jcdb", jcdb)
        applicationContext.beanFactory.registerSingleton("jcdbSettings", settings)
    }

    override fun afterStop() {
        applicationContext.close()
    }
}

fun JCDBSettings.exposeRestApi(port: Int) {


}