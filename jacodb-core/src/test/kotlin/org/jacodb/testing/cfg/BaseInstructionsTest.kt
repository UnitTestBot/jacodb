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

package org.jacodb.testing.cfg

import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.NoClassInClasspathException
import org.jacodb.api.cfg.applyAndGet
import org.jacodb.api.ext.isKotlin
import org.jacodb.api.ext.packageName
import org.jacodb.impl.bytecode.JcDatabaseClassWriter
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.classpaths.StringConcatSimplifier
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths

abstract class BaseInstructionsTest: BaseTest() {

    companion object : WithDB(InMemoryHierarchy, StringConcatSimplifier)

    private val target = Files.createTempDirectory("jcdb-temp")

    val ext = runBlocking { cp.hierarchyExt() }

    protected fun testClass(klass: JcClassOrInterface) = try {
        val classNode = klass.asmNode()
        classNode.methods = klass.declaredMethods.filter { it.enclosingClass == klass }.map {
            if (it.isAbstract || it.name.contains("$\$forInline")) {
                it.asmNode()
            } else {
                try {
//            val oldBody = it.body()
//            println()
//            println("Old body: ${oldBody.print()}")
                    val instructionList = it.rawInstList
                    it.instList.forEachIndexed { index, inst ->
                        Assertions.assertEquals(index, inst.location.index, "indexes not matched for $it at $index")
                    }
//            println("Instruction list: $instructionList")
                    val graph = it.flowGraph()
                    if (!it.enclosingClass.isKotlin) {
                        graph.instructions.forEach {
                            Assertions.assertTrue(it.lineNumber > 0, "$it should have line number")
                        }
                    }
                    graph.applyAndGet(OverridesResolver(ext)) {}
                    JcGraphChecker(it, graph).check()
//            println("Graph: $graph")
//            graph.view("/usr/bin/dot", "/usr/bin/firefox", false)
//            graph.blockGraph().view("/usr/bin/dot", "/usr/bin/firefox")
                    val newBody = MethodNodeBuilder(it, instructionList).build()
//            println("New body: ${newBody.print()}")
//            println()
                    newBody
                } catch (e: Exception) {
                    throw IllegalStateException("error handling $it", e)
                }

            }
        }
        val cw = JcDatabaseClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
        val checker = CheckClassAdapter(cw)
        try {
            classNode.accept(checker)
        } catch (ex: Throwable) {
            println(ex)
        }
        val targetDir = target.resolve(klass.packageName.replace('.', '/'))
        val targetFile = targetDir.resolve("${klass.simpleName}.class").toFile().also {
            it.parentFile?.mkdirs()
        }
        targetFile.writeBytes(cw.toByteArray())

        val cp = listOf(target.toUri().toURL()) + System.getProperty("java.class.path").split(File.pathSeparatorChar)
            .map { Paths.get(it).toUri().toURL() }
        val allClassLoader = URLClassLoader(cp.toTypedArray(), null)
        allClassLoader.loadClass(klass.name)
    } catch (e: NoClassInClasspathException) {
        System.err.println(e.localizedMessage)
    }
}