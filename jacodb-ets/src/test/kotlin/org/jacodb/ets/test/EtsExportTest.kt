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

class EtsExportTest {

    companion object {
        private const val TS_PATH = "/samples/source/lang/export.ts"

        private val file: EtsFile by lazy {
            logger.info { "Loading sample file: $TS_PATH" }
            val path = getResourcePath(TS_PATH)
            val file = loadEtsFileAutoConvert(path)

            logger.info { "Loaded ETS file: ${file.name}" }
            logger.info { "Found ${file.exportInfos.size} export statements" }

            // Print all exports for debugging
            file.exportInfos.forEachIndexed { index, exportInfo ->
                logger.info { "Export $index: $exportInfo" }
            }

            file
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            // Verify the file was loaded correctly
            assertNotNull(file)
            assertEquals("export.ts", file.name)

            // Verify we have export information
            assertTrue(file.exportInfos.isNotEmpty(), "Expected to find export statements")

            logger.info { "✓ Setup complete, ready to run tests on exports" }
        }
    }

    @Test
    fun testNamedExports() {
        logger.info { "Testing named exports" }

        // Test: export const publicConstant = 'hello';
        val constantExport = file.exportInfos.find {
            it.name == "publicConstant" && it.from == null
        }
        assertNotNull(constantExport, "Should find publicConstant named export")
        assertEquals("publicConstant", constantExport.name)
        assertEquals(null, constantExport.originalName, "Direct export should have no original name")
        logger.info { "✓ Named constant export test passed: $constantExport" }

        // Test: export function publicFunction()
        val functionExport = file.exportInfos.find {
            it.name == "publicFunction" && it.from == null
        }
        assertNotNull(functionExport, "Should find publicFunction named export")
        assertEquals("publicFunction", functionExport.name)
        logger.info { "✓ Named function export test passed: $functionExport" }

        // Test: export class PublicClass
        val classExport = file.exportInfos.find {
            it.name == "PublicClass" && it.from == null
        }
        assertNotNull(classExport, "Should find PublicClass named export")
        assertEquals("PublicClass", classExport.name)
        logger.info { "✓ Named class export test passed: $classExport" }
    }

    @Test
    fun testDefaultExports() {
        logger.info { "Testing default exports" }

        // Test: export default defaultValue;
        val defaultExport = file.exportInfos.find {
            it.isDefaultExport
        }
        assertNotNull(defaultExport, "Should find default export")
        assertTrue(defaultExport.isDefaultExport, "Export should be marked as default")
        logger.info { "✓ Default export test passed: $defaultExport" }
    }

    @Test
    fun testReExports() {
        logger.info { "Testing re-exports" }

        // Test: export { internalFunction } from './internal-module';
        val reExport = file.exportInfos.find {
            it.name == "internalFunction" && it.from == "./internal-module"
        }
        assertNotNull(reExport, "Should find re-export from internal-module")
        assertEquals("internalFunction", reExport.name)
        assertEquals("./internal-module", reExport.from)
        logger.info { "✓ Re-export test passed: $reExport" }

        // Test: export * from './all-exports';
        val wildcardReExport = file.exportInfos.find {
            it.name == "*" && it.from == "./all-exports"
        }
        if (wildcardReExport != null) {
            assertEquals("./all-exports", wildcardReExport.from)
            logger.info { "✓ Wildcard re-export test passed: $wildcardReExport" }
        }
    }

    @Test
    fun testAliasedExports() {
        logger.info { "Testing aliased exports" }

        // Test: export { Component as ReactComponent } from 'react';
        val aliasedReExport = file.exportInfos.find {
            it.name == "ReactComponent" &&
                it.originalName == "Component" &&
                it.from == "react"
        }
        assertNotNull(aliasedReExport, "Should find Component as ReactComponent re-export")
        assertEquals("Component", aliasedReExport.originalName)
        assertEquals("ReactComponent", aliasedReExport.name)
        assertEquals("react", aliasedReExport.from)
        logger.info { "✓ Aliased re-export test passed: $aliasedReExport" }

        // Test: export { internalName as publicName };
        val aliasedExport = file.exportInfos.find {
            it.name == "publicName" &&
                it.originalName == "internalName" &&
                it.from == null
        }
        assertNotNull(aliasedExport, "Should find internalName as publicName export")
        assertEquals("internalName", aliasedExport.originalName)
        assertEquals("publicName", aliasedExport.name)
        logger.info { "✓ Aliased export test passed: $aliasedExport" }
    }

    @Test
    fun testNamespaceExports() {
        logger.info { "Testing namespace exports" }

        // Test: export * as Utils from './utils';
        val namespaceReExport = file.exportInfos.find {
            it.name == "Utils" && it.from == "./utils"
        }
        if (namespaceReExport != null) {
            assertEquals("Utils", namespaceReExport.name)
            assertEquals("./utils", namespaceReExport.from)
            logger.info { "✓ Namespace re-export test passed: $namespaceReExport" }
        }

        // Test: export { MyNamespace };
        val namespaceExport = file.exportInfos.find {
            it.name == "MyNamespace" && it.from == null
        }
        assertNotNull(namespaceExport, "Should find MyNamespace export")
        assertEquals("MyNamespace", namespaceExport.name)
        logger.info { "✓ Namespace export test passed: $namespaceExport" }
    }

    @Test
    fun testExportInfoToString() {
        logger.info { "Testing EtsExportInfo toString() method" }

        // Test toString format for different export types
        file.exportInfos.forEach { exportInfo ->
            val stringRepr = exportInfo.toString()
            logger.info { "Export string representation: $stringRepr" }

            // Basic validation that toString contains expected elements
            assertTrue(stringRepr.contains("export"), "toString should contain 'export'")

            // Re-exports should contain 'from'
            if (exportInfo.from != null) {
                assertTrue(stringRepr.contains("from"), "toString should contain 'from' for re-exports")
            }

            // Default exports should contain 'default'
            if (exportInfo.isDefaultExport) {
                assertTrue(stringRepr.contains("default"), "toString should contain 'default' for default exports")
            }
        }

        logger.info { "✓ Export toString tests passed" }
    }
}
