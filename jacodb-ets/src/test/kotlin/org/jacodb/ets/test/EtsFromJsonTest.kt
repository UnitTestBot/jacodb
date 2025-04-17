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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import mu.KotlinLogging
import org.jacodb.ets.dto.AnyTypeDto
import org.jacodb.ets.dto.ClassSignatureDto
import org.jacodb.ets.dto.DecoratorDto
import org.jacodb.ets.dto.FieldDto
import org.jacodb.ets.dto.FieldSignatureDto
import org.jacodb.ets.dto.FileSignatureDto
import org.jacodb.ets.dto.LiteralTypeDto
import org.jacodb.ets.dto.LocalDto
import org.jacodb.ets.dto.MethodDto
import org.jacodb.ets.dto.NumberTypeDto
import org.jacodb.ets.dto.PrimitiveLiteralDto
import org.jacodb.ets.dto.RawStmtDto
import org.jacodb.ets.dto.RawTypeDto
import org.jacodb.ets.dto.RawValueDto
import org.jacodb.ets.dto.ReturnVoidStmtDto
import org.jacodb.ets.dto.StmtDto
import org.jacodb.ets.dto.TypeDto
import org.jacodb.ets.dto.ValueDto
import org.jacodb.ets.dto.dtoModule
import org.jacodb.ets.dto.toEtsLocal
import org.jacodb.ets.dto.toEtsMethod
import org.jacodb.ets.model.EtsAnyType
import org.jacodb.ets.model.EtsClassCategory
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsFileSignature
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsMethodSignature
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsUnknownType
import org.jacodb.ets.test.utils.getResourcePath
import org.jacodb.ets.test.utils.getResourcePathOrNull
import org.jacodb.ets.test.utils.loadEtsFileFromResource
import org.jacodb.ets.test.utils.loadEtsProjectFromResources
import org.jacodb.ets.test.utils.testFactory
import org.jacodb.ets.utils.DEFAULT_ARK_CLASS_NAME
import org.jacodb.ets.utils.DEFAULT_ARK_METHOD_NAME
import org.jacodb.ets.utils.loadEtsFileAutoConvert
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import kotlin.io.path.PathWalkOption
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.relativeTo
import kotlin.io.path.walk
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val logger = KotlinLogging.logger {}

class EtsFromJsonTest {

    companion object {
        private val json = Json {
            // classDiscriminator = "_"
            prettyPrint = true
            serializersModule = dtoModule
        }

        @JvmStatic
        private fun projectAvailable(res: String): Boolean {
            val path = getResourcePathOrNull(res)
            return path != null && path.exists()
        }

        private fun printProject(project: EtsScene) {
            logger.info {
                "Loaded project with ${project.projectClasses.size} classes and ${
                    project.projectClasses.sumOf { it.methods.size }
                } methods"
            }
            for (cls in project.projectClasses) {
                logger.info {
                    buildString {
                        appendLine("Class ${cls.name} has ${cls.methods.size} methods")
                        for (method in cls.methods) {
                            appendLine("- $method")
                        }
                    }
                }
            }
        }

        private fun printFile(file: EtsFile, showStmts: Boolean = false) {
            logger.info { "Loaded file $file with ${file.allClasses.size} classes" }
            for (cls in file.allClasses) {
                logger.info {
                    buildString {
                        appendLine("Class ${cls.name} has ${cls.methods.size} methods")
                        for (method in cls.methods) {
                            appendLine("- $method")
                            if (showStmts) {
                                for (stmt in method.cfg.stmts) {
                                    appendLine("  - $stmt")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testLoadEtsFileFromJson() {
        val path = "/samples/etsir/ast/save/basic.ts.json"
        val file = loadEtsFileFromResource(path)
        printFile(file, showStmts = true)
    }

    @Test
    fun testLoadEtsFileAutoConvert() {
        val path = "/samples/source/example.ts"
        val res = getResourcePath(path)
        val file = loadEtsFileAutoConvert(res)
        printFile(file, showStmts = true)
    }

    @TestFactory
    fun testLoadAllAvailableEtsFilesFromJson() = testFactory {
        val prefix = "/samples"
        val base = getResourcePathOrNull("$prefix/source") ?: run {
            logger.warn { "No samples directory found in resources" }
            return@testFactory
        }
        val availableFiles = base.walk(PathWalkOption.BREADTH_FIRST)
            .map { it.relativeTo(base) }
            .toList()
        logger.info {
            buildString {
                appendLine("Found ${availableFiles.size} sample files")
                for (path in availableFiles) {
                    appendLine("  - $path")
                }
            }
        }
        if (availableFiles.isEmpty()) {
            logger.warn { "No sample files found" }
            return@testFactory
        }
        container("load ${availableFiles.size} files") {
            for (path in availableFiles) {
                test("load $path") {
                    val file = loadEtsFileFromResource("$prefix/etsir/ast/$path.json")
                    printFile(file, showStmts = true)
                }
            }
        }
    }

    @TestFactory
    fun testLoadAllAvailableEtsFilesAutoConvert() = testFactory {
        val prefix = "/samples/source"
        val base = getResourcePathOrNull(prefix) ?: run {
            logger.warn { "No samples directory found in resources" }
            return@testFactory
        }
        val availableFiles = base.walk(PathWalkOption.BREADTH_FIRST)
            .map { it.relativeTo(base) }
            .toList()
        logger.info {
            buildString {
                appendLine("Found ${availableFiles.size} sample files")
                for (path in availableFiles) {
                    appendLine("  - $path")
                }
            }
        }
        if (availableFiles.isEmpty()) {
            logger.warn { "No sample files found" }
            return@testFactory
        }
        container("auto-load ${availableFiles.size} files") {
            for (path in availableFiles) {
                test("load $path") {
                    val p = getResourcePath("$prefix/$path")
                    val file = loadEtsFileAutoConvert(p)
                    printFile(file, showStmts = true)
                }
            }
        }
    }

    @Test
    fun testLoadEtsProject() {
        val res = "/projects/Demo_Calc"
        Assumptions.assumeTrue(projectAvailable(res)) { "Project not available: $res" }
        val modules = listOf("entry")
        val prefix = "$res/etsir"
        val project = loadEtsProjectFromResources(modules, prefix)
        printProject(project)
    }

    @TestFactory
    fun testLoadAllAvailableEtsProjects() = testFactory {
        val base = getResourcePathOrNull("/projects") ?: run {
            logger.warn { "No projects directory found in resources" }
            return@testFactory
        }
        val availableProjectNames = base.listDirectoryEntries()
            .filter { it.isDirectory() }
            .map { it.name }
            .sorted()
        logger.info {
            buildString {
                appendLine("Found ${availableProjectNames.size} projects")
                for (name in availableProjectNames) {
                    appendLine("  - $name")
                }
            }
        }
        if (availableProjectNames.isEmpty()) {
            logger.warn { "No projects found" }
            return@testFactory
        }
        container("load ${availableProjectNames.size} projects") {
            for (projectName in availableProjectNames) {
                test("load $projectName") {
                    dynamicLoadEtsProject(projectName)
                }
            }
        }
    }

    private fun dynamicLoadEtsProject(projectName: String) {
        logger.info { "Loading project: $projectName" }
        val projectPath = getResourcePath("/projects/$projectName")
        val etsirPath = projectPath / "etsir"
        if (!etsirPath.exists()) {
            logger.warn { "No etsir directory found for project $projectName" }
            return
        }
        val modules = etsirPath.listDirectoryEntries()
            .filter { it.isDirectory() }
            .map { it.name }
            .sorted()
        logger.info { "Found ${modules.size} modules: $modules" }
        if (modules.isEmpty()) {
            logger.warn { "No modules found for project $projectName" }
            return
        }
        val project = loadEtsProjectFromResources(modules, "/projects/$projectName/etsir")
        printProject(project)
    }

    @Test
    fun testLoadValueFromJson() {
        val jsonString = """
            {
              "name": "x",
              "type": {
                "_": "AnyType"
              }
            }
        """.trimIndent()
        val valueDto = Json.decodeFromString<LocalDto>(jsonString)
        logger.info { "valueDto = $valueDto" }
        assertEquals(LocalDto("x", AnyTypeDto), valueDto)
        val value = valueDto.toEtsLocal()
        logger.info { "value = $value" }
        assertEquals(EtsLocal("x", EtsAnyType), value)
    }

    @Test
    fun testLoadFieldFromJson() {
        val field = FieldDto(
            signature = FieldSignatureDto(
                declaringClass = ClassSignatureDto(
                    name = "TestClass",
                    declaringFile = FileSignatureDto(
                        projectName = "TestProject",
                        fileName = "test.ts",
                    )
                ),
                name = "x",
                type = NumberTypeDto,
            ),
            modifiers = 0,
            decorators = emptyList(),
            isOptional = true,
            isDefinitelyAssigned = false,
        )
        logger.info { "field = $field" }

        val jsonString = json.encodeToString(field)
        logger.info { "json: $jsonString" }

        val fieldDto = Json.decodeFromString<FieldDto>(jsonString)
        logger.info { "fieldDto = $fieldDto" }
        assertEquals(field, fieldDto)
    }

    @Test
    fun testLoadReturnVoidStmtFromJson() {
        val jsonString = """
            {
              "_": "ReturnVoidStmt"
            }
        """.trimIndent()
        val stmtDto = Json.decodeFromString<StmtDto>(jsonString)
        logger.info { "stmtDto = $stmtDto" }
        assertEquals(ReturnVoidStmtDto, stmtDto)
    }

    @Test
    fun testLoadMethodFromJson() {
        val jsonString = """
             {
               "signature": {
                 "declaringClass": {
                   "name": "$DEFAULT_ARK_CLASS_NAME",
                   "declaringFile": {
                     "projectName": "TestProject",
                     "fileName": "test.ts"
                   }
                 },
                 "name": "$DEFAULT_ARK_METHOD_NAME",
                 "parameters": [],
                 "returnType": {
                    "_": "UnknownType"
                  }
               },
               "modifiers": 0,
               "decorators": [],
               "typeParameters": [],
               "body": {
                 "locals": [],
                 "cfg": {
                   "blocks": [
                     {
                       "id": 0,
                       "successors": [],
                       "predecessors": [],
                       "stmts": [
                         {
                           "_": "ReturnVoidStmt"
                         }
                       ]
                     }
                   ]
                 }
               }
             }
        """.trimIndent()
        val methodDto = Json.decodeFromString<MethodDto>(jsonString)
        logger.info { "methodDto = $methodDto" }
        val method = methodDto.toEtsMethod()
        logger.info { "method = $method" }
        assertEquals(
            EtsMethodSignature(
                enclosingClass = EtsClassSignature(
                    name = DEFAULT_ARK_CLASS_NAME,
                    file = EtsFileSignature(
                        projectName = "TestProject",
                        fileName = "test.ts",
                    ),
                ),
                name = DEFAULT_ARK_METHOD_NAME,
                parameters = emptyList(),
                returnType = EtsUnknownType,
            ),
            method.signature
        )
        assertEquals(1, method.cfg.stmts.size)
        assertEquals(
            listOf(
                EtsReturnStmt(EtsStmtLocation(method, 0), null),
            ),
            method.cfg.stmts
        )
    }

    @Test
    fun testLoadDecoratorFromJson() {
        val jsonString = """
            {
              "kind": "cat"
            }
        """.trimIndent()
        val decoratorDto = Json.decodeFromString<DecoratorDto>(jsonString)
        logger.info { "decoratorDto = $decoratorDto" }
        assertEquals(DecoratorDto("cat"), decoratorDto)
        val jsonString2 = json.encodeToString(decoratorDto)
        logger.info { "json: $jsonString2" }
    }

    @Test
    fun testLoadLiteralTypeFromJson() {
        // TS: `let x: "hello" = "hello";`
        val jsonString = """
            {
              "_": "LiteralType",
              "literal": "hello"
            }
        """.trimIndent()
        val typeDto = Json.decodeFromString<TypeDto>(jsonString)
        logger.info { "typeDto = $typeDto" }
        assertIs<LiteralTypeDto>(typeDto)
        assertEquals(PrimitiveLiteralDto.StringLiteral("hello"), typeDto.literal)
    }

    @Test
    fun testLoadRawTypeFromJson() {
        val jsonString = """
            {
              "_": "DummyType",
              "value": 42
            }
        """.trimIndent()
        val typeDto = json.decodeFromString<TypeDto>(jsonString)
        logger.info { "typeDto = $typeDto" }
        assertIs<RawTypeDto>(typeDto)
        assertEquals("DummyType", typeDto.kind)
        assertEquals(42, typeDto.extra.getValue("value").jsonPrimitive.content.toInt())
    }

    @Test
    fun testLoadRawValueFromJson() {
        val jsonString = """
            {
              "_": "DummyValue",
              "value": 42,
              "type": { "_": "NumberType" }
            }
        """.trimIndent()
        val valueDto = json.decodeFromString<ValueDto>(jsonString)
        logger.info { "valueDto = $valueDto" }
        assertIs<RawValueDto>(valueDto)
        assertEquals("DummyValue", valueDto.kind)
        assertEquals(NumberTypeDto, valueDto.type)
        assertEquals(42, valueDto.extra.getValue("value").jsonPrimitive.content.toInt())
    }

    @Test
    fun testLoadRawStmtFromJson() {
        val jsonString = """
            {
              "_": "DummyStmt",
              "value": 42
            }
        """.trimIndent()
        val stmtDto = json.decodeFromString<StmtDto>(jsonString)
        logger.info { "stmtDto = $stmtDto" }
        assertIs<RawStmtDto>(stmtDto)
        assertEquals("DummyStmt", stmtDto.kind)
        assertEquals(42, stmtDto.extra.getValue("value").jsonPrimitive.content.toInt())
    }

    @Test
    fun testVararg() {
        val path = "/samples/etsir/ast/lang/vararg.ts.json"
        val file = loadEtsFileFromResource(path)
        val method = file.classes.flatMap { it.methods }.first { it.name == "f" }
        assertEquals(2, method.parameters.size)
        assertEquals(false, method.parameters[0].isRest)
        assertEquals(true, method.parameters[1].isRest)
    }

    @Test
    fun testClassCategory() {
        val path = "/samples/etsir/ast/lang/enum.ts.json"
        val file = loadEtsFileFromResource(path)
        val cls = file.classes.first { it.name == "Animal" }
        assertEquals(EtsClassCategory.ENUM, cls.category)
        assertEquals(2, cls.fields.size)
        assertEquals("Cat", cls.fields[0].name)
        assertEquals("Dog", cls.fields[1].name)
    }
}
