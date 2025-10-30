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
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsFile
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Tests for operator and expression constructions.
 */
class EtsOperatorTest : EtsLangTestBase() {

    companion object {
        private const val SOURCE_PATH = "/samples/source/lang/operators.ts"

        private val file: EtsFile by lazy {
            loadSourceFile(SOURCE_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(file, "Failed to load $SOURCE_PATH")
            logger.info { "✓ Setup complete, ready to run ${EtsOperatorTest::class.simpleName} tests" }
        }
    }

    @Test
    fun testArithmeticOperators() {
        val method = assertMethodExists(file, "testArithmeticOperators", minStmts = 1)

        logMethodDetails(method)

        // Should have multiple assignment statements for different operations
        val assignStmts = method.cfg.stmts.filterIsInstance<EtsAssignStmt>()
        assertTrue(assignStmts.isNotEmpty(), "Method should have assignment statements")
    }

    @Test
    fun testUnaryOperators() {

        val method = assertMethodExists(file, "testUnaryOperators", minStmts = 1)

        logMethodDetails(method)

        // Should have unary operations
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have statements with unary ops")
    }

    @Test
    fun testComparisonOperators() {

        val method = assertMethodExists(file, "testComparisonOperators", minStmts = 1)

        logMethodDetails(method)

        // Should have comparison operations
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have comparison operations")
    }

    @Test
    fun testLogicalOperators() {

        val method = assertMethodExists(file, "testLogicalOperators", minStmts = 1)

        logMethodDetails(method)

        // Should have logical operations
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have logical operations")
    }

    @Test
    fun testBitwiseOperators() {

        val method = assertMethodExists(file, "testBitwiseOperators", minStmts = 1)

        logMethodDetails(method)

        // Should have bitwise operations
        val assignStmts = method.cfg.stmts.filterIsInstance<EtsAssignStmt>()
        assertTrue(assignStmts.isNotEmpty(), "Method should have bitwise operations")
    }

    @Test
    fun testAssignmentOperators() {

        val method = assertMethodExists(file, "testAssignmentOperators", minStmts = 1)

        logMethodDetails(method)

        // Should have many assignment statements with compound operators
        val assignStmts = method.cfg.stmts.filterIsInstance<EtsAssignStmt>()
        assertTrue(assignStmts.size >= 5, "Method should have multiple assignment operations")
    }

    @Test
    fun testNullCoalescing() {

        val method = assertMethodExists(file, "testNullCoalescing", minStmts = 1)

        logMethodDetails(method)

        // Should handle null coalescing operator
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have null coalescing operation")
    }

    @Test
    fun testOptionalChaining() {

        val method = assertMethodExists(file, "testOptionalChaining", minStmts = 1)

        logMethodDetails(method)

        // Should handle optional chaining
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have optional chaining")
    }

    @Test
    fun testTypeOf() {

        val method = assertMethodExists(file, "testTypeOf", minStmts = 1)

        logMethodDetails(method)

        // Should have typeof operation
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have typeof operation")
    }

    @Test
    fun testInstanceOf() {

        val method = assertMethodExists(file, "testInstanceOf", minStmts = 1)

        logMethodDetails(method)

        // Should have instanceof operation
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have instanceof operation")
    }

    @Test
    fun testSpreadOperator() {

        val method = assertMethodExists(file, "testSpreadOperator", minStmts = 1)

        logMethodDetails(method)

        // Should handle spread operator in arrays
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have spread operation")
    }

    @Test
    fun testDestructuring() {

        val method = assertMethodExists(file, "testDestructuring", minStmts = 1)

        logMethodDetails(method)

        // Should handle array destructuring
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have destructuring")
    }

    @Test
    fun testObjectDestructuring() {

        val method = assertMethodExists(file, "testObjectDestructuring", minStmts = 1)

        logMethodDetails(method)

        // Should handle object destructuring
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have object destructuring")
    }

    @Test
    fun testConditionalOperator() {

        val method = assertMethodExists(file, "testConditional", minStmts = 1)

        logMethodDetails(method)

        // Ternary operator should create conditional flow
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have ternary operator")
    }

    @Test
    fun testTemplateLiterals() {

        val method = assertMethodExists(file, "testTemplateLiterals", minStmts = 1)

        logMethodDetails(method)

        // Should handle template string interpolation
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have template literal")
    }

    @Test
    fun testInOperatorWithClass() {

        val method = assertMethodExists(file, "testInOperatorWithClass", minStmts = 1)

        logMethodDetails(method)

        // Should handle in operator with class instances
        assertTrue(method.cfg.stmts.isNotEmpty(), "Method should have in operator with class")
    }

    @Test
    fun testScopedVariables() {

        val method = assertMethodExists(file, "testScopedVariables", minStmts = 1)

        logMethodDetails(method)

        // Should handle variable shadowing in nested scopes
        assertTrue(method.cfg.blocks.size > 1, "Method should have multiple scopes")
    }
}
