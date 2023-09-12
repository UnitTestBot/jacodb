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

package org.jacodb.analysis.impl

import org.jacodb.analysis.impl.custom.NullAssumptionAnalysis
import org.jacodb.api.JcClassOrInterface
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.ext.findClass
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithGlobalDB
import org.jacodb.testing.cfg.NullAssumptionAnalysisExample
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NullabilityAssumptionAnalysisTest : BaseTest() {

    companion object : WithGlobalDB()

    @Test
    fun `null-assumption analysis should work`() {
        val clazz = cp.findClass<NullAssumptionAnalysisExample>()
        with(clazz.findMethod("test1").flowGraph()) {
            val analysis = NullAssumptionAnalysis(this).also {
                it.run()
            }
            val sout = (instructions[0] as JcAssignInst).lhv as JcLocal
            val a = ((instructions[3] as JcAssignInst).rhv as JcInstanceCallExpr).instance

            assertTrue(analysis.isAssumedNonNullBefore(instructions[2], a))
            assertTrue(analysis.isAssumedNonNullBefore(instructions[0], sout))
        }
    }

    @Test
    fun `null-assumption analysis should work 2`() {
        val clazz = cp.findClass<NullAssumptionAnalysisExample>()
        with(clazz.findMethod("test2").flowGraph()) {
            val analysis = NullAssumptionAnalysis(this).also {
                it.run()
            }
            val sout = (instructions[0] as JcAssignInst).lhv as JcLocal
            val a = ((instructions[3] as JcAssignInst).rhv as JcInstanceCallExpr).instance
            val x = (instructions[5] as JcAssignInst).lhv as JcLocal

            assertTrue(analysis.isAssumedNonNullBefore(instructions[2], a))
            assertTrue(analysis.isAssumedNonNullBefore(instructions[0], sout))
            analysis.isAssumedNonNullBefore(instructions[5], x)
        }
    }

    private fun JcClassOrInterface.findMethod(name: String): JcMethod = declaredMethods.first { it.name == name }

}