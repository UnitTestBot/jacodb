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
import org.jacodb.ets.model.EtsImportInfo
import org.jacodb.ets.test.utils.getResourcePath
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

class EtsImportTest {

    @Test
    fun testLoadImportTsFile() {
        logger.info { "Loading import.ts sample file and testing import information" }

        // Load the TypeScript file
        val tsFilePath = getResourcePath("/samples/source/lang/import.ts")
        val etsFile = loadEtsFileAutoConvert(tsFilePath)

        logger.info { "Loaded ETS file: ${etsFile.name}" }
        logger.info { "Found ${etsFile.importInfos.size} import statements" }

        // Verify the file was loaded correctly
        assertNotNull(etsFile)
        assertEquals("import.ts", etsFile.name)

        // Verify we have import information
        assertTrue(etsFile.importInfos.isNotEmpty(), "Expected to find import statements")

        // Print all imports for debugging
        etsFile.importInfos.forEachIndexed { index, importInfo ->
            logger.info { "Import $index: $importInfo" }
        }

        // Test specific import patterns
        testDefaultImport(etsFile.importInfos)
        testNamedImports(etsFile.importInfos)
        testAliasedImports(etsFile.importInfos)
        testNamespaceImports(etsFile.importInfos)
        testMixedImports(etsFile.importInfos)
        testSideEffectImports(etsFile.importInfos)
    }

    private fun testDefaultImport(imports: List<EtsImportInfo>) {
        // Test: import React from 'react';
        val reactImport = imports.find {
            it.clauseName == "React" && it.from == "react"
        }
        assertNotNull(reactImport, "Should find React default import")
        assertTrue(reactImport.isDefault, "React import should be marked as default")
        assertEquals("React", reactImport.importedName)
        logger.info { "✓ Default import test passed: $reactImport" }
    }

    private fun testNamedImports(imports: List<EtsImportInfo>) {
        // Test: import { useState, useEffect } from 'react';
        val useStateImport = imports.find {
            it.clauseName == "useState" && it.from == "react"
        }
        val useEffectImport = imports.find {
            it.clauseName == "useEffect" && it.from == "react"
        }

        assertNotNull(useStateImport, "Should find useState named import")
        assertNotNull(useEffectImport, "Should find useEffect named import")
        assertEquals("useState", useStateImport.importedName)
        assertEquals("useEffect", useEffectImport.importedName)
        logger.info { "✓ Named imports test passed: useState, useEffect" }
    }

    private fun testAliasedImports(imports: List<EtsImportInfo>) {
        // Test: import { Component as ReactComponent } from 'react';
        val aliasedImport = imports.find {
            it.clauseName == "ReactComponent" &&
                it.originalName == "Component" &&
                it.from == "react"
        }
        assertNotNull(aliasedImport, "Should find Component as ReactComponent import")
        assertEquals("Component", aliasedImport.importedName)
        assertEquals("ReactComponent", aliasedImport.clauseName)
        logger.info { "✓ Aliased import test passed: $aliasedImport" }
    }

    private fun testNamespaceImports(imports: List<EtsImportInfo>) {
        // Test: import * as Utils from './utils';
        val namespaceImport = imports.find {
            it.clauseName == "Utils" && it.from == "./utils"
        }
        assertNotNull(namespaceImport, "Should find Utils namespace import")
        assertEquals("Utils", namespaceImport.importedName)
        logger.info { "✓ Namespace import test passed: $namespaceImport" }
    }

    private fun testMixedImports(imports: List<EtsImportInfo>) {
        // Test: import DefaultExport, { namedExport } from './module';
        val defaultExport = imports.find {
            it.clauseName == "DefaultExport" && it.from == "./module"
        }
        val namedExport = imports.find {
            it.clauseName == "namedExport" && it.from == "./module"
        }

        assertNotNull(defaultExport, "Should find DefaultExport from mixed import")
        assertNotNull(namedExport, "Should find namedExport from mixed import")
        logger.info { "✓ Mixed imports test passed: DefaultExport, namedExport" }
    }

    private fun testSideEffectImports(imports: List<EtsImportInfo>) {
        // Test: import './styles.css';
        val cssSideEffectImport = imports.find {
            it.from == "./styles.css" && it.isSideEffectImport
        }
        assertNotNull(cssSideEffectImport, "Should find side effect import for styles.css")
        assertEquals("", cssSideEffectImport.clauseName, "Side effect import should have empty clause name")
        assertEquals("", cssSideEffectImport.importedName, "Side effect import should have empty imported name")
        assertTrue(cssSideEffectImport.isSideEffectImport, "Import should be marked as side effect")
        logger.info { "✓ Side effect import test passed: $cssSideEffectImport" }
    }

    @Test
    fun testImportInfoToString() {
        logger.info { "Testing EtsImportInfo toString() method" }

        val tsFilePath = getResourcePath("/samples/source/lang/import.ts")
        val etsFile = loadEtsFileAutoConvert(tsFilePath)

        // Test toString format for different import types
        etsFile.importInfos.forEach { importInfo ->
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
