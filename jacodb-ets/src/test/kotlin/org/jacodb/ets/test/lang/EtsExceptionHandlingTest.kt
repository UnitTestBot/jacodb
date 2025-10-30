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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Tests for exception handling constructions (try-catch-finally).
 */
class EtsExceptionHandlingTest : EtsLangTestBase() {

    companion object {
        private const val EXCEPTIONS_PATH = "/samples/source/lang/exceptions.ts"
        private const val TRY_CATCH_PATH = "/samples/source/lang/try-catch.ts"

        private val exceptionsFile: EtsFile by lazy {
            loadSourceFile(EXCEPTIONS_PATH)
        }

        private val tryCatchFile: EtsFile by lazy {
            loadSourceFile(TRY_CATCH_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(exceptionsFile, "Failed to load $EXCEPTIONS_PATH")
            assertNotNull(tryCatchFile, "Failed to load $TRY_CATCH_PATH")
            logger.info { "✓ Setup complete, ready to run ${EtsExceptionHandlingTest::class.simpleName} tests" }
        }
    }

    @Test
    fun testSimpleTryCatch() {
        val method = assertMethodExists(exceptionsFile, "testSimpleTryCatch", minStmts = 1, hasTraps = true)

        logMethodDetails(method)

        // Should have at least one trap for the try-catch
        assertTrue(method.body.traps.isNotEmpty(), "Method should have exception traps")

        val trap = method.body.traps[0]
        assertTrue(trap.tryBlocks.isNotEmpty(), "Trap should have try blocks")
        assertTrue(trap.catchBlocks.isNotEmpty(), "Trap should have catch blocks")
    }

    @Test
    fun testTryCatchFinally() {
        val method = assertMethodExists(exceptionsFile, "testTryCatchFinally", minStmts = 1, hasTraps = true)

        logMethodDetails(method)

        // Should have two traps: one for catch and one for finally
        assertEquals(2, method.body.traps.size, "Method should have 2 traps (catch and finally)")

        // Verify trap structure
        for (trap in method.body.traps) {
            assertTrue(trap.tryBlocks.isNotEmpty(), "Each trap should have try blocks")
            assertTrue(trap.catchBlocks.isNotEmpty(), "Each trap should have catch blocks")
        }
    }

    @Test
    fun testTryFinally() {
        val method = assertMethodExists(exceptionsFile, "testTryFinally", minStmts = 1, hasTraps = true)

        logMethodDetails(method)

        // Should have trap for finally block
        assertTrue(method.body.traps.isNotEmpty(), "Method should have exception traps for finally")
    }

    @Disabled("ArkAnalyzer issue with nested try-catch: https://gitcode.com/openharmony-sig/arkanalyzer/issues/816")
    @Test
    fun testNestedTryCatch() {
        val method = assertMethodExists(exceptionsFile, "testNestedTryCatch", minStmts = 1, hasTraps = true)

        logMethodDetails(method)

        // Should have multiple traps for nested try-catch blocks
        assertTrue(method.body.traps.size >= 2, "Nested try-catch should have multiple traps")
    }

    @Test
    fun testMultipleCatchPaths() {
        val method = assertMethodExists(exceptionsFile, "testMultipleCatchPaths", minStmts = 1, hasTraps = true)

        logMethodDetails(method)

        // Verify the method handles different exception types
        assertTrue(method.body.traps.isNotEmpty(), "Method should have exception handling")
    }

    @Test
    fun testFinallyOverridesReturn() {
        val method = assertMethodExists(exceptionsFile, "testFinallyOverridesReturn", minStmts = 1, hasTraps = true)

        logMethodDetails(method)

        // Should have trap for finally with return in try
        assertTrue(method.body.traps.isNotEmpty(), "Method should have finally block trap")
    }

    @Test
    fun testMultipleExits() {
        val method = assertMethodExists(exceptionsFile, "testMultipleExits", minStmts = 1, hasTraps = true)

        logMethodDetails(method)

        // Should have traps for catch and finally with multiple return points
        assertTrue(method.body.traps.size >= 2, "Method should have multiple traps")
    }

    @Test
    fun testOriginalTryCatchSample() {
        // Test the original sample from try-catch.ts
        val method = assertMethodExists(tryCatchFile, "testTryCatch", minStmts = 1, hasTraps = true)

        logMethodDetails(method)

        // Should have two traps for the try-catch-finally block
        assertEquals(2, method.body.traps.size, "Original sample should have 2 traps")
        val trap = method.body.traps[0]

        // Verify trap has try blocks and catch blocks
        assertTrue(trap.tryBlocks.isNotEmpty(), "Try blocks should not be empty")
        assertTrue(trap.catchBlocks.isNotEmpty(), "Catch blocks should not be empty")
    }
}
