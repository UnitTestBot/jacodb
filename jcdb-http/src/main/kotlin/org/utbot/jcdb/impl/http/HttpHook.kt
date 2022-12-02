/*
 *  Copyright 2022 UnitTestBot contributors (utbot.org)
 * <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
                    "spring.servlet.multipart.max-request-size" to exposureSettings.maxUploadSize,
                    "springdoc.swagger-ui.tagsSorter" to "alpha",
                    "springdoc.swagger-ui.operationsSorter" to "method"
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