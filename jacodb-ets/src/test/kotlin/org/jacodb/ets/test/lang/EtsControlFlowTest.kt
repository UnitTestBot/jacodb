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

package org.jacodb.ets.test.lang

import mu.KotlinLogging
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Tests for control flow constructions (if-else, switch, ternary).
 */
class EtsControlFlowTest : EtsLangTestBase() {

    companion object {
        private const val SOURCE_PATH = "/samples/source/lang/control-flow.ts"

        private val file: EtsFile by lazy {
            loadSourceFile(SOURCE_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(file, "Failed to load $SOURCE_PATH")
            logger.info { "✓ Setup complete, ready to run ${EtsControlFlowTest::class.simpleName} tests" }
        }
    }

    @Test
    fun testIfElse() {
        val method = assertMethodExists(file, "testIfElse", minStmts = 1)

        logMethodDetails(method)

        // Should have if statement
        val hasIfStmt = method.cfg.stmts.any { it is EtsIfStmt }
        assertTrue(hasIfStmt, "Method should contain if statement")

        // Should have return statements
        val returnStmts = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()
        assertTrue(returnStmts.isNotEmpty(), "Method should have return statements")
    }

    @Test
    fun testIfElseIfChain() {
        val method = assertMethodExists(file, "testIfElseIfChain", minStmts = 1)

        logMethodDetails(method)

        // Should have multiple if statements or complex control flow
        val ifStmts = method.cfg.stmts.filterIsInstance<EtsIfStmt>()
        assertTrue(ifStmts.isNotEmpty(), "Method should contain if statements")
    }

    @Test
    fun testNestedIf() {
        val method = assertMethodExists(file, "testNestedIf", minStmts = 1)

        logMethodDetails(method)

        // Should have nested if statements (multiple blocks)
        assertTrue(method.cfg.blocks.size > 1, "Nested if should create multiple CFG blocks")
    }

    @Test
    fun testSwitch() {
        val method = assertMethodExists(file, "testSwitch", minStmts = 1)

        logMethodDetails(method)

        // Should have multiple blocks for cases
        assertTrue(method.cfg.blocks.size > 1, "Switch should create multiple CFG blocks")
    }

    @Test
    fun testSwitchFallthrough() {
        val method = assertMethodExists(file, "testSwitchFallthrough", minStmts = 1)

        logMethodDetails(method)

        // Should have multiple blocks for switch fallthrough behavior
        assertTrue(method.cfg.blocks.size > 1, "Switch with fallthrough should have multiple blocks")
    }

    @Test
    fun testTernary() {
        val method = assertMethodExists(file, "testTernary", minStmts = 1)

        logMethodDetails(method)

        // Ternary may be represented as conditional expressions or if-else
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have statements")
    }

    @Test
    fun testNestedTernary() {
        val method = assertMethodExists(file, "testNestedTernary", minStmts = 1)

        logMethodDetails(method)

        // Nested ternary should create complex control flow
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have statements")
    }

    @Test
    fun testEarlyReturn() {
        val method = assertMethodExists(file, "testEarlyReturn", minStmts = 1)

        logMethodDetails(method)

        // Should have multiple return statements
        val returnStmts = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()
        assertTrue(returnStmts.size >= 2, "Method should have multiple return statements")
    }

    @Test
    fun testMultipleReturns() {
        val method = assertMethodExists(file, "testMultipleReturns", minStmts = 1)

        logMethodDetails(method)

        // Should have multiple return statements in different branches
        val returnStmts = method.cfg.stmts.filterIsInstance<EtsReturnStmt>()
        assertTrue(returnStmts.size >= 2, "Method should have multiple return statements")

        // Should have multiple blocks for branches
        assertTrue(method.cfg.blocks.size > 1, "Multiple returns should create multiple blocks")
    }
}
