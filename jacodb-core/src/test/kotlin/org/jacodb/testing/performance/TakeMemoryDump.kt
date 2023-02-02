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

package org.jacodb.testing.performance

import kotlinx.coroutines.runBlocking
import org.jacodb.testing.allClasspath
import org.utbot.jacodb.impl.PredefinedPersistenceType
import org.utbot.jacodb.impl.features.InMemoryHierarchy
import org.utbot.jacodb.impl.features.Usages
import org.utbot.jacodb.impl.jacodb
import org.utbot.jacodb.impl.storage.jooq.tables.references.*

fun main() {
    var start = System.currentTimeMillis()
    runBlocking {
        val db = jacodb {
            loadByteCode(allClasspath)
            persistent(
                "d:\\work\\jacodb\\jacodb-inspection.db",
                clearOnStart = true,
                PredefinedPersistenceType.SQLITE
            )
//            persistent("jdbc:postgresql://localhost:5432/jacodb?user=postgres&password=root&reWriteBatchedInserts=false",
//                clearOnStart = true,
//                PredefinedPersistenceType.POSTGRES
//            )
            installFeatures(InMemoryHierarchy, Usages)
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
            println("Processed method params " + it.fetchCount(METHODPARAMETERS))
            println("Processed usages " + it.fetchCount(CALLS))
        }

//        val name = ManagementFactory.getRuntimeMXBean().name
//        val pid = name.split("@")[0]
//        println("Taking memory dump from $pid....")
//        val process = Runtime.getRuntime().exec("jmap -dump:live,format=b,file=db.hprof $pid")
//        process.waitFor()
        println(db)
    }
}