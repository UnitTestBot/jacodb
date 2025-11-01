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
import org.junit.jupiter.api.Disabled
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
        assertEquals(1, traps.size, "Simple try-catch should have exactly 1 trap")

        val trap = traps[0]
        val classification = TrapUtils.classifyHandler(trap, traps)

        assertEquals(
            TrapUtils.HandlerKind.UNKNOWN,
            classification,
            "Simple try-catch handler should be classified as UNKNOWN"
        )
        logger.info { "✓ Handler correctly classified as UNKNOWN in simpleTryCatch" }
    }

    @Test
    fun testTryFinally() {
        val method = assertMethodExists(file, "tryFinally", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertEquals(1, traps.size, "Try-finally should have exactly 1 trap")

        val trap = traps[0]
        val classification = TrapUtils.classifyHandler(trap, traps)

        assertEquals(
            TrapUtils.HandlerKind.COPIED_FINALLY,
            classification,
            "Try-finally handler should be classified as COPIED_FINALLY"
        )
        logger.info { "✓ Handler correctly classified as COPIED_FINALLY in tryFinally" }
    }

    @Test
    fun testTryCatchFinally() {
        val method = assertMethodExists(file, "tryCatchFinally", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertEquals(2, traps.size, "Try-catch-finally should have exactly 2 traps")

        val classifications = traps.map { trap -> TrapUtils.classifyHandler(trap, traps) }

        assertTrue(
            classifications.contains(TrapUtils.HandlerKind.CATCH),
            "Should have one CATCH handler"
        )
        assertTrue(
            classifications.contains(TrapUtils.HandlerKind.COPIED_FINALLY),
            "Should have one COPIED_FINALLY handler"
        )

        logger.info { "✓ Found both CATCH and COPIED_FINALLY handlers in tryCatchFinally" }
    }

    @Test
    fun testCatchWithRethrow() {
        val method = assertMethodExists(file, "catchWithRethrow", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertEquals(1, traps.size, "Catch with rethrow should have exactly 1 trap")

        val trap = traps[0]
        val classification = TrapUtils.classifyHandler(trap, traps)

        assertEquals(
            TrapUtils.HandlerKind.COPIED_FINALLY,
            classification,
            "Catch with rethrow should be classified as COPIED_FINALLY"
        )
        logger.info { "✓ Handler correctly classified as COPIED_FINALLY in catchWithRethrow" }
    }

    @Disabled("ArkAnalyze drops traps when 'finally' contains 'return'")
    @Test
    fun testFinallyOverridesReturn() {
        val method = assertMethodExists(file, "finallyOverridesReturn", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertEquals(1, traps.size, "Finally that overrides return should have exactly 1 trap")

        val trap = traps[0]
        val classification = TrapUtils.classifyHandler(trap, traps)

        assertEquals(
            TrapUtils.HandlerKind.COPIED_FINALLY,
            classification,
            "Finally that overrides return should be classified as COPIED_FINALLY"
        )
        logger.info { "✓ Handler correctly classified as COPIED_FINALLY in finallyOverridesReturn" }
    }

    @Test
    fun testComplexTryCatchFinally() {
        val method = assertMethodExists(file, "complexTryCatchFinally", minStmts = 1, hasTraps = true)
        logMethodDetails(method)

        val traps = method.body.traps
        assertEquals(2, traps.size, "Complex try-catch-finally should have exactly 2 traps")

        val classifications = traps.map { trap -> TrapUtils.classifyHandler(trap, traps) }

        assertTrue(
            classifications.contains(TrapUtils.HandlerKind.CATCH),
            "Should have one CATCH handler"
        )
        assertTrue(
            classifications.contains(TrapUtils.HandlerKind.COPIED_FINALLY),
            "Should have one COPIED_FINALLY handler"
        )

        logger.info { "✓ Found both CATCH and COPIED_FINALLY handlers in complexTryCatchFinally" }
    }

    @Test
    fun testClassifierConsistency() {
        val method = assertMethodExists(file, "tryCatchFinally", minStmts = 1, hasTraps = true)

        val traps = method.body.traps

        val firstRun = traps.map { trap -> TrapUtils.classifyHandler(trap, traps) }
        val secondRun = traps.map { trap -> TrapUtils.classifyHandler(trap, traps) }

        assertEquals(firstRun, secondRun, "Classifier should return consistent results")
        logger.info { "✓ Classifier returns consistent results across multiple calls" }
    }
}
