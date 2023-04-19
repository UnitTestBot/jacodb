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
import org.jacodb.analysis.codegen.main
import org.junit.jupiter.api.assertThrows

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)
    private fun testAssemble(
        generatingArgs: List<String>
    ) {
        val errorFile =
            File("src\\test\\kotlin\\org\\jacodb\\analysis\\impl\\errorsOutput".replace('\\', File.separatorChar))
        val commonArs = listOf(
            "java",
            "-ea",
            "-jar",
            "build/libs/jacodb-analysis-1.0-SNAPSHOT.jar"
        )
        val resultArgs = commonArs.plus(generatingArgs)
        val process = ProcessBuilder().redirectError(ProcessBuilder.Redirect.INHERIT).command(resultArgs).start()
        val assemblingResult = process.waitFor()
        var error: List<String>? = null
        if (assemblingResult != 0) {
            error = errorFile.readLines()
        }
        File("generated").deleteRecursively()
        errorFile.delete()
        assertEquals(0, assemblingResult, error?.joinToString(System.lineSeparator()))
    }

    private inline fun <reified T : Throwable> testThrows(exceptionMessage: String, args: Array<String>) {
        val exception = assertThrows<T> {
            main(args)
        }
        assertEquals(exceptionMessage, exception.message)
    }

    @Test
    fun `analyse something`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
    }

    @Test
    fun checkBigAssembling() {
        testAssemble(listOf("100", "1000", "10", "generated", "JavaLanguage", "true"))
    }

    @Test
    fun checkSmallAssembling() {
        testAssemble(listOf("2", "1", "1", "generated", "JavaLanguage", "true"))
    }

    @Test
    fun checkWrongLanguageAssembling() {
        testThrows<NoSuchElementException>(
            exceptionMessage = "Collection contains no element matching the predicate.",
            args = arrayOf("2", "1", "1", "generated", "PythonLanguage", "true")
        )
    }

    @Test
    fun checkWrongNumOfArgsAssembling() {
        val exceptionMessage =
            "vertices:Int edges:Int vulnerabilities:Int pathToProjectDir:String targetLanguage:String [clearTargetDir: Boolean]"
        testThrows<AssertionError>(exceptionMessage = exceptionMessage, args = arrayOf("2"))
        testThrows<AssertionError>(exceptionMessage = exceptionMessage, args = arrayOf("2", "1"))
        testThrows<AssertionError>(exceptionMessage = exceptionMessage, args = arrayOf("2", "1", "1"))
        testThrows<AssertionError>(exceptionMessage = exceptionMessage, args = arrayOf("2", "1", "1", "generated"))
        testThrows<AssertionError>(
            exceptionMessage = exceptionMessage,
            args = arrayOf("2", "1", "1", "2", "1", "1", "generated", "JavaLanguage", "true", "nothing")
        )
    }

    @Test
    fun checkWrongBoundsOfArgsAssembling() {
        val nExceptionMessage = "currently big graphs not supported just in case"
        val mExceptionMessage = "though we permit duplicated edges, do not overflow graph too much"
        val kExceptionMessage = "Assertion failed"
        testThrows<AssertionError>(
            exceptionMessage = nExceptionMessage,
            args = arrayOf("1", "1", "1", "generated", "JavaLanguage", "true")
        )
        testThrows<AssertionError>(
            exceptionMessage = nExceptionMessage,
            args = arrayOf("1001", "1", "1", "generated", "JavaLanguage", "true")
        )
        testThrows<AssertionError>(
            exceptionMessage = mExceptionMessage,
            args = arrayOf("2", "0", "1", "generated", "JavaLanguage", "true")
        )
        testThrows<AssertionError>(
            exceptionMessage = kExceptionMessage,
            args = arrayOf("2", "1", "256", "generated", "JavaLanguage", "true")
        )
        testThrows<AssertionError>(
            exceptionMessage = kExceptionMessage,
            args = arrayOf("2", "1", "-1", "generated", "JavaLanguage", "true")
        )
    }
}