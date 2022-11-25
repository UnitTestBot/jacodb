package org.utbot.jcdb.impl.http

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.api.JCDB

open class HttpHook(private val port: Int, private val jcdb: JCDB, private val settings: JCDBSettings) : Hook {

    private lateinit var applicationContext: ConfigurableApplicationContext
    private lateinit var springApplication: SpringApplication

    override fun afterStart() {
        springApplication = SpringApplication(Application::class.java).also {
            it.setDefaultProperties(
                mapOf(
                    "server.port" to port,
                    "spring.servlet.multipart.max-file-size" to "10MB",
                    "spring.servlet.multipart.max-request-size" to "10MB"
                )
            )
            it.addInitializers(object : ApplicationContextInitializer<ConfigurableApplicationContext> {
                override fun initialize(applicationContext: ConfigurableApplicationContext) {
                    applicationContext.beanFactory.registerSingleton("jcdb", jcdb)
                    applicationContext.beanFactory.registerSingleton("jcdbSettings", settings)
                }
            })
            applicationContext = it.run()
        }
    }

    override fun afterStop() {
        applicationContext.close()
    }
}

fun JCDBSettings.exposeRestApi(port: Int) {
    withHook {
        HttpHook(port, it, this)
    }
}

@SpringBootApplication
open class Application {

}