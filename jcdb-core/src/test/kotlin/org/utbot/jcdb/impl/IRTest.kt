package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.NoClassInClasspathException
import org.utbot.jcdb.api.cfg.ext.view
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.methods
import org.utbot.jcdb.api.packageName
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.bytecode.JcMethodImpl
import org.utbot.jcdb.impl.cfg.*
import org.utbot.jcdb.impl.cfg.util.ExprMapper
import org.utbot.jcdb.impl.index.hierarchyExt
import java.net.URLClassLoader
import java.nio.file.Files

class IRTest : BaseTest() {
    val target = Files.createTempDirectory("jcdb-temp")
    val hierarchy = runBlocking { cp.hierarchyExt() }

    companion object : WithDB()

    @Test
    fun `get ir of simple method`() {
        testClass(cp.findClass<IRExamples>())
    }

    @Test
    fun `get ir of algorithms lesson 1`() {
        testClass(cp.findClass<JavaTasks>())
    }

    @Test
    fun `get ir of binary search tree`() {
        testClass(cp.findClass<BinarySearchTree<*>>())
        testClass(cp.findClass<BinarySearchTree<*>.BinarySearchTreeIterator>())
    }

    @Test
    fun `get ir of self`() {
        testClass(cp.findClass<JcClasspathImpl>())
        testClass(cp.findClass<JcClassOrInterfaceImpl>())
        testClass(cp.findClass<JcMethodImpl>())
        testClass(cp.findClass<RawInstListBuilder>())
        testClass(cp.findClass<Simplifier>())
        testClass(cp.findClass<JCDBImpl>())
        testClass(cp.findClass<ExprMapper>())
    }

    private fun testClass(klass: JcClassOrInterface) = try {
        val classNode = klass.bytecode()
        classNode.methods = klass.methods.filter { it.enclosingClass == klass }.map {
            val oldBody = it.body()
            println()
            println("Old body: ${oldBody.print()}")
            val instructionList = it.instructionList(cp)
            println("Instruction list: $instructionList")
            val graph = instructionList.graph(cp, hierarchy, it)
            println("Graph: $graph")
            graph.view("/usr/bin/dot", "/usr/bin/firefox", false)
            graph.blockGraph().view("/usr/bin/dot", "/usr/bin/firefox")
            assertDoesNotThrow { graph.entry }
            assertDoesNotThrow { graph.blockGraph().entry }
            val newBody = MethodNodeBuilder(it, instructionList).build()
            println("New body: ${newBody.print()}")
            println()
            newBody
        }
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val checker = CheckClassAdapter(classNode)
        classNode.accept(checker)
        val targetDir = target.resolve(klass.packageName.replace('.', '/'))
        val targetFile = targetDir.resolve("${klass.simpleName}.class").toFile().also {
            it.parentFile?.mkdirs()
        }
        targetFile.writeBytes(cw.toByteArray())

        val classloader = URLClassLoader(arrayOf(target.toUri().toURL()))
        classloader.loadClass(klass.name)
    } catch (e: NoClassInClasspathException) {
        System.err.println(e.localizedMessage)
    }
}
