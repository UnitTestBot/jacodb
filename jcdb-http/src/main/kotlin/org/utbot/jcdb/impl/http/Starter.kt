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

val allClasspath: List<File>
    get() {
        return classpath.map { File(it) }
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

    private val location by option("--location", help = "location dor database ")
        .default("./jcdb-demo.db")

    override fun run() {
        runBlocking {
            jcdb {
                useProcessJavaRuntime()
                persistent(location = location)
                loadByteCode(allClasspath)
                installFeatures(Usages, Builders, InMemoryHierarchy)
                exposeRestApi(port.toInt()) {
                    explicitRefresh = refresh
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    DemoApplication().main(args)
}
