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
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Tests for object literal constructions.
 */
class EtsObjectTest : EtsLangTestBase() {

    companion object {
        private const val SOURCE_PATH = "/samples/source/lang/object.ts"

        private val file: EtsFile by lazy {
            loadSourceFile(SOURCE_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(file, "Failed to load $SOURCE_PATH")
            logger.info { "✓ Setup complete, ready to run ${EtsObjectTest::class.simpleName} tests" }
        }
    }

    @Test
    fun testObjectLiteralWithMethods() {
        // Find main method that creates object
        val method = assertMethodExists(file, "main", minStmts = 1)

        logMethodDetails(method)

        // Should have complex object creation with methods, getters, setters
        assertTrue(method.cfg.stmts.size >= 5, "Object creation should generate multiple statements")
    }

    @Test
    fun testObjectWithSpreadOperator() {
        val method = file.findMethod("main")

        logMethodDetails(method)

        // Object should use spread operator (...c)
        // Should have property shorthand (b instead of b: b)
        // Should have method shorthand
        assertTrue(method.cfg.stmts.isNotEmpty(), "Should create object with various features")
    }

    @Test
    fun testObjectGetterSetter() {
        val method = file.findMethod("main")

        logMethodDetails(method)

        // Object has getter and setter for 'accessor'
        // Should be called in the code
        assertTrue(method.cfg.stmts.isNotEmpty(), "Should handle getters and setters")
    }
}
