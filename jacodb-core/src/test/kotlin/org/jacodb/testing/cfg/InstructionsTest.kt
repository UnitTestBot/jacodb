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

import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.findClass
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
}