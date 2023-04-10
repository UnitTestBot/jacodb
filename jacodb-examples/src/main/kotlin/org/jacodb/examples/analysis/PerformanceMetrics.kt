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
    fun recalculateIndexes() {
        Thread.sleep(10_000)
    }

    fun admin() = cache.getOrPut("admin") {
        recalculateIndexes()
        User("admin")
    }

}

class Controller(private val service: Service) {

    @HighPerformance
    fun getAdminUser() {
        val user = service.admin()
        println("admin is $user")
    }
}

private val JcInst.ref: String
    get() {
        val method = location.method
        val sourceFile = method.enclosingClass.asmNode().sourceFile
        return "${method.enclosingClass.name}.${method.name}($sourceFile:${location.lineNumber})"
    }

interface MaybePath

object NoPath : MaybePath

class AccessPath(private val list: List<String>) : MaybePath {

    constructor(inst: JcInst) : this(listOf(inst.ref))

    fun add(inst: JcInst): AccessPath {
        return join(AccessPath(inst))
    }

    fun join(another: AccessPath): AccessPath {
        return AccessPath(list + another.list)
    }

    fun join(another: JcInst): AccessPath {
        return AccessPath(list + another.ref)
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

    private val slowCallCache = ConcurrentHashMap<JcMethod, MaybePath>()

    override fun shouldProcess(registeredLocation: RegisteredLocation): Boolean {
        return registeredLocation.jcLocation is BuildFolderLocation
    }

    override fun process(clazz: JcClassOrInterface) {
        clazz.declaredMethods
            .filter { it.hasAnnotation(highPerformance) }
            .forEach {
                val path = it.isSlowCallPath(slowCallCache)
                if (path is AccessPath) {
                    println(path)
                }
            }
    }

    private val JcClassOrInterface.isSystem: Boolean
        get() {
            return name.startsWith("java") || name.startsWith("kotlin")
        }

    private fun JcMethod.isSlowCallPath(
        cache: ConcurrentHashMap<JcMethod, MaybePath>,
        processingMethods: Set<JcMethod> = hashSetOf(this)
    ): MaybePath {
        return cache.getOrPut(this) {
            isSlowCallPath(processingMethods)
        }
    }

    private fun JcMethod.isSlowCallPath(methods: Set<JcMethod> = hashSetOf(this)): MaybePath {
        for (inst in instList) {
            val method = inst.callExpr?.method?.method ?: continue
            if (!methods.contains(method) && !method.enclosingClass.isSystem) {
                if (method.hasAnnotation(slow)) {
                    return AccessPath(inst)
                } else if (method.isAbstract) {
                    return NoPath
                }
                val callPath = method.isSlowCallPath(slowCallCache, methods + method)
                if (callPath is AccessPath) {
                    return callPath.join(inst)
                }
            }
        }
        return NoPath
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