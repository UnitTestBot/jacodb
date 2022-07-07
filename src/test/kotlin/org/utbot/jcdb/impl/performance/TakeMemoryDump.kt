package org.utbot.jcdb.impl.performance

import kotlinx.coroutines.runBlocking
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.index.ReversedUsages
import java.lang.management.ManagementFactory


val db = runBlocking {
    compilationDatabase {
        installIndexes(ReversedUsages)
        useProcessJavaRuntime()
    }.also {
        it.awaitBackgroundJobs()
    }
}


fun main() {
    println(db)
    val name = ManagementFactory.getRuntimeMXBean().name
    val pid = name.split("@")[0]
    println("Taking memory dump from $pid....")
    val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
    process.waitFor()
}