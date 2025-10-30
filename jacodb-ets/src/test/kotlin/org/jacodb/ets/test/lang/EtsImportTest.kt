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

class EtsImportTest : EtsLangTestBase() {

    companion object {
        private const val SOURCE_PATH = "/samples/source/lang/import.ts"

        private val file: EtsFile by lazy {
            loadSourceFile(SOURCE_PATH)
        }

        @BeforeAll
        @JvmStatic
        fun setup() {
            assertNotNull(file, "Failed to load $SOURCE_PATH")
            assertEquals("import.ts", file.name)
            assertTrue(file.importInfos.isNotEmpty(), "Expected to find import statements")
            logger.info { "✓ Setup complete, ready to run ${EtsImportTest::class.simpleName} tests" }
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
        assertTrue(reactImport.isDefaultImport, "React import should be marked as default")
        assertNull(reactImport.nameBeforeAs, "React import is not aliased")
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
        assertNull(useStateImport.nameBeforeAs, "useState import is not aliased")
        assertNull(useEffectImport.nameBeforeAs, "useEffect import is not aliased")
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
        assertEquals("ReactComponent", aliasedImport.name)
        assertEquals("Component", aliasedImport.originalName)
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
        assertNull(cssSideEffectImport.nameBeforeAs, "Side effect import should not have aliasing")
        assertTrue(cssSideEffectImport.isSideEffectImport, "Import should be marked as side effect")
        logger.info { "✓ Side effect import test passed: $cssSideEffectImport" }
    }

    @Test
    fun testImportInfoToString() {
        logger.info { "Testing EtsImportInfo toString() method" }

        // Test default import: import React from 'react';
        val defaultImport = file.importInfos.find {
            it.name == "React" && it.from == "react" && it.isDefaultImport
        }
        assertNotNull(defaultImport, "Should find React default import")
        val defaultImportString = defaultImport.toString()
        logger.info { "Default import string: $defaultImportString" }
        assertEquals("import React from 'react'", defaultImportString)

        // Test named import: import { useState } from 'react';
        val namedImport = file.importInfos.find {
            it.name == "useState" && it.from == "react" && it.isNamedImport
        }
        assertNotNull(namedImport, "Should find useState named import")
        val namedImportString = namedImport.toString()
        logger.info { "Named import string: $namedImportString" }
        assertEquals("import { useState } from 'react'", namedImportString)

        // Test aliased import: import { Component as ReactComponent } from 'react';
        val aliasedImport = file.importInfos.find {
            it.name == "ReactComponent" && it.originalName == "Component" && it.from == "react"
        }
        assertNotNull(aliasedImport, "Should find Component as ReactComponent import")
        val aliasedImportString = aliasedImport.toString()
        logger.info { "Aliased import string: $aliasedImportString" }
        assertEquals("import { Component as ReactComponent } from 'react'", aliasedImportString)

        // Test namespace import: import * as Utils from './utils';
        val namespaceImport = file.importInfos.find {
            it.name == "Utils" && it.from == "./utils" && it.isNamespaceImport
        }
        assertNotNull(namespaceImport, "Should find Utils namespace import")
        val namespaceImportString = namespaceImport.toString()
        logger.info { "Namespace import string: $namespaceImportString" }
        assertEquals("import * as Utils from './utils'", namespaceImportString)

        // Test side effect import: import './styles.css';
        val sideEffectImport = file.importInfos.find {
            it.from == "./styles.css" && it.isSideEffectImport
        }
        assertNotNull(sideEffectImport, "Should find side effect import")
        val sideEffectImportString = sideEffectImport.toString()
        logger.info { "Side effect import string: $sideEffectImportString" }
        assertEquals("import './styles.css'", sideEffectImportString)

        // Test mixed imports from same module: import DefaultExport, { namedExport } from './module';
        val mixedDefaultImport = file.importInfos.find {
            it.name == "DefaultExport" && it.from == "./module" && it.isDefaultImport
        }
        val mixedNamedImport = file.importInfos.find {
            it.name == "namedExport" && it.from == "./module" && it.isNamedImport
        }
        assertNotNull(mixedDefaultImport, "Should find DefaultExport from mixed import")
        assertNotNull(mixedNamedImport, "Should find namedExport from mixed import")

        val mixedDefaultString = mixedDefaultImport.toString()
        val mixedNamedString = mixedNamedImport.toString()
        logger.info { "Mixed default import string: $mixedDefaultString" }
        logger.info { "Mixed named import string: $mixedNamedString" }
        assertEquals("import DefaultExport from './module'", mixedDefaultString)
        assertEquals("import { namedExport } from './module'", mixedNamedString)

        logger.info { "✓ All specific import toString tests passed" }
    }
}
