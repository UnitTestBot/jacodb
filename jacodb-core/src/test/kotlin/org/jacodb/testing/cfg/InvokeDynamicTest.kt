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

import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcLambdaExpr
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.testing.WithGlobalDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InvokeDynamicTest : BaseInstructionsTest() {

    companion object : WithGlobalDB()

    @Test
    fun `test unary function`() = runStaticMethod<InvokeDynamicExamples>("testUnaryFunction")

    @Test
    fun `test method ref unary function`() = runStaticMethod<InvokeDynamicExamples>("testMethodRefUnaryFunction")

    @Test
    fun `test currying function`() = runStaticMethod<InvokeDynamicExamples>("testCurryingFunction")

    @Test
    fun `test sam function`() = runStaticMethod<InvokeDynamicExamples>("testSamFunction")

    @Test
    fun `test sam with default function`() = runStaticMethod<InvokeDynamicExamples>("testSamWithDefaultFunction")

    @Test
    fun `test complex invoke dynamic`() = runStaticMethod<InvokeDynamicExamples>("testComplexInvokeDynamic")

    @Test
    fun `test resolving virtual lambda`() {
        val clazz = cp.findClass<InvokeDynamicExamples.CollectionWithInnerMap>()
        val method = clazz.declaredMethods.find { it.name == "putAll" }!!
        val instructions = method.instList
        val first = instructions[0] as JcAssignInst
        assertTrue(first.rhv is JcLambdaExpr)
        val third = instructions[2] as JcAssignInst
        assertTrue(third.rhv is JcLambdaExpr)
        runStaticMethod<InvokeDynamicExamples>("testNonStaticLambda")
    }

    @Test
    fun `invoke dynamic constructor`() = runStaticMethod<InvokeDynamicExamples>("testInvokeDynamicConstructor")

    private inline fun <reified T> runStaticMethod(name: String) {
        val clazz = cp.findClass<T>()

        val javaClazz = testAndLoadClass(clazz)
        val method = javaClazz.methods.single { it.name == name }
        val res = method.invoke(null)
        assertEquals("OK", res)
    }
}
