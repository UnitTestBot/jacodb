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

package org.utbot.jacodb.impl

import com.google.common.collect.ImmutableMultiset
import com.google.gson.internal.JavaVersion
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import org.utbot.jacodb.api.JcClassOrInterface
import org.utbot.jacodb.api.JcClassType
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.JcTypedMethod
import org.utbot.jacodb.api.NoClassInClasspathException
import org.utbot.jacodb.api.TypeName
import org.utbot.jacodb.api.cfg.DefaultJcExprVisitor
import org.utbot.jacodb.api.cfg.DefaultJcInstVisitor
import org.utbot.jacodb.api.cfg.JcAssignInst
import org.utbot.jacodb.api.cfg.JcCallExpr
import org.utbot.jacodb.api.cfg.JcCallInst
import org.utbot.jacodb.api.cfg.JcCatchInst
import org.utbot.jacodb.api.cfg.JcEnterMonitorInst
import org.utbot.jacodb.api.cfg.JcExitMonitorInst
import org.utbot.jacodb.api.cfg.JcExpr
import org.utbot.jacodb.api.cfg.JcGotoInst
import org.utbot.jacodb.api.cfg.JcGraph
import org.utbot.jacodb.api.cfg.JcIfInst
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.cfg.JcInstVisitor
import org.utbot.jacodb.api.cfg.JcReturnInst
import org.utbot.jacodb.api.cfg.JcSpecialCallExpr
import org.utbot.jacodb.api.cfg.JcSwitchInst
import org.utbot.jacodb.api.cfg.JcTerminatingInst
import org.utbot.jacodb.api.cfg.JcThrowInst
import org.utbot.jacodb.api.cfg.JcVirtualCallExpr
import org.utbot.jacodb.api.ext.HierarchyExtension
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.api.ext.isAbstract
import org.utbot.jacodb.api.ext.isAnnotation
import org.utbot.jacodb.api.ext.isInterface
import org.utbot.jacodb.api.ext.methods
import org.utbot.jacodb.api.ext.packageName
import org.utbot.jacodb.api.ext.toType
import org.utbot.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jacodb.impl.bytecode.JcDatabaseClassWriter
import org.utbot.jacodb.impl.bytecode.JcMethodImpl
import org.utbot.jacodb.impl.cfg.BinarySearchTree
import org.utbot.jacodb.impl.cfg.IRExamples
import org.utbot.jacodb.impl.cfg.JavaTasks
import org.utbot.jacodb.impl.cfg.JcBlockGraphImpl
import org.utbot.jacodb.impl.cfg.JcGraphBuilder
import org.utbot.jacodb.impl.cfg.MethodNodeBuilder
import org.utbot.jacodb.impl.cfg.RawInstListBuilder
import org.utbot.jacodb.impl.cfg.Simplifier
import org.utbot.jacodb.impl.cfg.applyAndGet
import org.utbot.jacodb.impl.cfg.util.ExprMapper
import org.utbot.jacodb.impl.features.InMemoryHierarchy
import org.utbot.jacodb.impl.features.hierarchyExt
import org.utbot.jacodb.impl.fs.JarLocation
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files

class OverridesResolver(
    val hierarchyExtension: HierarchyExtension
) : DefaultJcInstVisitor<Sequence<JcTypedMethod>>, DefaultJcExprVisitor<Sequence<JcTypedMethod>> {
    override val defaultInstHandler: (JcInst) -> Sequence<JcTypedMethod>
        get() = { emptySequence() }
    override val defaultExprHandler: (JcExpr) -> Sequence<JcTypedMethod>
        get() = { emptySequence() }

    private fun JcClassType.getMethod(name: String, argTypes: List<TypeName>, returnType: TypeName): JcTypedMethod {
        return methods.firstOrNull { typedMethod ->
            val jcMethod = typedMethod.method
            jcMethod.name == name &&
                    jcMethod.returnType.typeName == returnType.typeName &&
                    jcMethod.parameters.map { param -> param.type.typeName } == argTypes.map { it.typeName }
        } ?: error("Could not find a method with correct signature")
    }

    private val JcMethod.typedMethod: JcTypedMethod
        get() {
            val klass = enclosingClass.toType()
            return klass.getMethod(name, parameters.map { it.type }, returnType)
        }

    override fun visitJcAssignInst(inst: JcAssignInst): Sequence<JcTypedMethod> {
        if (inst.rhv is JcCallExpr) return inst.rhv.accept(this)
        return emptySequence()
    }

    override fun visitJcCallInst(inst: JcCallInst): Sequence<JcTypedMethod> {
        return inst.callExpr.accept(this)
    }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): Sequence<JcTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): Sequence<JcTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

}

class JcGraphChecker(val method: JcMethod, val jcGraph: JcGraph) : JcInstVisitor<Unit> {
    fun check() {
        try {
            jcGraph.entry
        } catch (e: Exception) {
            println("Fail on method ${method.enclosingClass.simpleName}#${method.name}(${method.parameters.joinToString(",") { it.type.typeName }})")
            throw e
        }
        assertTrue(jcGraph.exits.all { it is JcTerminatingInst })

        jcGraph.forEach { it.accept(this) }

        checkBlocks()
    }

    fun checkBlocks() {
        val blockGraph = jcGraph.blockGraph()

        val entry = assertDoesNotThrow { blockGraph.entry }
        for (block in blockGraph) {
            if (block != entry) {
                when (jcGraph.inst(block.start)) {
                    is JcCatchInst -> {
                        assertTrue(blockGraph.predecessors(block).isEmpty())
                        assertTrue(blockGraph.throwers(block).isNotEmpty())
                    }

                    else -> {
                        assertTrue(blockGraph.predecessors(block).isNotEmpty())
                        assertTrue(blockGraph.throwers(block).isEmpty())
                    }
                }
            }
            assertDoesNotThrow { blockGraph.instructions(block).map { jcGraph.catchers(it) }.toSet().single() }
            if (jcGraph.inst(block.end) !is JcTerminatingInst) {
                assertTrue(blockGraph.successors(block).isNotEmpty())
            }
        }
    }

    override fun visitJcAssignInst(inst: JcAssignInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jcGraph.next(inst)), jcGraph.successors(inst))
        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

    override fun visitJcEnterMonitorInst(inst: JcEnterMonitorInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jcGraph.next(inst)), jcGraph.successors(inst))
        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

    override fun visitJcExitMonitorInst(inst: JcExitMonitorInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jcGraph.next(inst)), jcGraph.successors(inst))
        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

    override fun visitJcCallInst(inst: JcCallInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jcGraph.next(inst)), jcGraph.successors(inst))
        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

    override fun visitJcReturnInst(inst: JcReturnInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(emptySet<JcInst>(), jcGraph.successors(inst))
        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

    override fun visitJcThrowInst(inst: JcThrowInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(emptySet<JcInst>(), jcGraph.successors(inst))
        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

    override fun visitJcCatchInst(inst: JcCatchInst) {
        assertEquals(emptySet<JcInst>(), jcGraph.predecessors(inst))
        assertTrue(jcGraph.successors(inst).isNotEmpty())
        assertTrue(jcGraph.throwers(inst).all { thrower ->
            inst in jcGraph.catchers(thrower)
        })
    }

    override fun visitJcGotoInst(inst: JcGotoInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(setOf(jcGraph.inst(inst.target)), jcGraph.successors(inst))
        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

    override fun visitJcIfInst(inst: JcIfInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(
            setOf(
                jcGraph.inst(inst.trueBranch),
                jcGraph.inst(inst.falseBranch)
            ),
            jcGraph.successors(inst)
        )
        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

    override fun visitJcSwitchInst(inst: JcSwitchInst) {
        if (inst != jcGraph.entry) {
            assertTrue(jcGraph.predecessors(inst).isNotEmpty())
        }
        assertEquals(
            inst.branches.values.map { jcGraph.inst(it) }.toSet() + jcGraph.inst(inst.default),
            jcGraph.successors(inst)
        )

        assertTrue(jcGraph.catchers(inst).all { catch ->
            inst in catch.throwers.map { thrower -> jcGraph.inst(thrower) }.toSet()
        })
        assertTrue(jcGraph.throwers(inst).isEmpty())
    }

}

class IRTest : BaseTest() {

    private val target = Files.createTempDirectory("jcdb-temp")

    companion object : WithDB(InMemoryHierarchy)

    private val ext = runBlocking { cp.hierarchyExt() }

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
        testClass(cp.findClass<JcDatabaseImpl>())
        testClass(cp.findClass<ExprMapper>())
        testClass(cp.findClass<JcGraphBuilder>())
        testClass(cp.findClass<JcBlockGraphImpl>())
    }

    @Test
    fun `get ir of jackson`() {
        allClasspath.filter { it.name.contains("jackson") }.forEach {
            runAlongLib(it)
        }
    }

    @Test
    fun `test fail`() {
        testClass(cp.findClass<ImmutableMultiset<*>>())
    }

    @Test
    fun `get ir of guava`() {
        runAlongLib(guavaLib)
    }

    private fun runAlongLib(file: File) {
        println("Run along: ${file.absolutePath}")

        val classes = JarLocation(file, isRuntime = false, object : org.utbot.jacodb.api.JavaVersion {
            override val majorVersion: Int
                get() = JavaVersion.getMajorJavaVersion()
        }).classes
        assertNotNull(classes)
        classes!!.forEach {
            val clazz = cp.findClass(it.key)
            if (!clazz.isAnnotation && !clazz.isInterface) {
                println("Testing class: ${it.key}")
                testClass(clazz)
            }
        }
    }


    private fun testClass(klass: JcClassOrInterface) = try {
        val classNode = klass.bytecode()
        classNode.methods = klass.methods.filter { it.enclosingClass == klass }.map {
            if (it.isAbstract) {
                it.body()
            } else {
//            val oldBody = it.body()
//            println()
//            println("Old body: ${oldBody.print()}")
                val instructionList = it.instructionList()
//            println("Instruction list: $instructionList")
                val graph = instructionList.graph(it)
                graph.applyAndGet(OverridesResolver(ext)) {}
                JcGraphChecker(it, graph).check()
//            println("Graph: $graph")
//            graph.view("/usr/bin/dot", "/usr/bin/firefox", false)
//            graph.blockGraph().view("/usr/bin/dot", "/usr/bin/firefox")
                val newBody = MethodNodeBuilder(it, instructionList).build()
//            println("New body: ${newBody.print()}")
//            println()
                newBody
            }
        }
        val cw = JcDatabaseClassWriter(cp, ClassWriter.COMPUTE_FRAMES)
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
