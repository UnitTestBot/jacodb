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
        parser.parse()
    }
}