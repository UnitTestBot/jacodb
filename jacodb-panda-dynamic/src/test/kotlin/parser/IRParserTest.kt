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

import org.jacodb.panda.dynamic.parser.IRParser
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class IRParserTest {
    private val sampleFilePath = javaClass.getResource("/samples/ProgramIR.json")?.path ?: ""
    private val parser: IRParser = IRParser(sampleFilePath)

    @Test
    fun getProgramIR() {
        val ir: IRParser.ProgramIR = parser.getProgramIR()
        parser.printProgramInfo(ir)
        assertNotNull(ir)
    }

    @Test
    fun getPandaMethod() {
        val ir: IRParser.ProgramIR = parser.getProgramIR()
        val method = ir.classes[0].methods[0].pandaMethod
        println(method)
        println("Instructions:")
        method.instructions.forEach {
            println("${it.location}: $it")
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
