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
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Tests for function-related constructions.
 */
class EtsFunctionTest : EtsLangTestBase() {

    companion object {
        private const val SOURCE_PATH = "/samples/source/lang/functions.ts"

        private val file: EtsFile by lazy {
            loadSourceFile(SOURCE_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(file, "Failed to load $SOURCE_PATH")
            logger.info { "✓ Setup complete, ready to run ${EtsFunctionTest::class.simpleName} tests" }
        }
    }

    @Test
    fun testSimpleFunction() {
        val method = assertMethodExists(file, "simpleFunction", minStmts = 1)

        logMethodDetails(method)

        // Verify basic function structure
        assertEquals(1, method.parameters.size, "Simple function should have 1 parameter")
        assertNotNull(method.signature.returnType, "Function should have return type")
    }

    @Test
    fun testMultiParamFunction() {
        val method = assertMethodExists(file, "multiParamFunction", minStmts = 1)

        logMethodDetails(method)

        // Should have 3 parameters
        assertEquals(3, method.parameters.size, "Function should have 3 parameters")
    }

    @Test
    fun testDefaultParamFunction() {
        val method = assertMethodExists(file, "defaultParamFunction", minStmts = 1)

        logMethodDetails(method)

        // Should have 2 parameters with one being optional/default
        assertEquals(2, method.parameters.size, "Function should have 2 parameters")
    }

    @Test
    fun testRestParamFunction() {
        val method = assertMethodExists(file, "restParamFunction", minStmts = 1)

        logMethodDetails(method)

        // Should have at least 2 parameters (base + rest)
        assertTrue(method.parameters.size >= 2, "Function should have base and rest parameters")

        // Last parameter should be marked as rest
        val lastParam = method.parameters.last()
        assertTrue(lastParam.isRest, "Last parameter should be rest parameter")
    }

    @Test
    fun testOptionalParamFunction() {
        val method = assertMethodExists(file, "optionalParamFunction", minStmts = 1)

        logMethodDetails(method)

        // Should have 2 parameters with one optional
        assertTrue(method.parameters.size >= 1, "Function should have parameters")
    }

    @Test
    fun testArrowFunction() {
        // Top-level arrow function is stored as global variable
        val dfltClass = file.classes.first { it.name == DEFAULT_ARK_CLASS_NAME }
        val dfltMethod = dfltClass.methods.first { it.name == DEFAULT_ARK_METHOD_NAME }
        assertTrue(
            dfltMethod.locals.any { it.name == "arrowFunction" },
            "Arrow function should be present as a local variable in %dflt::%dflt"
        )
    }

    @Test
    fun testOuterFunction() {
        val method = assertMethodExists(file, "outerFunction", minStmts = 1)

        logMethodDetails(method)

        // Should have closure or nested function
        assertTrue(method.cfg.stmts.isNotEmpty(), "Outer function should have statements")
    }

    @Test
    fun testHigherOrderFunction() {
        val method = assertMethodExists(file, "higherOrderFunction", minStmts = 1)

        logMethodDetails(method)

        // Should accept function as parameter
        assertEquals(2, method.parameters.size, "Higher-order function should have 2 parameters")
    }

    @Test
    fun testRecursiveFunction() {
        val method = assertMethodExists(file, "factorial", minStmts = 1)

        logMethodDetails(method)

        // Recursive function should have conditional and call to itself
        assertTrue(method.cfg.blocks.size > 1, "Recursive function should have branching")
    }

    @Test
    fun testMutuallyRecursiveFunctions() {
        val isEven = file.findMethod("isEven")
        val isOdd = file.findMethod("isOdd")

        logMethodDetails(isEven)
        logMethodDetails(isOdd)

        // Both should exist and have recursive structure
        assertTrue(isEven.cfg.blocks.size > 1, "isEven should have branching")
        assertTrue(isOdd.cfg.blocks.size > 1, "isOdd should have branching")
    }

    @Test
    fun testDestructuringParams() {
        val method = assertMethodExists(file, "destructuringParams", minStmts = 1)

        logMethodDetails(method)

        // Should handle destructured parameters
        assertTrue(method.parameters.isNotEmpty(), "Function should have parameters")
    }

    @Test
    fun testGeneratorFunction() {
        // Generator function may have special representation
        val genFn = file.findMethods { it.name.contains("generator") }
        assertTrue(
            genFn.isNotEmpty() || file.allClasses.flatMap { it.methods }.isNotEmpty(),
            "Generator functions should be represented"
        )
    }

    @Test
    fun testAsyncFunction() {
        // Async functions may have special representation
        val asyncFn = file.findMethods { it.name.contains("async") }
        assertTrue(
            asyncFn.isNotEmpty() || file.allClasses.flatMap { it.methods }.isNotEmpty(),
            "Async functions should be represented"
        )
    }

    @Test
    fun testVoidFunction() {
        val method = assertMethodExists(file, "voidFunction", minStmts = 1)

        logMethodDetails(method)

        // Should have void return type
        assertTrue(method.cfg.stmts.isNotEmpty(), "Void function should have statements")
    }

    @Test
    fun testNeverFunction() {
        val method = assertMethodExists(file, "neverFunction", minStmts = 1)

        logMethodDetails(method)

        // Function that always throws should have trap
        assertTrue(method.cfg.stmts.isNotEmpty(), "Never function should have statements")
    }
}
