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

package parser

import org.jacodb.panda.dynamic.parser.ByteCodeParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ByteCodeParserTest {
    private val sampleFilePath = javaClass.getResource("/samples/ProgramByteCode.abc")?.path ?: ""
    private val bytes = FileInputStream(sampleFilePath).readBytes()
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    @Test
    fun parseAndPrintHeader() {
        val parser = ByteCodeParser(buffer)
        val header = parser.parseABC().header
        println(header)
    }

    @Test
    fun parseAndPrintClassIndex() {
        val parser = ByteCodeParser(buffer)
        val header = parser.parseABC().header
        val classIndex = header.classIndex
        println(classIndex)
    }

    @Test
    fun parseAndPrintMethods() {
        val parser = ByteCodeParser(buffer)
        val abc = parser.parseABC()
        val methods = abc.methods
        methods.forEach {
            println(it)
        }
    }

    // TODO: Convert int values to hex
    @Test
    fun validateHeader() {
        val expectedMagic = listOf(80, 65, 78, 68, 65, 0, 0, 0).map { it.toByte() }
        val expectedChecksum = listOf(58, -122, -34, -35).map { it.toByte() }
        val expectedVersion = listOf(11, 0, 1, 0).map { it.toByte() }
        val expectedFileSize = 832
        val expectedForeignOff = 180
        val expectedForeignSize = 21
        val expectedNumClasses = 4
        val expectedClassIdxOff = 60
        val expectedNumberOfLineNumberPrograms = 3
        val expectedLineNumberProgramIndexOffset = 820
        val expectedNumLiteralArrays = 0
        val expectedLiteralArrayIdxOff = 76
        val expectedNumIndexRegions = 1
        val expectedIndexSectionOff = 76

        val parser = ByteCodeParser(buffer)
        val actualHeader = parser.parseABC().header

        assertEquals(expectedMagic, actualHeader.magic)
        assertEquals(expectedChecksum, actualHeader.checksum)
        assertEquals(expectedVersion, actualHeader.version)
        assertEquals(expectedFileSize, actualHeader.fileSize)
        assertEquals(expectedForeignOff, actualHeader.foreignOff)
        assertEquals(expectedForeignSize, actualHeader.foreignSize)
        assertEquals(expectedNumClasses, actualHeader.numClasses)
        assertEquals(expectedClassIdxOff, actualHeader.classIdxOff)
        assertEquals(expectedNumberOfLineNumberPrograms, actualHeader.numberOfLineNumberPrograms)
        assertEquals(expectedLineNumberProgramIndexOffset, actualHeader.lineNumberProgramIndexOffset)
        assertEquals(expectedNumLiteralArrays, actualHeader.numLiteralArrays)
        assertEquals(expectedLiteralArrayIdxOff, actualHeader.literalArrayIdxOff)
        assertEquals(expectedNumIndexRegions, actualHeader.numIndexRegions)
        assertEquals(expectedIndexSectionOff, actualHeader.indexSectionOff)
    }
}
