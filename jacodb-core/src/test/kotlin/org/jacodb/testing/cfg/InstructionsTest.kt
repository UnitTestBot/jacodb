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
import mu.KLogging
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcClassProcessingTask
import org.jacodb.api.JcMethod
import org.jacodb.api.RegisteredLocation
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.boolean
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.cfg.locals
import org.jacodb.api.ext.cfg.values
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.humanReadableSignature
import org.jacodb.api.ext.int
import org.jacodb.testing.Common
import org.jacodb.testing.Common.CommonClass
import org.jacodb.testing.WithDB
import org.jacodb.testing.cfg.RealMethodResolution.Virtual
import org.jacodb.testing.cfg.RealMethodResolution.VirtualImpl
import org.jacodb.testing.primitives.Primitives
import org.jacodb.testing.structure.FieldsAndMethods
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnJre
import org.junit.jupiter.api.condition.EnabledOnJre
import org.junit.jupiter.api.condition.JRE
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.activation.DataHandler


class InstructionsTest : BaseInstructionsTest() {

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
    fun `cmp insts`() {
        val clazz = cp.findClass<Conditionals>()
        val method = clazz.declaredMethods.first { it.name == "main" }
        val instructions = method.instList.instructions
        val cmpExprs = instructions.filterIsInstance<JcIfInst>().map { it.condition }
        assertEquals(4, cmpExprs.size)

        val geZero = cmpExprs[0] as JcGeExpr
        assertEquals(0 to 0, (geZero.lhv as JcArgument).index to (geZero.rhv as JcInt).value)

        val gtZero = cmpExprs[1] as JcGtExpr
        assertEquals(0 to 0, (gtZero.lhv as JcArgument).index to (gtZero.rhv as JcInt).value)

        val geOther = cmpExprs[2] as JcGeExpr
        assertEquals(0 to 1, (geOther.lhv as JcArgument).index to (geOther.rhv as JcArgument).index)

        val gtOther = cmpExprs[3] as JcGtExpr
        assertEquals(0 to 1, (gtOther.lhv as JcArgument).index to (gtOther.rhv as JcArgument).index)
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
        method.flowGraph()
    }

    @Test
    fun `ref undefined`() {
        val clazz = cp.findClass("com.sun.mail.smtp.SMTPTransport\$DigestMD5Authenticator")
        clazz.declaredMethods.forEach { it.flowGraph() }
    }

    @Test
    fun `properly merged frames for old bytecode`() {
        val clazz = cp.findClass<IMAPMessage>()
        val method = clazz.declaredMethods.first { it.name == "writeTo" }
        method.flowGraph()
    }

    @Test
    @EnabledOnJre(JRE.JAVA_11)
    fun `locals should work`() {
        val clazz = cp.findClass<IRExamples>()
        with(clazz.declaredMethods.first { it.name == "sortTimes" }) {
            assertEquals(9, instList.locals.size)
            assertEquals(13, instList.values.size)
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
    @EnabledOnJre(JRE.JAVA_8)
    fun `java 5 bytecode processed correctly on java 8`() {
        runAlong("mail-1.4.7.jar", "joda-time-2.12.5.jar")
    }

    @Test
    @DisabledOnJre(JRE.JAVA_8)
    fun `java 5 bytecode processed correctly on java 9+`() {
        runAlong("mail-1.4.7.jar", "activation-1.1.jar", "joda-time-2.12.5.jar")
    }

    @Test
    fun `constant ldc instructions`() {
        val clazz = cp.findClass("TestLDC")
        clazz.declaredMethods.forEach {
            it.flowGraph()
        }
    }

    private fun runAlong(vararg patters: String) {
        val jars = cp.registeredLocations.map { it.path }
            .filter { patters.any { pattern -> it.contains(pattern) } }
        assertEquals(patters.size, jars.size)
        val list = ConcurrentHashMap.newKeySet<JcClassOrInterface>()
        runBlocking {
            val pureClasspath = cp.db.classpath(jars.map { File(it) })
            pureClasspath.execute(object : JcClassProcessingTask {
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
                    KLogging().logger.error(e) { "can't process $it" }
                    failed.add(it)
                }
            }
        }
        assertTrue(
            failed.isEmpty(),
            "Failed to process methods: \n${failed.joinToString("\n") { it.enclosingClass.name + "#" + it.name }}"
        )
    }

    @Test
    fun `resolving primitive types for local variables should work`() {
        val clazz = cp.findClass<Primitives>()
        clazz.declaredMethods.filter { !it.isConstructor }.forEach {
            val returnValue = (it.instList.last() as JcReturnInst).returnValue
            assertNotNull(returnValue!!)
            val expected = cp.findTypeOrNull(it.returnType.typeName)
            assertEquals(expected, returnValue.type, "types not matched for ${it.humanReadableSignature}")
        }
    }

    @Test
    fun `method resolution based on var`() {
        val clazz = cp.findClass<RealMethodResolution>()
        val insts = clazz.declaredMethods.first { it.name == "test" }.instList
        val actionCallExpr = insts.instructions.firstNotNullOf {
            (it as? JcCallInst)?.callExpr.takeIf { it is JcVirtualCallExpr }
        } as JcVirtualCallExpr
        assertEquals(cp.findClass<VirtualImpl>(), actionCallExpr.method.method.enclosingClass)
        assertEquals(cp.findClass<Virtual>(), actionCallExpr.declaredMethod.method.enclosingClass)
    }

    @Test
    fun `resolving field descriptors should work`() {
        val parent = cp.findClass<Common.Common1>()
        val child = cp.findClass<FieldsAndMethods.Common1Child>()

        // public int field
        val methodWithPublicFieldInt = child.declaredMethods.first { it.name == "accessIntField" }
        val assignInstInt = methodWithPublicFieldInt.instList
            .filterIsInstance<JcAssignInst>()
            .single()
        val fieldInt = (assignInstInt.rhv as JcFieldRef).field

        assertEquals(parent, fieldInt.enclosingType.jcClass)
        assertEquals(cp.int, fieldInt.fieldType)


        // public boolean field
        val methodWithPublicFieldBoolean = child.declaredMethods.first { it.name == "accessBooleanField" }
        val assignInstBoolean = methodWithPublicFieldBoolean.instList
            .filterIsInstance<JcAssignInst>()
            .single()
        val fieldBoolean = (assignInstBoolean.rhv as JcFieldRef).field

        assertEquals(child, fieldBoolean.enclosingType.jcClass)
        assertEquals(cp.boolean, fieldBoolean.fieldType)
    }

    @Test
    fun `private call with invokevirtual instruction`() {
        val clazz = cp.findClass("VirtualInstructions")
        val instList = clazz.declaredMethods.first { it.name == "run" }.instList
        val callDoSmth = instList.mapNotNull { it.callExpr }.first {
            it.toString().contains("doSmth")
        }
        assertEquals("doSmth", callDoSmth.method.method.name)
    }

    @Test
    fun `call default method should be resolved`() {
        val clazz = cp.findClass<CommonClass>()
        val instList = clazz.declaredMethods.first { it.name == "run" }.instList
        val callDefaultMethod = instList.mapNotNull { it.callExpr }.first {
            it.toString().contains("defaultMethod")
        }
        assertEquals("defaultMethod", callDefaultMethod.method.method.name)
    }


    @Test
    fun `iinc should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iinc" }
        val res = method.invoke(null, 0)
        assertEquals(0, res)
    }

    @Test
    fun `iinc arrayIntIdx should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincArrayIntIdx" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(1, 0, 2), res as IntArray)
    }

    @Test
    fun `iinc arrayByteIdx should work`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincArrayByteIdx" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(1, 0, 2), res as IntArray)
    }

    @Test
    fun `iinc for`() {
        val clazz = cp.findClass<Incrementation>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.first { it.name == "iincFor" }
        val res = method.invoke(null)
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4), res as IntArray)
    }

}

fun JcMethod.dumpInstructions(): String {
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