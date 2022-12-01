package org.utbot.jcdb.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import org.utbot.jcdb.api.*
import org.utbot.jcdb.api.cfg.*
import org.utbot.jcdb.api.cfg.ext.view
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.bytecode.JcMethodImpl
import org.utbot.jcdb.impl.cfg.*
import org.utbot.jcdb.impl.cfg.util.ExprMapper
import java.net.URLClassLoader
import java.nio.file.Files

class OverridesResolver(
    val hierarchyExtension: HierarchyExtension
) : DefaultJcInstVisitor<List<JcTypedMethod>>, DefaultJcExprVisitor<List<JcTypedMethod>> {
    override val defaultInstHandler: (JcInst) -> List<JcTypedMethod>
        get() = { emptyList() }
    override val defaultExprHandler: (JcExpr) -> List<JcTypedMethod>
        get() = { emptyList() }

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

    override fun visitJcAssignInst(inst: JcAssignInst): List<JcTypedMethod> {
        if (inst.rhv is JcCallExpr) return inst.rhv.accept(this)
        return emptyList()
    }

    override fun visitJcCallInst(inst: JcCallInst): List<JcTypedMethod> {
        return inst.callExpr.accept(this)
    }

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): List<JcTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): List<JcTypedMethod> {
        return hierarchyExtension.findOverrides(expr.method.method).map { it.typedMethod }
    }

}

class IRTest : BaseTest() {
    val target = Files.createTempDirectory("jcdb-temp")

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
            val graph = instructionList.graph(cp, it)
            println("Graph: $graph")
//            graph.view("/usr/bin/dot", "/usr/bin/firefox", false)
//            graph.blockGraph().view("/usr/bin/dot", "/usr/bin/firefox")
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
