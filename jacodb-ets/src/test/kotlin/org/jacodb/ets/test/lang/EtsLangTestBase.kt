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
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.test.utils.getResourcePath
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

/**
 * Base class for language construction tests.
 * Provides utilities for loading files and finding methods.
 */
abstract class EtsLangTestBase {

    companion object {
        /**
         * Load a TypeScript file from test resources and convert it to EtsFile.
         * This static version can be used in @BeforeAll methods.
         */
        @JvmStatic
        fun loadSourceFile(relativePath: String): EtsFile {
            val path = getResourcePath(relativePath)
            logger.info { "Loading file: $relativePath" }
            val file = loadEtsFileAutoConvert(path)
            logger.info {
                "Loaded file with ${
                    file.allClasses.size
                } classes and ${
                    file.allClasses.sumOf { it.methods.size }
                } methods"
            }
            return file
        }
    }

    /**
     * Find a method by name in the file.
     */
    protected fun EtsFile.findMethod(name: String): EtsMethod {
        val method = allClasses.flatMap { it.methods }.firstOrNull { it.name == name }
        assertNotNull(method, "Method '$name' not found in file")
        return method
    }

    /**
     * Find all methods matching a predicate.
     */
    protected fun EtsFile.findMethods(predicate: (EtsMethod) -> Boolean): List<EtsMethod> {
        return allClasses.flatMap { it.methods }.filter(predicate)
    }

    /**
     * Assert that a method exists and has specific properties.
     */
    protected fun assertMethodExists(
        file: EtsFile,
        name: String,
        minStmts: Int = 0,
        hasTraps: Boolean? = null,
    ): EtsMethod {
        val method = file.findMethod(name)

        logger.info {
            "Method '$name': ${
                method.cfg.stmts.size
            } statements, ${
                method.body.traps.size
            } traps"
        }

        if (minStmts > 0) {
            assertTrue(
                method.cfg.stmts.size >= minStmts,
                "Method '$name' should have at least $minStmts statements, but has ${method.cfg.stmts.size}"
            )
        }

        if (hasTraps != null) {
            val actualHasTraps = method.body.traps.isNotEmpty()
            assertEquals(
                hasTraps,
                actualHasTraps,
                "Method '$name' should ${if (hasTraps) "have" else "not have"} exception traps"
            )
        }

        return method
    }

    /**
     * Log detailed information about a method's CFG.
     */
    protected fun logMethodDetails(method: EtsMethod) {
        logger.info {
            buildString {
                appendLine("Method: ${method.signature}")
                appendLine("  Parameters: ${method.parameters.size}")
                appendLine("  Statements: ${method.cfg.stmts.size}")
                appendLine("  Blocks: ${method.cfg.blocks.size}")
                appendLine("  Traps: ${method.body.traps.size}")
                if (method.body.traps.isNotEmpty()) {
                    for ((idx, trap) in method.body.traps.withIndex()) {
                        appendLine("    Trap $idx: tryBlocks=${trap.tryBlocks.size}, catchBlocks=${trap.catchBlocks.size}")
                    }
                }
                appendLine("  Statements:")
                for ((idx, stmt) in method.cfg.stmts.withIndex()) {
                    appendLine("    [$idx] $stmt")
                }
            }
        }
    }
}
