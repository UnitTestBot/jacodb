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
import org.jacodb.ets.test.utils.getResourcePath
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

class EtsImportTest {

    companion object {
        private const val TS_PATH = "/samples/source/lang/import.ts"

        private val file: EtsFile by lazy {
            logger.info { "Loading sample file: $TS_PATH" }
            val path = getResourcePath(TS_PATH)
            val file = loadEtsFileAutoConvert(path)

            logger.info { "Loaded ETS file: ${file.name}" }
            logger.info { "Found ${file.importInfos.size} import statements" }

            // Print all imports for debugging
            file.importInfos.forEachIndexed { index, importInfo ->
                logger.info { "Import $index: $importInfo" }
            }

            file
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Verify the file was loaded correctly
            assertNotNull(file)
            assertEquals("import.ts", file.name)

            // Verify we have import information
            assertTrue(file.importInfos.isNotEmpty(), "Expected to find import statements")

            logger.info { "✓ Setup complete, ready to run tests on imports" }
        }
    }

    @Test
    fun testDefaultImport() {
        logger.info { "Testing default imports" }

        // Test: import React from 'react';
        val reactImport = file.importInfos.find {
            it.name == "React" && it.from == "react"
        }
        assertNotNull(reactImport, "Should find React default import")
        assertTrue(reactImport.isDefault, "React import should be marked as default")
        assertEquals("React", reactImport.name)
        logger.info { "✓ Default import test passed: $reactImport" }
    }

    @Test
    fun testNamedImports() {
        logger.info { "Testing named imports" }

        // Test: import { useState, useEffect } from 'react';
        val useStateImport = file.importInfos.find {
            it.name == "useState" && it.from == "react"
        }
        val useEffectImport = file.importInfos.find {
            it.name == "useEffect" && it.from == "react"
        }

        assertNotNull(useStateImport, "Should find useState named import")
        assertNotNull(useEffectImport, "Should find useEffect named import")
        assertEquals("useState", useStateImport.name)
        assertEquals("useEffect", useEffectImport.name)
        logger.info { "✓ Named imports test passed: useState, useEffect" }
    }

    @Test
    fun testAliasedImports() {
        logger.info { "Testing aliased imports" }

        // Test: import { Component as ReactComponent } from 'react';
        val aliasedImport = file.importInfos.find {
            it.name == "ReactComponent" &&
                it.originalName == "Component" &&
                it.from == "react"
        }
        assertNotNull(aliasedImport, "Should find Component as ReactComponent import")
        assertEquals("Component", aliasedImport.originalName)
        assertEquals("ReactComponent", aliasedImport.name)
        logger.info { "✓ Aliased import test passed: $aliasedImport" }
    }

    @Test
    fun testNamespaceImports() {
        logger.info { "Testing namespace imports" }

        // Test: import * as Utils from './utils';
        val namespaceImport = file.importInfos.find {
            it.name == "Utils" && it.from == "./utils"
        }
        assertNotNull(namespaceImport, "Should find Utils namespace import")
        assertEquals("Utils", namespaceImport.name)
        logger.info { "✓ Namespace import test passed: $namespaceImport" }
    }

    @Test
    fun testMixedImports() {
        logger.info { "Testing mixed imports" }

        // Test: import DefaultExport, { namedExport } from './module';
        val defaultExport = file.importInfos.find {
            it.name == "DefaultExport" && it.from == "./module"
        }
        val namedExport = file.importInfos.find {
            it.name == "namedExport" && it.from == "./module"
        }

        assertNotNull(defaultExport, "Should find DefaultExport from mixed import")
        assertNotNull(namedExport, "Should find namedExport from mixed import")
        logger.info { "✓ Mixed imports test passed: DefaultExport, namedExport" }
    }

    @Test
    fun testSideEffectImports() {
        logger.info { "Testing side effect imports" }

        // Test: import './styles.css';
        val cssSideEffectImport = file.importInfos.find {
            it.from == "./styles.css" && it.isSideEffectImport
        }
        assertNotNull(cssSideEffectImport, "Should find side effect import for styles.css")
        assertEquals("", cssSideEffectImport.name, "Side effect import should have empty clause name")
        assertEquals(null, cssSideEffectImport.originalName, "Side effect import should have null original name")
        assertTrue(cssSideEffectImport.isSideEffectImport, "Import should be marked as side effect")
        logger.info { "✓ Side effect import test passed: $cssSideEffectImport" }
    }

    @Test
    fun testImportInfoToString() {
        logger.info { "Testing EtsImportInfo toString() method" }

        // Test toString format for different import types
        file.importInfos.forEach { importInfo ->
            val stringRepr = importInfo.toString()
            logger.info { "Import string representation: $stringRepr" }

            // Basic validation that toString contains expected elements
            assertTrue(stringRepr.contains("import"), "toString should contain 'import'")

            // Only side-effect imports don't have 'from' in their syntax
            if (!importInfo.isSideEffectImport) {
                assertTrue(stringRepr.contains("from"), "toString should contain 'from' for imports with source")
            }
        }

        logger.info { "✓ Import toString tests passed" }
    }
}
