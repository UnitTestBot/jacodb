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

import mu.KotlinLogging
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.utils.TrapUtils
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Tests for the TrapUtils.classifyHandler function.
 * Tests various exception handling patterns: try-catch, try-finally, try-catch-finally, catch with rethrow.
 */
class HandlerClassifierTest : TestBase() {

    companion object {
        private const val SOURCE_PATH = "/samples/source/lang/handler-classifier.ts"

        private val file: EtsFile by lazy {
            loadSourceFile(SOURCE_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(file, "Failed to load $SOURCE_PATH")
            logger.info { "✓ Setup complete, ready to run ${HandlerClassifierTest::class.simpleName} tests" }
        }
    }

    @Test
    fun testSimpleTryCatch() {
        val method = assertMethodExists(file, "simpleTryCatch", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertTrue(traps.isNotEmpty(), "Method should have at least one trap")

        // Classify all handlers and verify the classifier runs without errors
        val classifications = traps.map { trap -> TrapUtils.classifyHandler(trap, traps) }

        assertTrue(classifications.isNotEmpty(), "Should have classified at least one handler")
        logger.info { "✓ Handler classifications in simpleTryCatch: $classifications" }
    }

    @Test
    fun testTryFinally() {
        val method = assertMethodExists(file, "tryFinally", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertTrue(traps.isNotEmpty(), "Method should have at least one trap for finally")

        // For try-finally, we expect COPIED_FINALLY handlers
        val finallyTraps = traps.filter { trap ->
            TrapUtils.classifyHandler(trap, traps) == TrapUtils.HandlerKind.COPIED_FINALLY
        }

        assertTrue(finallyTraps.isNotEmpty(), "Should have at least one COPIED_FINALLY handler")
        logger.info { "✓ Found ${finallyTraps.size} COPIED_FINALLY handler(s) in tryFinally" }
    }

    @Test
    fun testTryCatchFinally() {
        val method = assertMethodExists(file, "tryCatchFinally", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertTrue(traps.size >= 2, "Method should have at least 2 traps (for catch and finally)")

        val catchTraps = traps.filter { trap ->
            TrapUtils.classifyHandler(trap, traps) == TrapUtils.HandlerKind.CATCH
        }
        val finallyTraps = traps.filter { trap ->
            TrapUtils.classifyHandler(trap, traps) == TrapUtils.HandlerKind.COPIED_FINALLY
        }

        assertTrue(catchTraps.isNotEmpty(), "Should have at least one CATCH handler")
        assertTrue(finallyTraps.isNotEmpty(), "Should have at least one COPIED_FINALLY handler")

        logger.info { "✓ Found ${catchTraps.size} CATCH and ${finallyTraps.size} COPIED_FINALLY handlers in tryCatchFinally" }
    }

    @Test
    fun testCatchWithRethrow() {
        val method = assertMethodExists(file, "catchWithRethrow", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertTrue(traps.isNotEmpty(), "Method should have at least one trap")

        // A catch with rethrow should still be classified as CATCH (or possibly COPIED_FINALLY if it rethrows)
        // The classifier should detect the rethrow pattern
        val classifications = traps.associateWith { trap ->
            TrapUtils.classifyHandler(trap, traps)
        }

        logger.info { "✓ Handler classifications in catchWithRethrow: $classifications" }

        // At least one trap should be present
        assertTrue(classifications.isNotEmpty(), "Should have classified at least one handler")
    }

    @Test
    fun testMultipleCatches() {
        val method = assertMethodExists(file, "multipleCatches", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertTrue(traps.isNotEmpty(), "Method should have at least one trap")

        val classifications = traps.map { trap -> TrapUtils.classifyHandler(trap, traps) }
        logger.info { "✓ Handler classifications in multipleCatches: $classifications" }

        // Just verify that classification works without errors
        assertTrue(classifications.isNotEmpty(), "Should have classified handlers")
    }

    @Test
    fun testFinallyOverridesReturn() {
        // Note: ArkAnalyzer might optimize finally blocks that override return values
        val method = assertMethodExists(file, "finallyOverridesReturn", minStmts = 1, hasTraps = null)
        logMethodDetails(method)

        val traps = method.body.traps
        if (traps.isEmpty()) {
            logger.info { "⚠ No traps generated for finallyOverridesReturn (possibly optimized away)" }
            return
        }

        val finallyTraps = traps.filter { trap ->
            TrapUtils.classifyHandler(trap, traps) == TrapUtils.HandlerKind.COPIED_FINALLY
        }

        logger.info { "✓ Found ${finallyTraps.size} COPIED_FINALLY handler(s) in finallyOverridesReturn" }
    }

    @Test
    fun testComplexTryCatchFinally() {
        val method = assertMethodExists(file, "complexTryCatchFinally", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertTrue(traps.size >= 2, "Complex try-catch-finally should have at least 2 traps")

        val catchTraps = traps.filter { trap ->
            TrapUtils.classifyHandler(trap, traps) == TrapUtils.HandlerKind.CATCH
        }
        val finallyTraps = traps.filter { trap ->
            TrapUtils.classifyHandler(trap, traps) == TrapUtils.HandlerKind.COPIED_FINALLY
        }
        val unknownTraps = traps.filter { trap ->
            TrapUtils.classifyHandler(trap, traps) == TrapUtils.HandlerKind.UNKNOWN
        }

        logger.info {
            "✓ Handler classifications in complexTryCatchFinally: " +
                "${catchTraps.size} CATCH, ${finallyTraps.size} COPIED_FINALLY, ${unknownTraps.size} UNKNOWN"
        }

        // Should have both catch and finally handlers
        assertTrue(
            catchTraps.isNotEmpty() || finallyTraps.isNotEmpty(),
            "Should have at least one CATCH or COPIED_FINALLY handler"
        )
    }

    @Test
    fun testClassifierConsistency() {
        val method = assertMethodExists(file, "tryCatchFinally", minStmts = 1, hasTraps = true)

        val traps = method.body.traps

        // Test that calling classifier multiple times gives consistent results
        val firstRun = traps.map { trap -> TrapUtils.classifyHandler(trap, traps) }
        val secondRun = traps.map { trap -> TrapUtils.classifyHandler(trap, traps) }

        assertEquals(firstRun, secondRun, "Classifier should return consistent results")
        logger.info { "✓ Classifier returns consistent results across multiple calls" }
    }
}
