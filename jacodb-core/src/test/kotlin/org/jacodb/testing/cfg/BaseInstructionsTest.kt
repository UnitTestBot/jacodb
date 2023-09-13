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
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithGlobalDB
import org.junit.jupiter.api.Assertions
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths

abstract class BaseInstructionsTest : BaseTest() {

    companion object : WithGlobalDB()

    private val target = Files.createTempDirectory("jcdb-temp")

    val ext = runBlocking { cp.hierarchyExt() }

    fun runKotlinTest(className: String) {
        val clazz = cp.findClassOrNull(className)
        Assertions.assertNotNull(clazz)

        val javaClazz = testAndLoadClass(clazz!!)
        val clazzInstance = javaClazz.constructors.first().newInstance()
        val method = javaClazz.methods.first { it.name == "box" }
        val res = method.invoke(clazzInstance)
        Assertions.assertEquals("OK", res)
    }


    protected fun testClass(klass: JcClassOrInterface, validateLineNumbers: Boolean = true) {
        testAndLoadClass(klass, false, validateLineNumbers)
    }

    protected fun testAndLoadClass(klass: JcClassOrInterface): Class<*> {
        return testAndLoadClass(klass, true, validateLineNumbers = true)!!
    }

    private fun testAndLoadClass(klass: JcClassOrInterface, loadClass: Boolean, validateLineNumbers: Boolean): Class<*>? {
        try {
            val classNode = klass.asmNode()
            classNode.methods = klass.declaredMethods.filter { it.enclosingClass == klass }.map {
                if (it.isAbstract || it.name.contains("$\$forInline")) {
                    it.asmNode()
                } else {
                    try {
                        val instructionList = it.rawInstList
                        it.instList.forEachIndexed { index, inst ->
                            Assertions.assertEquals(index, inst.location.index, "indexes not matched for $it at $index")
                        }
                        val graph = it.flowGraph()
                        if (!it.enclosingClass.isKotlin) {
                            val methodMsg = "$it should have line number"
                            if (validateLineNumbers) {
                                graph.instructions.forEach {
                                    Assertions.assertTrue(it.lineNumber > 0, methodMsg)
                                }
                            }
                        }
                        graph.applyAndGet(OverridesResolver(ext)) {}
                        JcGraphChecker(it, graph).check()
                        val newBody = MethodNodeBuilder(it, instructionList).build()
                        newBody
                    } catch (e: Throwable) {
                        it.dumpInstructions()
                        throw IllegalStateException("error handling $it", e)
                    }

                }
            }
            val cw = JcDatabaseClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
            val checker = CheckClassAdapter(cw)
            classNode.accept(checker)
            val targetDir = target.resolve(klass.packageName.replace('.', '/'))
            val targetFile = targetDir.resolve("${klass.simpleName}.class").toFile().also {
                it.parentFile?.mkdirs()
            }
            targetFile.writeBytes(cw.toByteArray())
            if (loadClass) {

                val cp = listOf(target.toUri().toURL()) + System.getProperty("java.class.path")
                    .split(File.pathSeparatorChar)
                    .map { Paths.get(it).toUri().toURL() }
                val allClassLoader = URLClassLoader(cp.toTypedArray(), null)
                return allClassLoader.loadClass(klass.name)
            }
        } catch (e: NoClassInClasspathException) {
            System.err.println(e.localizedMessage)
        }
        return null
    }
}