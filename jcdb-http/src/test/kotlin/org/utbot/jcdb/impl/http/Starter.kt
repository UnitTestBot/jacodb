package org.utbot.jcdb.impl.http

import kotlinx.coroutines.runBlocking
import org.utbot.jcdb.impl.features.Builders
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


fun main() {
    runBlocking {
        jcdb {
            useProcessJavaRuntime()
            persistent("D:\\work\\jcdb\\jcdb-http.db")
            loadByteCode(allClasspath)
            installFeatures(Usages, Builders)
            exposeRestApi(8080)
        }
    }
}
