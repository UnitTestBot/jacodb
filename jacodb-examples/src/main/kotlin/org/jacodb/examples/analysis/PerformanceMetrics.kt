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

package org.jacodb.examples.analysis

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassProcessingTask
import org.jacodb.api.JcMethod
import org.jacodb.api.RegisteredLocation
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.hasAnnotation
import org.jacodb.impl.fs.BuildFolderLocation
import org.jacodb.impl.jacodb
import java.io.File
import java.util.concurrent.ConcurrentHashMap


data class User(val login: String)

annotation class HighPerformance
annotation class Slow

class Service {
    private val cache = ConcurrentHashMap<String, User>()

    @Slow
    fun loadAdmin(): User {
        Thread.sleep(10_000)
        return User("admin")
    }

    private fun getOrLoadAdminUser(): User {
        return loadAdmin()
    }

    fun admin(): User {
        return cache.getOrPut("admin") {
            getOrLoadAdminUser()
        }
    }

}

class Controller(private val service: Service) {

    @HighPerformance
    fun drawAdminUser() {
        val user = service.admin()
        println("admin is $user")
    }
}

object NoPath

class AccessPath(private val list: List<String>) {

    companion object {
        fun of(inst: JcInst): AccessPath {
            val method = inst.location.method
            val sourceFile = method.enclosingClass.asmNode().sourceFile
            val ref = "${method.enclosingClass.name}.${method.name}($sourceFile:${inst.location.lineNumber})"
            return AccessPath(listOf(ref))
        }
    }

    fun add(inst: JcInst): AccessPath {
        return join(of(inst))
    }

    fun join(another: AccessPath): AccessPath {
        return AccessPath(list + another.list)
    }

    override fun toString(): String {
        return buildString {
            list.forEach {
                appendLine(it)
            }
        }
    }
}

private val highPerformance = HighPerformance::class.java.name
private val slow = Slow::class.java.name

class HighPerformanceChecker : JcClassProcessingTask {

    private val hasSlowCall = ConcurrentHashMap<JcMethod, Any>()

    override fun shouldProcess(registeredLocation: RegisteredLocation): Boolean {
        return registeredLocation.jcLocation is BuildFolderLocation
    }

    override fun process(clazz: JcClassOrInterface) {
        clazz.declaredMethods
            .filter { it.hasAnnotation(highPerformance) }
            .forEach {
                val path = it.slowCallPath()
                if (path is AccessPath) {
                    println(path)
                }
            }
    }

    private fun JcMethod.slowCallPath(processingMethods: Set<JcMethod> = hashSetOf(this)): Any {
        return hasSlowCall.getOrPut(this) {
            for (inst in instList) {
                val m = inst.callExpr?.method?.method
                if (m != null && !processingMethods.contains(m) && !m.enclosingClass.name.startsWith("java")) {
                    if (m.hasAnnotation(slow)) {
                        return@getOrPut AccessPath.of(inst)
                    } else if (m.isAbstract) {
                        return@getOrPut false
                    }
                    val callPath = m.slowCallPath(processingMethods + m)
                    if (callPath is AccessPath) {
                        return@getOrPut callPath.join(AccessPath.of(inst))
                    }
                }
            }
            NoPath
        }
    }
}

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
        val db = jacodb {
            loadByteCode(allClasspath)
        }
        val cp = db.classpath(allClasspath)
        val checker = HighPerformanceChecker()
        cp.execute(checker)
    }
}