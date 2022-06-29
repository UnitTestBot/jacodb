package org.utbot.jcdb.impl.performance

import kotlinx.coroutines.runBlocking
import org.utbot.jcdb.compilationDatabase
import org.utbot.jcdb.impl.index.ReversedUsagesIndex
import java.lang.management.ManagementFactory


val db = runBlocking {
    compilationDatabase {
        installIndexes(ReversedUsagesIndex)
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