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

import org.jacodb.testing.allClasspath
import org.jacodb.testing.guavaLib
import org.openjdk.jmh.annotations.*
import soot.G
import soot.PackManager
import soot.Scene
import soot.SootClass
import soot.jimple.JimpleBody
import soot.options.Options
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

val allIdeaJars: List<File>
    get() {
        return allIdeaJarsMain("idea-community", "unzip")
    }

val allIdeaJarsAbsolute: List<File>
    get() {
        return allIdeaJarsMain("jcdb-core", "idea-community", "unzip")
    }

private fun allIdeaJarsMain(vararg paths: String): List<File> {
    val absolutePath = File(".").absolutePath
    val ideaClassPath = Paths.get(absolutePath, *paths)
    require(ideaClassPath.exists())
    val pluginsJars = ideaClassPath.resolve("plugins").toFile().walk().filter { it.extension == "jar" }
    val libsJars = ideaClassPath.resolve("lib").toFile().walk().filter { it.extension == "jar" }
    return (libsJars + pluginsJars).toList()
}

@State(Scope.Benchmark)
@Fork(0)
@Warmup(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.MILLISECONDS)
class SootBenchmarks {

    @Benchmark
    fun jvmRuntime() {
        initSoot(emptyList())
    }

    @Benchmark
    fun jvmRuntimeWithGuava() {
        initSoot(listOf(guavaLib))
    }

    @Benchmark
    fun jvmRuntimeWithAllClasspath() {
        initSoot(allClasspath)
    }

    @Benchmark
    fun jvmRuntimeWithIdeaCommunity() {
        initSoot(allIdeaJars)
    }

    private fun initSoot(files: List<File>) {
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
        }
        Scene.v().loadNecessaryClasses()
        PackManager.v().runPacks()
        val sootClass = Scene.v().getSootClass("java.lang.String")
        sootClass.getMethod("asd").retrieveActiveBody()
        sootClass.setResolvingLevel(SootClass.BODIES)
        sootClass.methods.first().retrieveActiveBody() as JimpleBody
    }

}