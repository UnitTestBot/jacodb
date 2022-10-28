package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.impl.cfg.JavaTasks
import org.utbot.jcdb.impl.cfg.RawInstListBuilder
import org.utbot.jcdb.impl.cfg.IRExamples

class IRTest : BaseTest() {
    companion object : WithDB()

    @Test
    fun `get ir of simple method`() = runBlocking {
        val a = cp.findClass<IRExamples>()
        a.methods.forEach { jcMethod ->
            println("${jcMethod.enclosingClass.name}::${jcMethod.name}")
            println(RawInstListBuilder().build(jcMethod))
        }
    }

    @Test
    fun `get ir of algorithms lesson 1`() = runBlocking {
        val a = cp.findClass<JavaTasks>()
        a.methods.forEach { jcMethod ->
            println("${jcMethod.enclosingClass.name}::${jcMethod.name}")
            println(RawInstListBuilder().build(jcMethod))
        }
    }
}
