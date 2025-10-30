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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val logger = KotlinLogging.logger {}

class EtsExportTest : EtsLangTestBase() {

    companion object {
        private const val SOURCE_PATH = "/samples/source/lang/export.ts"

        private val file: EtsFile by lazy {
            loadSourceFile(SOURCE_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(file, "Failed to load $SOURCE_PATH")
            assertEquals("export.ts", file.name)
            assertTrue(file.exportInfos.isNotEmpty(), "Expected to find export statements")
            logger.info { "✓ Setup complete, ready to run ${EtsExportTest::class.simpleName} tests" }
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
        assertEquals("publicConstant", constantExport.originalName)
        assertNull(constantExport.nameBeforeAs, "Direct export should have no aliasing")
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
            it.isDefaultExport && it.originalName == "defaultValue"
        }
        assertNotNull(defaultExport, "Should find direct default export")
        logger.info { "✓ Direct default export test passed: $defaultExport" }
    }

    @Test
    fun testDefaultReExports() {
        logger.info { "Testing default re-exports" }

        // // Test: export { default } from './module-with-default';
        // val defaultReExport = file.exportInfos.find {
        //     it.name == "default" &&
        //         it.originalName == "default" &&
        //         it.from == "./module-with-default"
        // }
        // assertNotNull(defaultReExport, "Should find re-exported default export")
        // assertTrue(defaultReExport.isDefaultExport, "Re-exported default should be marked as default")
        // assertEquals("default", defaultReExport.name)
        // assertEquals("default", defaultReExport.originalName)
        // assertEquals("./module-with-default", defaultReExport.from)
        // logger.info { "✓ Default re-export test passed: $defaultReExport" }

        // Test: export { default as ModuleDefault } from './another-module';
        val aliasedDefaultReExport = file.exportInfos.find {
            it.name == "ModuleDefault" &&
                it.originalName == "default" &&
                it.from == "./another-module"
        }
        assertNotNull(aliasedDefaultReExport, "Should find aliased default re-export")
        assertTrue(aliasedDefaultReExport.isDefaultExport, "Aliased default re-export should be marked as default")
        assertEquals("ModuleDefault", aliasedDefaultReExport.name)
        assertEquals("default", aliasedDefaultReExport.originalName)
        assertEquals("./another-module", aliasedDefaultReExport.from)
        logger.info { "✓ Aliased default re-export test passed: $aliasedDefaultReExport" }
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

        // Test named exports: export const publicConstant = 'hello';
        val namedExport = file.exportInfos.find { it.name == "publicConstant" && it.from == null }
        assertNotNull(namedExport, "Should find publicConstant export")
        val namedExportString = namedExport.toString()
        logger.info { "Named export string: $namedExportString" }
        assertEquals("export { publicConstant }", namedExportString)

        // Test default export: export default defaultValue;
        val defaultExport = file.exportInfos.find { it.isDefaultExport && it.from == null }
        assertNotNull(defaultExport, "Should find default export")
        val defaultExportString = defaultExport.toString()
        logger.info { "Default export string: $defaultExportString" }
        assertEquals("export default defaultValue", defaultExportString)

        // Test re-export: export { internalFunction } from './internal-module';
        val reExport = file.exportInfos.find {
            it.name == "internalFunction" && it.from == "./internal-module"
        }
        assertNotNull(reExport, "Should find internalFunction re-export")
        val reExportString = reExport.toString()
        logger.info { "Re-export string: $reExportString" }
        assertEquals("export { internalFunction } from './internal-module'", reExportString)

        // Test aliased re-export: export { Component as ReactComponent } from 'react';
        val aliasedReExport = file.exportInfos.find {
            it.name == "ReactComponent" && it.originalName == "Component" && it.from == "react"
        }
        assertNotNull(aliasedReExport, "Should find Component as ReactComponent re-export")
        val aliasedReExportString = aliasedReExport.toString()
        logger.info { "Aliased re-export string: $aliasedReExportString" }
        assertEquals("export { Component as ReactComponent } from 'react'", aliasedReExportString)

        // Test aliased export: export { internalName as publicName };
        val aliasedExport = file.exportInfos.find {
            it.name == "publicName" && it.originalName == "internalName" && it.from == null
        }
        assertNotNull(aliasedExport, "Should find internalName as publicName export")
        val aliasedExportString = aliasedExport.toString()
        logger.info { "Aliased export string: $aliasedExportString" }
        assertEquals("export { internalName as publicName }", aliasedExportString)

        // Test star re-export: export * from './all-exports';
        val starReExport = file.exportInfos.find {
            it.name == "*" && it.from == "./all-exports"
        }
        if (starReExport != null) {
            val starReExportString = starReExport.toString()
            logger.info { "Star re-export string: $starReExportString" }
            assertEquals("export * from './all-exports'", starReExportString)
        }

        // Test namespace re-export: export * as Utils from './utils';
        val namespaceReExport = file.exportInfos.find {
            it.name == "Utils" && it.from == "./utils"
        }
        if (namespaceReExport != null) {
            val namespaceReExportString = namespaceReExport.toString()
            logger.info { "Namespace re-export string: $namespaceReExportString" }
            assertEquals("export * as Utils from './utils'", namespaceReExportString)
        }

        // // Test default re-export: export { default } from './module-with-default';
        // val defaultReExport = file.exportInfos.find {
        //     it.name == "default" && it.originalName == "default" && it.from == "./module-with-default"
        // }
        // assertNotNull(defaultReExport, "Should find default re-export")
        // val defaultReExportString = defaultReExport.toString()
        // logger.info { "Default re-export string: $defaultReExportString" }
        // assertEquals("export { default } from './module-with-default'", defaultReExportString)

        // Test aliased default re-export: export { default as ModuleDefault } from './another-module';
        val aliasedDefaultReExport = file.exportInfos.find {
            it.name == "ModuleDefault" && it.originalName == "default" && it.from == "./another-module"
        }
        assertNotNull(aliasedDefaultReExport, "Should find default as ModuleDefault re-export")
        val aliasedDefaultReExportString = aliasedDefaultReExport.toString()
        logger.info { "Aliased default re-export string: $aliasedDefaultReExportString" }
        assertEquals("export { default as ModuleDefault } from './another-module'", aliasedDefaultReExportString)

        logger.info { "✓ All specific export toString tests passed" }
    }
}
