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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import org.utbot.jcdb.impl.features.Builders
import org.utbot.jcdb.impl.features.InMemoryHierarchy
import org.utbot.jcdb.impl.features.Usages
import org.utbot.jcdb.jcdb
import java.io.File
import java.net.URISyntaxException

val jar: File?
    get() {
        try {
            // Get path of the JAR file
            val path = HttpHook::class.java
                .protectionDomain
                .codeSource
                .location
                .toURI()
                .toString()

            // Get name of the JAR file
            return File(path.substring(0, path.lastIndexOf('!')))
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            return null
        }
    }

val allClasspath: List<File>
    get() {
        return classpath.map { File(it) } + listOfNotNull(jar)
    }

private val classpath: List<String>
    get() {
        val classpath = System.getProperty("java.class.path")
        return classpath.split(File.pathSeparatorChar).toList()
    }


class DemoApplication : CliktCommand(name = "JCDB demo application") {
    private val port by option("--port", help = "Changes default port from 8080")
        .default("8080")

    private val refresh by option("--refresh", help = "do explicit refresh of the database")
        .flag(default = true)

    private val location by option("--location", help = "location dor database")
        .default("./jcdb-demo.db")

    private val apiPrefix by option("--api-prefix", help = "api prefix")
        .default("/jcdb-api")

    override fun run() {
        val api = apiPrefix
        runBlocking {
            jcdb {
                useProcessJavaRuntime()
                persistent(location = location)
                loadByteCode(allClasspath)
                installFeatures(Usages, Builders, InMemoryHierarchy)
                exposeRestApi(port.toInt()) {
                    explicitRefresh = refresh
                    apiPrefix = api
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    DemoApplication().main(args)
}
