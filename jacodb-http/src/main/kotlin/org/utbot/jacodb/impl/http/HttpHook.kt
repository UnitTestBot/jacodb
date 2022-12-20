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

package org.utbot.jacodb.impl.http

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.utbot.jacodb.api.Hook
import org.utbot.jacodb.api.JCDB
import org.utbot.jacodb.impl.JcSettings


open class HttpHook(
    private val port: Int, private val jcdb: JCDB, private val settings: JcSettings,
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

fun JcSettings.exposeRestApi(port: Int, action: DefaultExposureSettings.() -> Unit = {}) = withHook {
    val settings = DefaultExposureSettings().also(action)
    HttpHook(port, it, this, settings)
}

@SpringBootApplication
open class Application {

    @Bean
    open fun springOpenAPI(@Value("\${server.servlet.context-path}") contextPath: String): OpenAPI {
        return OpenAPI()
            .addServersItem(Server().url(contextPath))
            .info(
                Info()
                    .title("JacoDB")
                    .description("`JacoDB` is a pure Java library that allows you to get information about Java bytecode outside the JVM process and to store it in a database. While Java `Reflection` makes it possible to inspect code at runtime, `JacoDB` does the same for bytecode stored in a file system.")
            )
            .externalDocs(
                ExternalDocumentation().url("https://github.com/UnitTestBot/jacodb/wiki")
            )
            .components(
                Components()
                    .addSecuritySchemes(
                        "basicScheme",
                        SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
                    )
            )
    }
}