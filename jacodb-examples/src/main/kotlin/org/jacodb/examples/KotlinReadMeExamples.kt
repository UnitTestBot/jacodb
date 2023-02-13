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

package org.jacodb.examples

import kotlinx.coroutines.runBlocking
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.methods
import org.jacodb.api.ext.toType
import org.jacodb.impl.jacodb
import java.io.File
import kotlin.concurrent.thread

val lib1 = File("1")
val lib2 = File("2")
val buildDir = File("3")


suspend fun findNormalDistribution(): Any {
    val commonsMath32 = File("commons-math3-3.2.jar")
    val commonsMath36 = File("commons-math3-3.6.1.jar")
    val buildDir = File("my-project/build/classes/java/main")
    val database = jacodb {
        useProcessJavaRuntime()
        persistent("/tmp/compilation-db/${System.currentTimeMillis()}") // persist data
    }

    // Let's load these three bytecode locations
    database.load(listOf(commonsMath32, commonsMath36, buildDir))

    // This method just refreshes the libraries inside the database. If there are any changes in libs then
    // the database updates data with the new results.
    database.load(listOf(buildDir))

    // Let's assume that we want to get bytecode info only for `commons-math3` version 3.2.
    val jcClass = database.classpath(listOf(commonsMath32, buildDir))
        .findClass("org.apache.commons.math3.distribution.NormalDistribution")
    println(jcClass.declaredMethods.size)
    println(jcClass.constructors.size)
    println(jcClass.annotations.size)

    // At this point the database read the method bytecode and return the result.
    return jcClass.methods[0].body()
}

suspend fun watchFileChanges() {
    val database = jacodb {
        watchFileSystem()
        useProcessJavaRuntime()
        loadByteCode(listOf(lib1, buildDir))
        persistent("")
    }
}

open class A<T>(val x: T)

class B(x: String) : A<String>(x)

suspend fun typesSubstitution() {
    val db = jacodb {
        loadByteCode(listOf(File("all-classpath")))
    }
    val classpath = db.classpath(listOf(File("all-classpath")))
    val b = classpath.findClass<B>().toType()
    println(b.fields.first { it.name == "x" }.fieldType == classpath.findClass<String>().toType()) // will print `true`
}

suspend fun refresh() {
    val database = jacodb {
        watchFileSystem()
        useProcessJavaRuntime()
        loadByteCode(listOf(lib1, buildDir))
        persistent("")
    }

    val cp = database.classpath(listOf(buildDir))
    database.refresh() // does not affect cp classes

    val cp1 = database.classpath(listOf(buildDir)) // will use new version of compiled results in buildDir
}

suspend fun autoLoad() {
    val database = jacodb {
        loadByteCode(listOf(lib1))
        persistent("")
    }

    val cp = database.classpath(listOf(buildDir)) // database will automatically process buildDir
}


suspend fun threadSafe() {
    val db = jacodb {
        persistent("")
    }

    thread(start = true) {
        runBlocking {
            db.load(listOf(lib1, lib2))
        }
    }

    thread(start = true) {
        runBlocking {
            // maybe created when lib2 or both are not loaded into database
            // but buildDir will be loaded anyway
            val cp = db.classpath(listOf(buildDir))
        }
    }

}