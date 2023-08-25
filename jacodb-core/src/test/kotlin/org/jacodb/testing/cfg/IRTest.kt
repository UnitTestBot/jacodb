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
import org.jacodb.api.*
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.*
import org.jacodb.impl.JcClasspathImpl
import org.jacodb.impl.JcDatabaseImpl
import org.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.jacodb.impl.bytecode.JcDatabaseClassWriter
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.cfg.*
import org.jacodb.impl.cfg.util.ExprMapper
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.classpaths.ClasspathCache
import org.jacodb.impl.features.classpaths.StringConcatSimplifier
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.fs.JarLocation
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.guavaLib
import org.jacodb.testing.kotlinxCoroutines
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Paths

class OverridesResolver(
    private val hierarchyExtension: HierarchyExtension
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
            println(
                "Fail on method ${method.enclosingClass.simpleName}#${method.name}(${
                    method.parameters.joinToString(
                        ","
                    ) { it.type.typeName }
                })"
            )
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

    override fun visitExternalJcInst(inst: JcInst) {
    }
}

class IRTest : BaseTest() {

    companion object : WithDB(InMemoryHierarchy, StringConcatSimplifier)

    private val target = Files.createTempDirectory("jcdb-temp")

    private val ext = runBlocking { cp.hierarchyExt() }

    @Test
    fun `get ir of simple method`() {
        testClass(cp.findClass<IRExamples>())
    }

    @Test
    fun `arrays methods`() {
        testClass(cp.findClass<JavaArrays>())
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
        testClass(cp.findClass<JcInstListBuilder>())
        testClass(cp.findClass<JcBlockGraphImpl>())
    }


    @Test
    fun `get ir of guava`() {
        runAlongLib(guavaLib)
    }

    @Test
    fun `iinc should work`() {
        val clazz = cp.findClass<IRExamples>()

        val javaClazz = testClass(clazz) as Class<*>
        val method = javaClazz.methods.first { it.name == "testIinc" }
        val res = method.invoke(null, 0)
        assertEquals(0, res)
    }

    @Test
    fun `iincArrayIntIdx should work`() {
        val clazz = cp.findClass<IRExamples>()

        val javaClazz = testClass(clazz) as Class<*>
        val method = javaClazz.methods.first { it.name == "testIincArrayIntIdx" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(1, 0, 2), res as IntArray)
    }

    @Test
    fun `iincArrayByteIdx should work`() {
        val clazz = cp.findClass<IRExamples>()

        val javaClazz = testClass(clazz) as Class<*>
        val method = javaClazz.methods.first { it.name == "testIincArrayByteIdx" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(1, 0, 2), res as IntArray)
    }

    // todo: make this test green
//    @Test
    fun `get ir of kotlinx-coroutines`() {
//        testClass(cp.findClass("kotlinx.coroutines.ThreadContextElementKt"))
        runAlongLib(kotlinxCoroutines)
    }

    @AfterEach
    fun printStats() {
        cp.features!!.filterIsInstance<ClasspathCache>().forEach {
            it.dumpStats()
        }
    }

    private fun runAlongLib(file: File) {
        println("Run along: ${file.absolutePath}")

        val classes = JarLocation(file, isRuntime = false, object : JavaVersion {
            override val majorVersion: Int
                get() = 8
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
                        assertEquals(index, inst.location.index, "indexes not matched for $it at $index")
                    }
//            println("Instruction list: $instructionList")
                    val graph = it.flowGraph()
                    if (!it.enclosingClass.isKotlin) {
                        graph.instructions.forEach {
                            assertTrue(it.lineNumber > 0, "$it should have line number")
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
