package org.utbot.jcdb.impl

import org.junit.jupiter.api.Test
import org.objectweb.asm.util.CheckClassAdapter
import org.utbot.jcdb.api.JcClassOrInterface
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.methods
import org.utbot.jcdb.impl.cfg.*

class IRTest : BaseTest() {
    companion object : WithDB()

    @Test
    fun `get ir of simple method`() {
        val klass = cp.findClass<IRExamples>()
        testClass(klass)
    }

    @Test
    fun `get ir of algorithms lesson 1`() {
        val klass = cp.findClass<JavaTasks>()
        testClass(klass)
    }

    private fun testClass(klass: JcClassOrInterface) {
        val classNode = klass.bytecode()
        classNode.methods = klass.methods.map {
            val oldBody = it.body()
            println()
            println("Old body: ${oldBody.print()}")
            val instructionList = it.instructionList()
            println("Instruction list: $instructionList")
            val newBody = MethodNodeBuilder(it, instructionList).build()
            println("New body: ${newBody.print()}")
            println()
            newBody
        }
//        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val checker = CheckClassAdapter(classNode)
        classNode.accept(checker)
    }
}
