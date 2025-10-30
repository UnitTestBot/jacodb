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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Tests for loop constructions (for, while, do-while, for-in, for-of).
 */
class EtsLoopTest : EtsLangTestBase() {

    companion object {
        private const val SOURCE_PATH = "/samples/source/lang/loops.ts"

        private val file: EtsFile by lazy {
            loadSourceFile(SOURCE_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(file, "Failed to load $SOURCE_PATH")
            logger.info { "✓ Setup complete, ready to run ${EtsLoopTest::class.simpleName} tests" }
        }
    }

    @Test
    fun testSimpleForLoop() {
        val method = assertMethodExists(file, "testSimpleForLoop", minStmts = 1)

        logMethodDetails(method)

        // Should have multiple blocks for loop structure
        assertTrue(method.cfg.blocks.size > 1, "For loop should create multiple CFG blocks")
    }

    @Test
    fun testForLoopWithBreak() {

        val method = assertMethodExists(file, "testForLoopWithBreak", minStmts = 1)

        logMethodDetails(method)

        // Break statement should create additional control flow
        assertTrue(method.cfg.blocks.size > 1, "For loop with break should have complex CFG")
    }

    @Test
    fun testForLoopWithContinue() {

        val method = assertMethodExists(file, "testForLoopWithContinue", minStmts = 1)

        logMethodDetails(method)

        // Continue statement should create additional control flow
        assertTrue(method.cfg.blocks.size > 1, "For loop with continue should have complex CFG")
    }

    @Test
    fun testWhileLoop() {

        val method = assertMethodExists(file, "testWhileLoop", minStmts = 1)

        logMethodDetails(method)

        // Should have loop structure
        assertTrue(method.cfg.blocks.size > 1, "While loop should create multiple CFG blocks")
    }

    @Test
    fun testWhileLoopWithBreak() {

        val method = assertMethodExists(file, "testWhileLoopWithBreak", minStmts = 1)

        logMethodDetails(method)

        // Break in infinite loop should create exit path
        assertTrue(method.cfg.blocks.size > 1, "While loop with break should have complex CFG")
    }

    @Test
    fun testDoWhileLoop() {

        val method = assertMethodExists(file, "testDoWhileLoop", minStmts = 1)

        logMethodDetails(method)

        // Do-while has different structure than while
        assertTrue(method.cfg.blocks.size > 1, "Do-while loop should create multiple CFG blocks")
    }

    @Test
    fun testNestedLoops() {

        val method = assertMethodExists(file, "testNestedLoops", minStmts = 1)

        logMethodDetails(method)

        // Nested loops should create many blocks
        assertTrue(method.cfg.blocks.size > 2, "Nested loops should create many CFG blocks")
    }

    @Test
    fun testForInLoop() {

        val method = assertMethodExists(file, "testForInLoop", minStmts = 1)

        logMethodDetails(method)

        // For-in loop structure
        assertTrue(method.cfg.blocks.size > 1, "For-in loop should create multiple CFG blocks")
    }

    @Test
    fun testForOfLoop() {

        val method = assertMethodExists(file, "testForOfLoop", minStmts = 1)

        logMethodDetails(method)

        // For-of loop structure
        assertTrue(method.cfg.blocks.size > 1, "For-of loop should create multiple CFG blocks")
    }

    @Disabled("Labeled loops are not supported by ArkAnalyzer")
    @Test
    fun testLabeledBreak() {

        val method = assertMethodExists(file, "testLabeledBreak", minStmts = 1)

        logMethodDetails(method)

        // Labeled break in nested loops
        assertTrue(method.cfg.blocks.size > 2, "Labeled break should work with nested loops")
    }

    @Disabled("Labeled loops are not supported by ArkAnalyzer")
    @Test
    fun testLabeledContinue() {

        val method = assertMethodExists(file, "testLabeledContinue", minStmts = 1)

        logMethodDetails(method)

        // Labeled continue in nested loops
        assertTrue(method.cfg.blocks.size > 2, "Labeled continue should work with nested loops")
    }

    @Test
    fun testInfiniteLoop() {

        val method = assertMethodExists(file, "testInfiniteLoop", minStmts = 1)

        logMethodDetails(method)

        // Infinite loop with conditional break
        assertTrue(method.cfg.blocks.size > 1, "Infinite loop should have break path")
    }
}
