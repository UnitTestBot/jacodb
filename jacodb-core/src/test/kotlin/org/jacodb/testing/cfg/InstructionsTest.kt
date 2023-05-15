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

import com.sun.mail.imap.IMAPMessage
import kotlinx.coroutines.runBlocking
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassProcessingTask
import org.jacodb.api.JcMethod
import org.jacodb.api.RegisteredLocation
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.locals
import org.jacodb.api.cfg.values
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.cfg.locals
import org.jacodb.api.ext.cfg.values
import org.jacodb.api.ext.findClass
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.util.concurrent.ConcurrentHashMap
import javax.activation.DataHandler


class InstructionsTest : BaseTest() {

    companion object : WithDB()

    @Test
    fun `assign inst`() {
        val clazz = cp.findClass<SimpleAlias1>()
        val method = clazz.declaredMethods.first { it.name == "main" }
        val bench = cp.findClass<Benchmark>()
        val use = bench.declaredMethods.first { it.name == "use" }
        val instructions = method.instList.instructions
        val firstUse = instructions.indexOfFirst { it.callExpr?.method?.method == use }
        val assign = instructions[firstUse + 1] as JcAssignInst
        assertEquals("%4", (assign.lhv as JcLocalVar).name)
        assertEquals("%1", (assign.rhv as JcLocalVar).name)
    }

    @Test
    fun `null ref test`() {
        val clazz = cp.findClass<DataHandler>()
        clazz.declaredMethods.first { it.name == "writeTo" }.flowGraph()
    }

    @Test
    fun `Protocol test`() {
        val clazz = cp.findClass("com.sun.mail.pop3.Protocol")
        val method = clazz.declaredMethods.first { it.name == "<init>" }
        method.flowGraph()
    }

    @Test
    fun `SMTPSaslAuthenticator test`() {
        val clazz = cp.findClass("com.sun.mail.smtp.SMTPSaslAuthenticator")
        val method = clazz.declaredMethods.first { it.name == "authenticate" }
        println(method.dumpInstructions())
        method.flowGraph()
    }

    @Test
    fun `ref undefined`() {
        val clazz = cp.findClass("com.sun.mail.smtp.SMTPTransport\$DigestMD5Authenticator")
        clazz.declaredMethods.forEach { it.flowGraph() }
    }

    @Test
    fun `properly merged frames for old bytecodce`() {
        val clazz = cp.findClass<IMAPMessage>()
        val method = clazz.declaredMethods.first { it.name == "writeTo" }
        method.flowGraph()
    }

    @Test
    fun `locals should work`() {
        val clazz = cp.findClass<IRExamples>()
        with(clazz.declaredMethods.first { it.name == "sortTimes" }) {
            assertEquals(9, instList.locals.size)
            assertEquals(13, instList.values .size)
        }

        with(clazz.declaredMethods.first { it.name == "test" }) {
            assertEquals(2, instList.locals.size)
            assertEquals(5, instList.values.size)
        }
        with(clazz.declaredMethods.first { it.name == "concatTest" }) {
            assertEquals(6, instList.locals.size)
            assertEquals(6, instList.values.size)
        }
        with(clazz.declaredMethods.first { it.name == "testArrays" }) {
            assertEquals(4, instList.locals.size)
            assertEquals(8, instList.values.size)
        }
    }

    @Test
    fun `java 5 bytecode processed correctly`() {
        val jars = cp.registeredLocations.map { it.path }
            .filter { it.contains("mail-1.4.7.jar") || it.contains("activation-1.1.jar") }
        assertEquals(2, jars.size)
        val list = ConcurrentHashMap.newKeySet<JcClassOrInterface>()
        runBlocking {
            cp.execute(object : JcClassProcessingTask {
                override fun shouldProcess(registeredLocation: RegisteredLocation): Boolean {
                    return !registeredLocation.isRuntime && jars.contains(registeredLocation.path)
                }

                override fun process(clazz: JcClassOrInterface) {
                    list.add(clazz)
                }
            })
        }
        val failed = ConcurrentHashMap.newKeySet<JcMethod>()
        list.parallelStream().forEach { clazz ->
            clazz.declaredMethods.forEach {
                try {
                    it.flowGraph()
                } catch (e: Exception) {
                    failed.add(it)
                }
            }
        }
        assertTrue(
            failed.isEmpty(),
            "Failed to process methods: \n${failed.joinToString("\n") { it.enclosingClass.name + "#" + it.name }}"
        )
    }

    private fun JcMethod.dumpInstructions(): String {
        return buildString {
            val textifier = Textifier()
            asmNode().accept(TraceMethodVisitor(textifier))
            textifier.text.printList(this)
        }
    }

    private fun List<*>.printList(builder: StringBuilder) {
        forEach {
            if (it is List<*>) {
                it.printList(builder)
            } else {
                builder.append(it.toString())
            }
        }
    }

}