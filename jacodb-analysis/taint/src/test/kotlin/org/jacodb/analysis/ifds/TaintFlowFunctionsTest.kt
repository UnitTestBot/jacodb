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

package org.jacodb.analysis.ifds

import io.mockk.every
import io.mockk.mockk
import org.jacodb.analysis.ifds.domain.CallAction
import org.jacodb.analysis.ifds.taint.ForwardTaintFlowFunctions
import org.jacodb.analysis.ifds.taint.TaintZeroFact
import org.jacodb.analysis.ifds.taint.Tainted
import org.jacodb.analysis.ifds.util.getArgument
import org.jacodb.analysis.ifds.util.toPath
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCallInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.packageName
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TaintFlowFunctionsTest : BaseTest() {

    companion object : WithDB(Usages, InMemoryHierarchy)

    private val configurationFeature = run {
        val configFileName = "config_test.json"
        val configResource = this.javaClass.getResourceAsStream("/$configFileName")
        if (configResource != null) {
            val configJson = configResource.bufferedReader().readText()
            TaintConfigurationFeature.fromJson(configJson)
        } else {
            null
        }
    }

    private val stringType = cp.findTypeOrNull<String>() as JcClassType

    private val testMethod = mockk<JcMethod> {
        every { name } returns "test"
        every { enclosingClass } returns mockk(relaxed = true) {
            every { packageName } returns "com.example"
            every { simpleName } returns "Example"
            every { name } returns "com.example.Example"
            every { superClass } returns null
            every { interfaces } returns emptyList()
        }
        every { isConstructor } returns false
        every { returnType } returns mockk(relaxed = true)
        every { parameters } returns listOf(
            mockk(relaxed = true) {
                every { index } returns 0
                every { type } returns mockk {
                    every { typeName } returns "java.lang.String"
                }
            }
        )
    }

    @Test
    fun `test obtain start facts`() {
        val flowSpace =
            ForwardTaintFlowFunctions(cp, configurationFeature)
        val facts = flowSpace.obtainPossibleStartFacts(testMethod).toList()
        val arg0 = cp.getArgument(testMethod.parameters[0])!!
        val arg0Taint = Tainted(arg0.toPath(), TaintMark("EXAMPLE"))
        Assertions.assertEquals(listOf(TaintZeroFact, arg0Taint), facts)
    }

    @Test
    fun `test sequential flow function assign mark`() {
        // "x := y", where 'y' is tainted, should result in both 'x' and 'y' to be tainted
        val x: JcLocal = JcLocalVar(1, "x", stringType)
        val y: JcLocal = JcLocalVar(2, "y", stringType)
        val inst = JcAssignInst(location = mockk(), lhv = x, rhv = y)
        val flowSpace = ForwardTaintFlowFunctions(
            cp,
            configurationFeature
        )
        val yTaint = Tainted(y.toPath(), TaintMark("TAINT"))
        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val facts = flowSpace.sequent(inst, next = mockk(), yTaint).toList()
        Assertions.assertEquals(listOf(yTaint, xTaint), facts)
    }

    @Test
    fun `test call flow function assign mark`() {
        // "x := test(...)", where 'test' is a source, should result in 'x' to be tainted
        val x: JcLocal = JcLocalVar(1, "x", stringType)
        val callStatement = JcAssignInst(location = mockk(), lhv = x, rhv = mockk<JcCallExpr> {
            every { method } returns mockk {
                every { method } returns testMethod
            }
        })
        val flowSpace = ForwardTaintFlowFunctions(
            cp,
            configurationFeature
        )
        val xTaint = Tainted(x.toPath(), TaintMark("EXAMPLE"))
        val actions = flowSpace.call(callStatement, returnSite = mockk(), TaintZeroFact).toList()
        val expectedActions = listOf(
            CallAction.Return(TaintZeroFact),
            CallAction.Start(TaintZeroFact),
            CallAction.Return(xTaint)
        )
        Assertions.assertEquals(expectedActions, actions)
    }

    @Test
    fun `test call flow function remove mark`() {
        // "test(x)", where 'x' is tainted, should result in 'x' NOT to be tainted
        val x: JcLocal = JcLocalVar(1, "x", stringType)
        val callStatement = JcCallInst(location = mockk(), callExpr = mockk<JcCallExpr> {
            every { method } returns mockk {
                every { method } returns testMethod
            }
            every { args } returns listOf(x)
        })
        val flowSpace = ForwardTaintFlowFunctions(
            cp,
            configurationFeature
        )
        val xTaint = Tainted(x.toPath(), TaintMark("REMOVE"))
        val facts = flowSpace.call(callStatement, returnSite = mockk(), xTaint).toList()
        Assertions.assertTrue(facts.isEmpty())
    }

    @Test
    fun `test call flow function copy mark`() {
        // "y := test(x)" should result in 'y' to be tainted only when 'x' is tainted
        val x: JcLocal = JcLocalVar(1, "x", stringType)
        val y: JcLocal = JcLocalVar(2, "y", stringType)
        val callStatement = JcAssignInst(location = mockk(), lhv = y, rhv = mockk<JcCallExpr> {
            every { method } returns mockk {
                every { method } returns testMethod
            }
            every { args } returns listOf(x)
        })
        val flowSpace = ForwardTaintFlowFunctions(
            cp,
            configurationFeature
        )
        val xTaint = Tainted(x.toPath(), TaintMark("COPY"))
        val yTaint = Tainted(y.toPath(), TaintMark("COPY"))
        val actions = flowSpace.call(callStatement, returnSite = mockk(), xTaint).toList()
        val expectedActions = listOf(
            CallAction.Return(xTaint),
            CallAction.Return(yTaint)
        )
        Assertions.assertEquals(expectedActions, actions) // copy from x to y

        val other: JcLocal = JcLocalVar(10, "other", stringType)
        val otherTaint = Tainted(other.toPath(), TaintMark("OTHER"))
        val actions2 = flowSpace.call(callStatement, returnSite = mockk(), otherTaint).toList()
        val expectedActions2 = listOf(CallAction.Return(otherTaint))
        Assertions.assertEquals(expectedActions2, actions2) // pass-through
    }

    @Test
    fun `test call to start flow function`() {
        // "test(x)", where 'x' is tainted, should result in 'x' (formal argument of 'test') to be tainted
        val x: JcLocal = JcLocalVar(1, "x", stringType)
        val callStatement = JcCallInst(location = mockk(), callExpr = mockk<JcCallExpr> {
            every { method } returns mockk {
                every { method } returns testMethod
            }
            every { args } returns listOf(x)
        })
        val flowSpace = ForwardTaintFlowFunctions(
            cp,
            configurationFeature
        )

        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val arg0: JcArgument = cp.getArgument(testMethod.parameters[0])!!
        val arg0Taint = Tainted(arg0.toPath(), TaintMark("TAINT"))
        val calleeStart = mockk<JcInst> {
            every { location } returns mockk {
                every { method } returns testMethod
            }
        }
        val facts = flowSpace.callToStart(
            callStatement,
            calleeStart = calleeStart,
            xTaint
        ).toList()
        Assertions.assertEquals(listOf(arg0Taint), facts)
        val other: JcLocal = JcLocalVar(10, "other", stringType)
        val otherTaint = Tainted(other.toPath(), TaintMark("TAINT"))
        val facts2 = flowSpace.callToStart(
            callStatement,
            calleeStart = calleeStart,
            otherTaint
        ).toList()
        Assertions.assertTrue(facts2.isEmpty())
    }

    @Test
    fun `test exit flow function`() {
        // "x := test()" + "return y", where 'y' is tainted, should result in 'x' to be tainted
        val x: JcLocal = JcLocalVar(1, "x", stringType)
        val callStatement = JcAssignInst(location = mockk(), lhv = x, rhv = mockk<JcCallExpr> {
            every { method } returns mockk {
                every { method } returns testMethod
            }
        })
        val y: JcLocal = JcLocalVar(1, "y", stringType)
        val exitStatement = JcReturnInst(location = mockk {
            every { method } returns testMethod
        }, returnValue = y)
        val flowSpace = ForwardTaintFlowFunctions(
            cp,
            configurationFeature
        )
        val yTaint = Tainted(y.toPath(), TaintMark("TAINT"))
        val xTaint = Tainted(x.toPath(), TaintMark("TAINT"))
        val facts = flowSpace.exitToReturnSite(callStatement, returnSite = mockk(), exitStatement, yTaint).toList()
        Assertions.assertEquals(listOf(xTaint), facts)
    }
}
