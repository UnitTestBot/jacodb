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

import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.test.utils.getResourcePath
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnumTest {

    @Test
    fun testEnum() {
        val path = "/samples/source/lang/enum.ts"
        val res = getResourcePath(path)
        val file = loadEtsFileAutoConvert(res)

        // Count enum classes
        val enumClasses = file.allClasses.filter { it.category == EtsClassCategory.ENUM }
        assertTrue(enumClasses.size >= 5, "File should have multiple enums")

        // Check for specific enum types
        val basicEnum = enumClasses.find { it.name == "BasicEnum" }
        val stringEnum = enumClasses.find { it.name == "StringEnum" }
        val mixedEnum = enumClasses.find { it.name == "MixedEnum" }

        assertNotNull(basicEnum, "Should find BasicEnum")
        assertNotNull(stringEnum, "Should find StringEnum")
        assertNotNull(mixedEnum, "Should find MixedEnum")

        // Verify field counts
        assertEquals(3, basicEnum.fields.size, "BasicEnum should have 3 fields")
        assertEquals(3, stringEnum.fields.size, "StringEnum should have 3 fields")
        assertEquals(3, mixedEnum.fields.size, "MixedEnum should have 3 fields")
    }

    @Test
    fun testEnumEdgeCases() {
        val path = "/samples/source/lang/enum-edge-cases.ts"
        val res = getResourcePath(path)
        val file = loadEtsFileAutoConvert(res)

        val enumClasses = file.allClasses.filter { it.category == EtsClassCategory.ENUM }
        assertTrue(enumClasses.isNotEmpty(), "Edge cases file should have enums")

        // Check for specific edge case enums
        val emptyEnum = enumClasses.find { it.name == "EmptyEnum" }
        val singleEnum = enumClasses.find { it.name == "SingleEnum" }
        val floatEnum = enumClasses.find { it.name == "FloatEnum" }

        assertNotNull(emptyEnum, "Should find EmptyEnum")
        assertNotNull(singleEnum, "Should find SingleEnum")
        assertNotNull(floatEnum, "Should find FloatEnum")

        // Verify edge case properties
        assertEquals(0, emptyEnum.fields.size, "EmptyEnum should have no fields")
        assertEquals(1, singleEnum.fields.size, "SingleEnum should have 1 field")
        assertEquals(3, floatEnum.fields.size, "FloatEnum should have 3 fields")
    }

    @Test
    fun testEnumModules() {
        val path = "/samples/source/lang/enum-modules.ts"
        val res = getResourcePath(path)
        val file = loadEtsFileAutoConvert(res)

        val enumClasses = file.allClasses.filter { it.category == EtsClassCategory.ENUM }
        assertTrue(enumClasses.isNotEmpty(), "Modules file should have enums")

        // Check for exported enums
        val publicEnum = enumClasses.find { it.name == "PublicEnum" }
        val constExportEnum = enumClasses.find { it.name == "ConstExportEnum" }

        assertNotNull(publicEnum, "Should find PublicEnum")
        assertNotNull(constExportEnum, "Should find ConstExportEnum")

        // Verify exported enum properties
        assertEquals(3, publicEnum.fields.size, "PublicEnum should have 3 fields")
        assertEquals(3, constExportEnum.fields.size, "ConstExportEnum should have 3 fields")
    }

    @Test
    fun testEnumUsageInClasses() {
        val path = "/samples/source/lang/enum.ts"
        val res = getResourcePath(path)
        val file = loadEtsFileAutoConvert(res)

        // Find class that uses enums
        val usageClass = file.allClasses.find { it.name == "EnumUsageExamples" }
        assertNotNull(usageClass, "Should find EnumUsageExamples class")

        // Verify the class has methods that work with enums
        val methods = usageClass.methods
        assertTrue(methods.any { it.name == "setColor" }, "Should have setColor method")
        assertTrue(methods.any { it.name == "getDirection" }, "Should have getDirection method")
        assertTrue(methods.any { it.name == "handleDirection" }, "Should have handleDirection method")

        // Check method parameter counts
        val setColorMethod = methods.find { it.name == "setColor" }
        assertNotNull(setColorMethod, "setColor method should exist")
        assertEquals(1, setColorMethod.parameters.size, "setColor should have 1 parameter")

        val getDirectionMethod = methods.find { it.name == "getDirection" }
        assertNotNull(getDirectionMethod, "getDirection method should exist")
        assertEquals(0, getDirectionMethod.parameters.size, "getDirection should have no parameters")
    }

    @Test
    fun testEnumVsClassDistinction() {
        val path = "/samples/source/lang/enum.ts"
        val res = getResourcePath(path)
        val file = loadEtsFileAutoConvert(res)

        // Count different class categories
        val enumCount = file.allClasses.count { it.category == EtsClassCategory.ENUM }
        val classCount = file.allClasses.count { it.category == EtsClassCategory.CLASS }

        assertTrue(enumCount > 0, "Should have enum classes")
        assertTrue(classCount > 0, "Should have regular classes")

        // Verify proper categorization
        val basicEnum = file.allClasses.find { it.name == "BasicEnum" }
        val usageClass = file.allClasses.find { it.name == "EnumUsageExamples" }

        assertNotNull(basicEnum, "Should find BasicEnum")
        assertNotNull(usageClass, "Should find EnumUsageExamples")

        assertEquals(EtsClassCategory.ENUM, basicEnum.category, "BasicEnum should be categorized as ENUM")
        assertEquals(EtsClassCategory.CLASS, usageClass.category, "EnumUsageExamples should be categorized as CLASS")
    }
}
