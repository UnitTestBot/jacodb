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
import org.jacodb.panda.dynamic.parser.IRParser
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class IRParserTest {
    private val bcFilePath = javaClass.getResource("/samples/ProgramByteCode.abc")?.path ?: ""
    private val bytes = FileInputStream(bcFilePath).readBytes()
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private val bcParser = ByteCodeParser(buffer)

    private val sampleFilePath = javaClass.getResource("/samples/ProgramIR.json")?.path ?: ""
    private val parser: IRParser = IRParser(sampleFilePath, bcParser)

    init {
        bcParser.parseABC()
    }

    @Test
    fun getProject() {
        val project = parser.getProject()
        assertNotNull(project)
    }

    @Test
    fun getProgramIR() {
        val ir: IRParser.ProgramIR = parser.getProgramIR()
        parser.printProgramInfo(ir)
        assertNotNull(ir)
    }

    @Test
    fun getPandaMethods() {
        val ir: IRParser.ProgramIR = parser.getProgramIR()
        ir.classes.forEach { cls ->
            cls.methods.forEach { method ->
                val pandaMethod = method.pandaMethod
                println("\n$pandaMethod")
                println("Instructions:")
                pandaMethod.instructions.forEach {pandaInst ->
                    println("${pandaInst.location}: $pandaInst")
                }
            }
        }
    }

    @Test
    fun getSetOfProgramOpcodes() {
        val ir: IRParser.ProgramIR = parser.getProgramIR()
        val opcodes = mutableSetOf<String>()
        ir.classes.forEach { programClass ->
            programClass.methods.forEach { programMethod ->
                programMethod.basicBlocks.forEach { programBlock ->
                    programBlock.insts.forEach { programInst ->
                        opcodes.add(programInst.opcode)
                    }
                }
            }
        }
        println(opcodes.sorted().joinToString(separator = "\n"))
    }

//    @Test
//    fun createControlFlowGraph() {
//    }

}
