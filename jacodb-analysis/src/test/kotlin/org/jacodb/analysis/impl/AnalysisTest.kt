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

package org.jacodb.analysis.impl

import kotlinx.coroutines.runBlocking
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.usagesExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)
    private fun checkTestCase(
        generatingArgs: List<String>,
        shouldFail: Boolean = false,
        failMessage: String? = null) {
        val errorFile =
            File("src\\test\\kotlin\\org\\jacodb\\analysis\\impl\\errorsOutput".replace('\\', File.separatorChar))
        val commonArs = listOf(
            "java",
            "-ea",
            "-jar",
            "build/libs/jacodb-analysis-1.0-SNAPSHOT.jar"
        )
        val resultArgs = commonArs.plus(generatingArgs)
        val assemblingResult = ProcessBuilder().redirectError(errorFile).command(resultArgs).start().waitFor()
        if (!shouldFail) {
            var error: List<String>? = null
            if (assemblingResult != 0) {
                error = errorFile.readLines()
            }
            File("generated").deleteRecursively()
            errorFile.delete()
            assertEquals(0, assemblingResult, error?.joinToString("/n"))
        } else {
            File("generated").deleteRecursively()
            val error = errorFile.readLines()
            errorFile.delete()
            assertEquals(1, assemblingResult)
            assertEquals(error[0], failMessage)
        }
    }

    @Test
    fun `analyse something`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
    }

    @Test
    fun checkBigAssembling() {
        checkTestCase(listOf("100", "1000", "10", "generated", "JavaLanguage", "true"))
    }

    @Test
    fun checkSmallAssembling() {
        checkTestCase(listOf("2", "1", "1", "generated", "JavaLanguage", "true"))
    }

    @Test
    fun checkWrongLanguageAssembling() {
        val failMessage =
            "Exception in thread \"main\" java.util.NoSuchElementException: Collection contains no element matching the predicate."
        checkTestCase(
            listOf("2", "1", "1", "generated", "PythonLanguage", "true"),
            shouldFail = true,
            failMessage = failMessage
        )
    }

    @Test
    fun checkWrongNumOfArgsAssembling() {
        val failMessage =
            "Exception in thread \"main\" java.lang.AssertionError: vertices:Int edges:Int vulnerabilities:Int pathToProjectDir:String targetLanguage:String [clearTargetDir: Boolean]"
        checkTestCase(listOf("2"), shouldFail = true, failMessage = failMessage)
        checkTestCase(listOf("2", "1"), shouldFail = true, failMessage = failMessage)
        checkTestCase(listOf("2", "1", "1"), shouldFail = true, failMessage = failMessage)
        checkTestCase(listOf("2", "1", "1", "generated"), shouldFail = true, failMessage = failMessage)
        checkTestCase(
            listOf("2", "1", "1", "generated", "JavaLanguage", "true", "nothing"),
            shouldFail = true,
            failMessage = failMessage
        )
    }

    @Test
    fun checkWrongBoundsOfArgsAssembling() {
        val commonFailMessage = "Exception in thread \"main\" java.lang.AssertionError: "
        val nFailMessages = commonFailMessage + "currently big graphs not supported just in case"
        val mFailMessages = commonFailMessage + "though we permit duplicated edges, do not overflow graph too much"
        val kFailMessage = commonFailMessage + "Assertion failed"
        checkTestCase(
            listOf("1", "1", "1", "generated", "JavaLanguage", "true"),
            shouldFail = true,
            failMessage = nFailMessages
        )
        checkTestCase(
            listOf("1001", "1", "1", "generated", "JavaLanguage", "true"),
            shouldFail = true,
            failMessage = nFailMessages
        )
        checkTestCase(
            listOf("2", "0", "1", "generated", "JavaLanguage", "true"),
            shouldFail = true,
            failMessage = mFailMessages
        )
        checkTestCase(
            listOf("2", "1", "256", "generated", "JavaLanguage", "true"),
            shouldFail = true,
            failMessage = kFailMessage
        )
        checkTestCase(
            listOf("2", "1", "-1", "generated", "JavaLanguage", "true"),
            shouldFail = true,
            failMessage = kFailMessage
        )
    }
}