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

import org.jacodb.api.JavaVersion
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.JcTypedMethod
import org.jacodb.api.TypeName
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcCatchInst
import org.jacodb.api.cfg.JcEnterMonitorInst
import org.jacodb.api.cfg.JcExitMonitorInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcExprVisitor
import org.jacodb.api.cfg.JcGotoInst
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstVisitor
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcSwitchInst
import org.jacodb.api.cfg.JcTerminatingInst
import org.jacodb.api.cfg.JcThrowInst
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.toType
import org.jacodb.impl.JcClasspathImpl
import org.jacodb.impl.JcDatabaseImpl
import org.jacodb.impl.bytecode.JcClassOrInterfaceImpl
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.cfg.JcBlockGraphImpl
import org.jacodb.impl.cfg.JcInstListBuilder
import org.jacodb.impl.cfg.RawInstListBuilder
import org.jacodb.impl.cfg.Simplifier
import org.jacodb.impl.cfg.util.ExprMapper
import org.jacodb.impl.features.classpaths.ClasspathCache
import org.jacodb.impl.features.classpaths.StringConcatSimplifier
import org.jacodb.impl.fs.JarLocation
import org.jacodb.testing.WithDB
import org.jacodb.testing.asmLib
import org.jacodb.testing.guavaLib
import org.jacodb.testing.kotlinStdLib
import org.jacodb.testing.kotlinxCoroutines
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File

class OverridesResolver(
    private val hierarchyExtension: HierarchyExtension,
) : JcExprVisitor.Default<Sequence<JcTypedMethod>>,
    JcInstVisitor.Default<Sequence<JcTypedMethod>> {

    override fun defaultVisitJcExpr(expr: JcExpr): Sequence<JcTypedMethod> {
        return emptySequence()
    }

    override fun defaultVisitJcInst(inst: JcInst): Sequence<JcTypedMethod> {
        return emptySequence()
    }

    private fun JcClassType.getMethod(
        name: String,
        argTypes: List<TypeName>,
        returnType: TypeName,
    ): JcTypedMethod {
        return methods.firstOrNull { typedMethod ->
            val jcMethod = typedMethod.method
            jcMethod.name == name
                && jcMethod.returnType.typeName == returnType.typeName
                && jcMethod.parameters.map { param -> param.type.typeName } == argTypes.map { it.typeName }
        } ?: error("Could not find a method with correct signature")
    }

    private val JcMethod.typedMethod: JcTypedMethod
        get() {
            val klass = enclosingClass.toType()
            return klass.getMethod(name, parameters.map { it.type }, returnType)
        }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): Sequence<JcTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): Sequence<JcTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

    override fun visitJcAssignInst(inst: JcAssignInst): Sequence<JcTypedMethod> {
        if (inst.rhv is JcCallExpr) return inst.rhv.accept(this)
        return emptySequence()
    }

    override fun visitJcCallInst(inst: JcCallInst): Sequence<JcTypedMethod> {
        return inst.callExpr.accept(this)
    }

}

class JcGraphChecker(
    val method: JcMethod,
    val jcGraph: JcGraph,
) : JcInstVisitor<Unit> {

    fun check() {
        try {
            jcGraph.entry
        } catch (e: Exception) {
            println(
                "Fail on method ${method.enclosingClass.simpleName}#${method.name}(${
                    method.parameters.joinToString(",") { it.type.typeName }
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

    override fun visitExternalJcInst(inst: JcInst) {
        // Do nothing
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

class IRTest : BaseInstructionsTest() {

    companion object : WithDB(StringConcatSimplifier)

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
    fun `get ir of random class`() {
        val clazz = cp.findClass("kotlinx.coroutines.channels.ChannelsKt__DeprecatedKt\$filterIndexed\$1")
        val method = clazz.declaredMethods.first { it.name == "invokeSuspend" }
        JcGraphChecker(method, method.flowGraph()).check()
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
    fun `get ir of asm`() {
        runAlongLib(asmLib, muteGraphChecker = true)
    }

    @Test
    fun `get ir of kotlinx-coroutines`() {
        runAlongLib(kotlinxCoroutines, false)
    }

    @Test
    fun `get ir of kotlin stdlib`() {
        runAlongLib(kotlinStdLib, false)
    }

    @AfterEach
    fun printStats() {
        cp.features!!.filterIsInstance<ClasspathCache>().forEach {
            it.dumpStats()
        }
    }

    private fun runAlongLib(file: File, validateLineNumbers: Boolean = true, muteGraphChecker: Boolean = false) {
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
                testClass(clazz, validateLineNumbers, muteGraphChecker)
            }
        }
    }

}
