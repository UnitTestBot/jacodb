package org.utbot.jcdb.impl.performance

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.utbot.jcdb.impl.index.Usages
import org.utbot.jcdb.impl.storage.ClassEntity
import org.utbot.jcdb.impl.storage.FieldEntity
import org.utbot.jcdb.impl.storage.MethodEntity
import org.utbot.jcdb.impl.storage.MethodParameterEntity
import org.utbot.jcdb.jcdb
import java.io.File
import java.lang.management.ManagementFactory

val allClasspath: List<File>
    get() {
        return classpath.map { File(it) }
    }

val guavaLib: File
    get() {
        val guavaUrl = classpath.first { it.contains("guava-31.1-jre.jar") }
        return File(guavaUrl).also {
            Assertions.assertTrue(it.isFile && it.exists())
        }
    }

val allJars: List<File>
    get() {
        return classpath.filter { it.endsWith(".jar") }.map { File(it) }
    }


private val classpath: List<String>
    get() {
        val classpath = System.getProperty("java.class.path")
        return classpath.split(File.pathSeparatorChar).toList()
    }

fun main() {
    var start = System.currentTimeMillis()
    runBlocking {
        val db = jcdb {
//            predefinedDirOrJars = allClasspath
            persistent("D:\\work\\jcdb\\jcdb.db")
            installFeatures(Usages)
        }.also {
            println("AWAITING db took ${System.currentTimeMillis() - start}ms")
            start = System.currentTimeMillis()
            it.awaitBackgroundJobs()
            println("AWAITING jobs took ${System.currentTimeMillis() - start}ms")
        }
        transaction {
            println("Classes " + ClassEntity.count())
            println("Methods " + MethodEntity.count())
            println("Methods params " + MethodParameterEntity.count())
            println("Fields " + FieldEntity.count())
        }

        val name = ManagementFactory.getRuntimeMXBean().name
        val pid = name.split("@")[0]
        println("Taking memory dump from $pid....")
        val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
        process.waitFor()
        println(db)
    }
}
//
//fun main() {
//    println(db)
//    val name = ManagementFactory.getRuntimeMXBean().name
//    val pid = name.split("@")[0]
//    println("Taking memory dump from $pid....")
//    val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
//    process.waitFor()
//}