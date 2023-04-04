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

    private fun checkAssembling(generatingArgs: List<String>): Int {
        val commonArs = listOf(
            "java",
            "-ea",
            "-jar",
            "build/libs/jacodb-analysis-1.0-SNAPSHOT.jar"
        )
        val resultArgs = commonArs.plus(generatingArgs)
        val assemblingResult = ProcessBuilder().command(resultArgs).start().waitFor()
        File("generated").deleteRecursively()
        return assemblingResult
    }

    @Test
    fun `analyse something`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
    }

    @Test
    fun checkBigAssembling() {
        assertEquals(0, checkAssembling(listOf("100", "1000", "10", "generated", "JavaLanguage", "true")))
    }

    @Test
    fun checkSmallAssembling() {
        assertEquals(0, checkAssembling(listOf("2", "1", "1", "generated", "JavaLanguage", "true")))
    }

    @Test
    fun checkWrongLanguageAssembling() {
        assertEquals(1, checkAssembling(listOf("2", "1", "1", "generated", "PythonLanguage", "true")))
    }

    @Test
    fun checkWrongNumOfArgsAssembling() {
        assertEquals(1, checkAssembling(listOf("2")))
        assertEquals(1, checkAssembling(listOf("2", "1")))
        assertEquals(1, checkAssembling(listOf("2", "1", "1")))
        assertEquals(1, checkAssembling(listOf("2", "1", "1", "generated")))
        assertEquals(1, checkAssembling(listOf("2", "1", "1", "generated", "JavaLanguage", "true", "nothing")))
    }

    @Test
    fun checkWrongBoundsOfArgsAssembling() {
        assertEquals(1, checkAssembling(listOf("1", "1", "1", "generated", "JavaLanguage", "true")))
        assertEquals(1, checkAssembling(listOf("1001", "1", "1", "generated", "JavaLanguage", "true")))
        assertEquals(1, checkAssembling(listOf("2", "0", "1", "generated", "JavaLanguage", "true")))
        assertEquals(1, checkAssembling(listOf("2", "1", "256", "generated", "JavaLanguage", "true")))
        assertEquals(1, checkAssembling(listOf("2", "1", "-1", "generated", "JavaLanguage", "true")))
    }
}