package org.utbot.jcdb.impl.http

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.utbot.jcdb.JCDBSettings
import org.utbot.jcdb.api.Hook
import org.utbot.jcdb.api.JCDB

open class HttpHook(
    private val port: Int, private val jcdb: JCDB, private val settings: JCDBSettings,
    private val exposureSettings: DefaultExposureSettings
) : Hook {

    private lateinit var applicationContext: ConfigurableApplicationContext
    private lateinit var springApplication: SpringApplication

    override suspend fun afterStart() {
        if (exposureSettings.explicitRefresh) {
            jcdb.refresh()
        }
        springApplication = SpringApplication(Application::class.java).also {
            it.setDefaultProperties(
                mapOf(
                    "server.port" to port,
                    "server.servlet.contextPath" to exposureSettings.apiPrefix,
                    "spring.servlet.multipart.max-file-size" to exposureSettings.maxUploadSize,
                    "spring.servlet.multipart.max-request-size" to exposureSettings.maxUploadSize
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

class DefaultExposureSettings {
    var explicitRefresh: Boolean = true
    var maxUploadSize: String = "10MB"
    var apiPrefix: String = "/jcdb-api"
}

fun JCDBSettings.exposeRestApi(port: Int, action: DefaultExposureSettings.() -> Unit = {}) = withHook {
    val settings = DefaultExposureSettings().also(action)
    HttpHook(port, it, this, settings)
}

@SpringBootApplication
open class Application