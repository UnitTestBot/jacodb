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

import org.jacodb.api.jvm.ext.jvmName
import soot.G
import soot.Scene
import soot.SootClass
import soot.SootMethod
import soot.jimple.Stmt
import soot.options.Options
import soot.tagkit.VisibilityAnnotationTag
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

fun initSoot(files: List<File>) {
    G.reset()
    val options = Options.v()
    val version = 11
    val location = System.getProperty("java.home")

    G.v().initJdk(G.JreInfo(location, version)) // init Soot with the right jdk
    options.apply {
        set_prepend_classpath(true)
        // set true to debug. Disabled because of a bug when two different variables
        // from the source code have the same name in the jimple body.
        setPhaseOption("jb", "use-original-names:false")
        set_soot_classpath(files.filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath })
        set_process_dir(files.filter { it.exists() }.map { it.absolutePath })

        set_src_prec(Options.src_prec_only_class)
        set_keep_line_number(true)
        set_ignore_classpath_errors(true) // gradle/build/resources/main does not exists, but it's not a problem
        set_output_format(Options.output_format_jimple)
        /**
         * In case of Java8, set_full_resolver(true) fails with "soot.SootResolver$SootClassNotFoundException:
         * couldn't find class: javax.crypto.BadPaddingException (is your soot-class-path set properly?)".
         * To cover that, set_allow_phantom_refs(true) is required
         */
        set_allow_phantom_refs(true) // Java8 related
        set_full_resolver(true)
        set_whole_program(true)
    }
    Scene.v().loadNecessaryClasses()
}

private val highPerformanceJvm = highPerformance.jvmName()
private val slowJvm = slow.jvmName()

class SootHighPerformanceChecker {

    private val slowCallCache = ConcurrentHashMap<SootMethod, MaybePath>()

    fun process(clazz: SootClass) {
        clazz.methods
            .filter { it.hasAnnotation(highPerformanceJvm) }
            .forEach {
                val path = it.isSlowCallPath(slowCallCache)
                if (path is AccessPath) {
                    println(path)
                }
            }
    }

    private val SootClass.isSystem: Boolean
        get() {
            return packageName.startsWith("java") || packageName.startsWith("kotlin")
        }

    private fun SootMethod.hasAnnotation(name: String): Boolean {
        return tags.any { it is VisibilityAnnotationTag && it.annotations.any { it.type == name } }
    }

    private fun SootMethod.isSlowCallPath(
        cache: ConcurrentHashMap<SootMethod, MaybePath>,
        processingMethods: Set<SootMethod> = hashSetOf(this)
    ): MaybePath {
        return cache.getOrPut(this) {
            isSlowCallPath(processingMethods)
        }
    }

    private fun SootMethod.isSlowCallPath(methods: Set<SootMethod> = hashSetOf(this)): MaybePath {
        for (stmt in retrieveActiveBody().units.filterIsInstance<Stmt>()) {
            if (!stmt.containsInvokeExpr()) {
                continue
            }
            val method = stmt.invokeExpr.method ?: continue
            if (!methods.contains(method) && !method.declaringClass.isSystem) {
                if (method.hasAnnotation(slowJvm)) {
                    return AccessPath(method, stmt)
                } else if (method.isAbstract) {
                    return NoPath
                }
                val callPath = method.isSlowCallPath(slowCallCache, methods + method)
                if (callPath is AccessPath) {
                    return callPath.join(method, stmt)
                }
            }
        }
        return NoPath
    }
}

fun main() {
    println("Soot tooks: "  + measureTimeMillis {
        initSoot(allClasspath)
        val checker = SootHighPerformanceChecker()
        Scene.v().applicationClasses
            .filter { it.packageName == "org.jacodb.examples.analysis" }
            .stream()
            .parallel()
            .forEach { checker.process(it) }
    } + "ms to finish")
}