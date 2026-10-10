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

package org.jacodb.ets.test

import org.jacodb.ets.dto.BasicBlockDto
import org.jacodb.ets.dto.BooleanTypeDto
import org.jacodb.ets.dto.CfgDto
import org.jacodb.ets.dto.ConstantDto
import org.jacodb.ets.dto.ExceptionalSuccessorDto
import org.jacodb.ets.dto.IfStmtDto
import org.jacodb.ets.dto.StringTypeDto
import org.jacodb.ets.dto.ThrowStmtDto
import org.jacodb.ets.dto.ReturnVoidStmtDto
import org.jacodb.ets.utils.toDot
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class CfgDtoToDotTest {
    @Test
    fun `labels condition successors using the DTO false then true convention`() {
        val cfg = CfgDto(blocks = listOf(
            BasicBlockDto(
                id = 0,
                successors = listOf(1, 2),
                stmts = listOf(IfStmtDto(condition = ConstantDto(value = "true", type = BooleanTypeDto))),
            ),
            BasicBlockDto(id = 1, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
            BasicBlockDto(id = 2, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
        ))

        for (useHtml in listOf(false, true)) {
            val dot = cfg.toDot(useHtml = useHtml)

            assertTrue("0 -> 2 [label=\"true\"]" in dot)
            assertTrue("0 -> 1 [label=\"false\"]" in dot)
        }
    }

    @Test
    fun `retains exceptional edges of blocks without normal successors`() {
        val cfg = CfgDto(blocks = listOf(
            BasicBlockDto(
                id = 0,
                successors = emptyList(),
                exceptionalSuccessors = listOf(ExceptionalSuccessorDto(stmtIndex = 0, target = 1)),
                stmts = listOf(ThrowStmtDto(arg = ConstantDto(value = "failure", type = StringTypeDto))),
            ),
            BasicBlockDto(id = 1, successors = emptyList(), stmts = listOf(ReturnVoidStmtDto)),
        ))

        val dot = cfg.toDot(useHtml = true)

        assertTrue("0 -> 1 [label=\"exception @0\" style=dashed]" in dot)
    }

}
