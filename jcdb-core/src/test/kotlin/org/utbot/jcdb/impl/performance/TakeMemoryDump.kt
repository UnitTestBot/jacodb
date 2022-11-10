package org.utbot.jcdb

import kotlinx.coroutines.runBlocking
import org.utbot.jcdb.impl.performance.allIdeaJarsAbsolute
import org.utbot.jcdb.impl.storage.jooq.tables.references.CLASSES
import org.utbot.jcdb.impl.storage.jooq.tables.references.FIELDS
import org.utbot.jcdb.impl.storage.jooq.tables.references.METHODPARAMETERS
import org.utbot.jcdb.impl.storage.jooq.tables.references.METHODS

fun main() {
    var start = System.currentTimeMillis()
    runBlocking {
        val db = jcdb {
            loadByteCode(allIdeaJarsAbsolute)
            persistent("D:\\work\\jcdb\\jcdb-idea.db")
//            installFeatures(Usages)
        }.also {
            println("AWAITING db took ${System.currentTimeMillis() - start}ms")
            start = System.currentTimeMillis()
            it.awaitBackgroundJobs()
            println("AWAITING jobs took ${System.currentTimeMillis() - start}ms")
        }
        db.persistence.read {
            println("Processed classes " + it.fetchCount(CLASSES))
            println("Processed fields " + it.fetchCount(FIELDS))
            println("Processed methods " + it.fetchCount(METHODS))
            println("Processed method params "+ it.fetchCount(METHODPARAMETERS))
        }

//        val name = ManagementFactory.getRuntimeMXBean().name
//        val pid = name.split("@")[0]
//        println("Taking memory dump from $pid....")
//        val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
//        process.waitFor()
        println(db)
    }
}