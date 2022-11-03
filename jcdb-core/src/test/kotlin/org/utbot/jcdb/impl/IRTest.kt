package org.utbot.jcdb.impl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.utbot.jcdb.api.ext.findClass
import org.utbot.jcdb.api.methods
import org.utbot.jcdb.impl.cfg.*

class IRTest : BaseTest() {
    companion object : WithDB()

    @Test
    fun `get ir of simple method`() = runBlocking {
        val a = cp.findClass<IRExamples>()
        a.methods.forEach { jcMethod ->
            println("${jcMethod.enclosingClass.name}::${jcMethod.name}")
            val body = runBlocking { jcMethod.body() }
            val instList = RawInstListBuilder(jcMethod, body).build()
            println(instList)
            val mn = MethodNodeBuilder(jcMethod, instList).build()
            println(mn.print())
        }
    }

    @Test
    fun `get ir of algorithms lesson 1`() = runBlocking {
        val a = cp.findClass<JavaTasks>()
        a.methods.forEach { jcMethod ->
            println("${jcMethod.enclosingClass.name}::${jcMethod.name}")
            val body = runBlocking { jcMethod.body() }
            val instList = RawInstListBuilder(jcMethod, body).build()
            println(instList)
            val mn = MethodNodeBuilder(jcMethod, instList).build()
            println(mn.print())
        }
    }
}
