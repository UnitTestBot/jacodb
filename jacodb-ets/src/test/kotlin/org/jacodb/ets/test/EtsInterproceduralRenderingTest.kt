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

import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsMethodImpl
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsVoidType
import org.jacodb.ets.utils.InterproceduralCfg
import org.jacodb.ets.utils.toHighlightedDotWithCalls
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EtsInterproceduralRenderingTest {

    @Test
    fun `empty callee has a visible placeholder in HTML and plain DOT`() {
        val file = EtsFileSignature(projectName = "Project", fileName = "empty.ts")
        val owner = EtsClassSignature(name = "Caller", file = file)
        val caller = method(owner, name = "run")
        val target = method(owner, name = "target")
        val call = call(caller, target)
        val main = cfg(blocks = listOf(BasicBlock(id = 0, statements = listOf(call))))
        val graph = InterproceduralCfg(
            main = main,
            callees = mapOf((call to 0) to EtsBlockCfg.EMPTY),
        )

        val html = graph.toHighlightedDotWithCalls(pathStmts = emptySet(), currentStmt = null, useHtml = true)
        val plain = graph.toHighlightedDotWithCalls(pathStmts = emptySet(), currentStmt = null, useHtml = false)

        for (dot in listOf(html, plain)) {
            assertContains(dot, "subgraph \"cluster_0_B0\"")
            assertContains(dot, "C_0_empty [label=\"empty CFG\"]")
            assertContains(dot, "lhead=\"cluster_0_B0\"")
        }
        assertContains(html, "M_0:p0 -> C_0_empty")
        assertContains(plain, "M_0 -> C_0_empty")
    }

    @Test
    fun `colliding statement hashes keep separate callee nodes and call edges`() {
        val file = EtsFileSignature(projectName = "Project", fileName = "collisions.ts")
        val ownerAa = EtsClassSignature(name = "Aa", file = file)
        val ownerBb = EtsClassSignature(name = "BB", file = file)
        val callerAa = method(ownerAa, name = "run")
        val callerBb = method(ownerBb, name = "run")
        val targetAa = method(ownerAa, name = "target")
        val targetBb = method(ownerBb, name = "target")
        val callAa = call(callerAa, targetAa)
        val callBb = call(callerBb, targetBb)
        val main = cfg(
            blocks = listOf(
                BasicBlock(id = 0, statements = listOf(callAa)),
                BasicBlock(id = 1, statements = listOf(callBb)),
            ),
        )
        val graph = InterproceduralCfg(
            main = main,
            callees = linkedMapOf(
                (callAa to 0) to returnedCfg(targetAa),
                (callBb to 1) to returnedCfg(targetBb),
            ),
        )

        assertEquals(ownerAa.hashCode(), ownerBb.hashCode())
        assertEquals(callAa.hashCode(), callBb.hashCode())
        assertNotEquals(callAa, callBb)

        val html = graph.toHighlightedDotWithCalls(pathStmts = emptySet(), currentStmt = null, useHtml = true)
        val plain = graph.toHighlightedDotWithCalls(pathStmts = emptySet(), currentStmt = null, useHtml = false)

        for (dot in listOf(html, plain)) {
            assertContains(dot, "subgraph \"cluster_0_B0\"")
            assertContains(dot, "subgraph \"cluster_1_B1\"")
            assertContains(dot, "C_0_0 [label=")
            assertContains(dot, "C_1_0 [label=")
            assertContains(dot, "Aa::target(): void")
            assertContains(dot, "BB::target(): void")
        }
        assertContains(html, "M_0:p0 -> C_0_0")
        assertContains(html, "M_1:p1 -> C_1_0")
        assertContains(plain, "M_0 -> C_0_0")
        assertContains(plain, "M_1 -> C_1_0")
    }

    private fun method(owner: EtsClassSignature, name: String): EtsMethodImpl = EtsMethodImpl(
        signature = EtsMethodSignature(
            enclosingClass = owner,
            name = name,
            parameters = emptyList(),
            returnType = EtsVoidType,
        ),
    )

    private fun call(caller: EtsMethodImpl, target: EtsMethodImpl): EtsCallStmt = EtsCallStmt(
        location = EtsStmtLocation(method = caller, index = 0),
        expr = EtsStaticCallExpr(
            callee = target.signature,
            args = emptyList(),
            type = EtsVoidType,
        ),
    )

    private fun returnedCfg(method: EtsMethodImpl): EtsBlockCfg = cfg(
        blocks = listOf(
            BasicBlock(
                id = 0,
                statements = listOf(EtsReturnStmt(location = EtsStmtLocation(method = method, index = 0), returnValue = null)),
            ),
        ),
    )

    private fun cfg(blocks: List<BasicBlock>): EtsBlockCfg {
        val successors = blocks.associate { block -> block.id to emptyList<Int>() }
        return EtsBlockCfg(blocks = blocks, successors = successors)
    }
}
