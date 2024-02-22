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
import org.jacodb.panda.dynamic.parser.ByteCodeParser.MethodTag
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
        val abc = parser.parseABC()
        println(abc.header)
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

    @Test
    fun validateHeader() {
        val expectedMagic = listOf(0x50, 0x41, 0x4E, 0x44, 0x41, 0x00, 0x00, 0x00).map { it.toByte() }
        val expectedChecksum = listOf(0x60, 0x7A, 0x49, 0x42).map { it.toByte() }
        val expectedVersion = listOf(0x0B, 0x00, 0x01, 0x00).map { it.toByte() }
        val expectedFileSize = 0x02E0
        val expectedForeignOff = 0xAC
        val expectedForeignSize = 0x15
        val expectedNumClasses = 0x04
        val expectedClassIdxOff = 0x3C
        val expectedNumberOfLineNumberPrograms = 0x03
        val expectedLineNumberProgramIndexOffset = 0x02D4
        val expectedNumLiteralArrays = 0x00
        val expectedLiteralArrayIdxOff = 0x4C
        val expectedNumIndexRegions = 0x01
        val expectedIndexSectionOff = 0x4C

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

    @Test
    fun validateMethods() {
        val parser = ByteCodeParser(buffer)
        val actualMethods = parser.parseABC().methods
        assertEquals(2, actualMethods.size)

        // Validate the first method
        val method1 = actualMethods[0]
        assertEquals(0x3.toShort(), method1.classIdx)
        assertEquals(0x0.toShort(), method1.protoIdx)
        assertEquals(0xE6, method1.nameOff)
        assertEquals(0x288, method1.accessFlag)

        val method1Tags = method1.methodData
        assertEquals(5, method1Tags.size)

        val method1Tag1 = method1Tags[0]
        assertEquals(MethodTag.Code(0x21B), method1Tag1)

        val method1Tag2 = method1Tags[1]
        assertEquals(MethodTag.SourceLang(0x0), method1Tag2)

        val method1Tag3 = method1Tags[2]
        assertEquals(MethodTag.DebugInfo(0x2A1), method1Tag3)

        val method1Tag4 = method1Tags[3]
        assertEquals(MethodTag.Annotation(0x1F4), method1Tag4)

        val method1Tag5 = method1Tags[4]
        assertEquals(MethodTag.Nothing, method1Tag5)

        // Validate the second method
        val method2 = actualMethods[1]
        assertEquals(0x3.toShort(), method2.classIdx)
        assertEquals(0x1.toShort(), method2.protoIdx)
        assertEquals(0xF9, method2.nameOff)
        assertEquals(0x288, method2.accessFlag)

        val method2Tags = method2.methodData
        assertEquals(5, method2Tags.size)

        val method2Tag1 = method2Tags[0]
        assertEquals(MethodTag.Code(0x263), method2Tag1)

        val method2Tag2 = method2Tags[1]
        assertEquals(MethodTag.SourceLang(0x0), method2Tag2)

        val method2Tag3 = method2Tags[2]
        assertEquals(MethodTag.DebugInfo(0x2C7), method2Tag3)

        val method2Tag4 = method2Tags[3]
        assertEquals(MethodTag.Annotation(0x20E), method2Tag4)

        val method2Tag5 = method2Tags[4]
        assertEquals(MethodTag.Nothing, method2Tag5)
    }
}
