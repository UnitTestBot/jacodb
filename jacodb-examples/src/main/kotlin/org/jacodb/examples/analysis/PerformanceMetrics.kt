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
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassProcessingTask
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.cfg.callExpr
import org.jacodb.api.jvm.ext.hasAnnotation
import org.jacodb.impl.fs.BuildFolderLocation
import org.jacodb.impl.jacodb
import soot.SootMethod
import soot.jimple.Stmt
import soot.tagkit.LineNumberTag
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis


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

private fun SootMethod.ref(stmt: Stmt): String {
    return "${declaringClass.name}.${name}:${stmt.lineNumber})"
}

private val Stmt.lineNumber: String
    get() {
        return tags.filterIsInstance<LineNumberTag>().firstOrNull()?.lineNumber?.toString() ?: "unknown"
    }


interface MaybePath

object NoPath : MaybePath

class AccessPath(private val list: List<String>) : MaybePath {

    constructor(inst: JcInst) : this(listOf(inst.ref))
    constructor(method: SootMethod, stmt: Stmt) : this(listOf(method.ref(stmt)))

    fun add(inst: JcInst): AccessPath {
        return join(AccessPath(inst))
    }

    fun join(another: AccessPath): AccessPath {
        return AccessPath(list + another.list)
    }

    fun join(another: JcInst): AccessPath {
        return AccessPath(list + another.ref)
    }

    fun join(method: SootMethod, stmt: Stmt): AccessPath {
        return AccessPath(list + method.ref(stmt))
    }

    override fun toString(): String {
        return buildString {
            append("Performance issue found:")
            list.forEach {
                appendLine()
                append("\t")
                append(it)
            }
        }
    }
}

internal val highPerformance = HighPerformance::class.java.name
internal val slow = Slow::class.java.name

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
    println("JacoDB tooks: " + measureTimeMillis {
        runBlocking {
            val db = jacodb {
                loadByteCode(allClasspath)
            }
            val cp = db.classpath(allClasspath)
            val checker = HighPerformanceChecker()
            cp.execute(checker)
        }
    } + "ms to finish")

}