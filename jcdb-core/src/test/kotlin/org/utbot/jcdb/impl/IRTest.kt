package org.utbot.jcdb.impl

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.JcClassType
import org.utbot.jcdb.api.JcMethod
import org.utbot.jcdb.api.JcTypedMethod
import org.utbot.jcdb.api.NoClassInClasspathException
import org.utbot.jcdb.api.TypeName
import org.utbot.jcdb.api.cfg.DefaultJcExprVisitor
import org.utbot.jcdb.api.cfg.DefaultJcInstVisitor
import org.utbot.jcdb.api.cfg.JcAssignInst
import org.utbot.jcdb.api.cfg.JcCallExpr
import org.utbot.jcdb.api.cfg.JcCallInst
import org.utbot.jcdb.api.cfg.JcExpr
import org.utbot.jcdb.api.cfg.JcInst
import org.utbot.jcdb.api.cfg.JcSpecialCallExpr
import org.utbot.jcdb.api.cfg.JcVirtualCallExpr
import org.utbot.jcdb.api.ext.HierarchyExtension
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.methods
import org.utbot.jcdb.api.packageName
import org.utbot.jcdb.api.toType
import org.utbot.jcdb.impl.bytecode.JcClassOrInterfaceImpl
import org.utbot.jcdb.impl.bytecode.JcMethodImpl
import org.utbot.jcdb.impl.cfg.BinarySearchTree
import org.utbot.jcdb.impl.cfg.IRExamples
import org.utbot.jcdb.impl.cfg.JavaTasks
import org.utbot.jcdb.impl.cfg.MethodNodeBuilder
import org.utbot.jcdb.impl.cfg.RawInstListBuilder
import org.utbot.jcdb.impl.cfg.Simplifier
import org.utbot.jcdb.impl.cfg.print
import org.utbot.jcdb.impl.cfg.util.ExprMapper
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
