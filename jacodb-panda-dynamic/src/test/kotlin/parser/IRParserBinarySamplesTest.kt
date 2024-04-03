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

import org.jacodb.panda.dynamic.api.PandaAnyType
import org.jacodb.panda.dynamic.api.PandaArgument
import org.jacodb.panda.dynamic.api.PandaAssignInst
import org.jacodb.panda.dynamic.api.PandaLocalVar
import org.jacodb.panda.dynamic.api.PandaNegExpr
import org.jacodb.panda.dynamic.api.PandaNumberConstant
import org.jacodb.panda.dynamic.api.PandaReturnInst
import org.jacodb.panda.dynamic.api.PandaSubExpr
import org.jacodb.panda.dynamic.parser.IRParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IRParserBinarySamplesTest {

    companion object {
        private fun loadIR(fileName: String): IRParser {
            val sampleFilePath = this::class.java.getResource("/samples/binary/$fileName.json")?.path ?: ""
            return IRParser(sampleFilePath)
        }
    }

    @Test
    fun `test parser on binary subtraction`() {
        val irParser = loadIR("Subtraction")
        val program = irParser.getProgram()
        program.classes.forEach { cls ->
            cls.properties.forEach { property ->
                val pandaMethod = property.method.pandaMethod
                Assertions.assertNotNull(pandaMethod.name)
                Assertions.assertNotNull(pandaMethod.instructions)
                when (pandaMethod.name) {
                    "subtract" -> {
                        assertEquals(2, pandaMethod.instructions.size)
                        assertEquals(2, pandaMethod.blocks.size)
                        pandaMethod.instructions.forEachIndexed { index, inst ->
                            when (index) {
                                0 -> {
                                    val assignInst = inst as PandaAssignInst
                                    val expectedInst = PandaLocalVar(
                                        index = 0,
                                        type = PandaAnyType
                                    )
                                    assertEquals(
                                        expectedInst,
                                        assignInst.lhv
                                    )
                                    val subExpr = assignInst.rhv as PandaSubExpr
                                    assertEquals(
                                        PandaArgument(
                                            index = 3,
                                            name = "arg3"
                                        ),
                                        subExpr.lhv
                                    )
                                    assertEquals(
                                        PandaNumberConstant(
                                            value = 5
                                        ),
                                        subExpr.rhv
                                    )
                                }

                                1 -> {
                                    val returnInst = inst as PandaReturnInst
                                    assertEquals(
                                        PandaLocalVar(
                                            index = 0,
                                            type = PandaAnyType
                                        ),
                                        returnInst.returnValue
                                    )
                                }
                            }
                        }
                    }

                    "subtractToZero" -> {
                        assertEquals(2, pandaMethod.instructions.size)
                        assertEquals(2, pandaMethod.blocks.size)
                        pandaMethod.instructions.forEachIndexed { index, inst ->
                            when (index) {
                                0 -> {
                                    val assignInst = inst as PandaAssignInst
                                    val expectedInst = PandaLocalVar(
                                        index = 0,
                                        type = PandaAnyType
                                    )
                                    assertEquals(
                                        expectedInst,
                                        assignInst.lhv
                                    )
                                    val subExpr = assignInst.rhv as PandaSubExpr
                                    assertEquals(
                                        PandaArgument(
                                            index = 3,
                                            name = "arg3"
                                        ),
                                        subExpr.lhv
                                    )
                                    assertEquals(
                                        PandaNumberConstant(
                                            value = 0
                                        ),
                                        subExpr.rhv
                                    )
                                }

                                1 -> {
                                    val returnInst = inst as PandaReturnInst
                                    assertEquals(
                                        PandaLocalVar(
                                            index = 0,
                                            type = PandaAnyType
                                        ),
                                        returnInst.returnValue
                                    )
                                }
                            }
                        }
                    }

                    "subtractNumbers" -> {
                        assertEquals(2, pandaMethod.instructions.size)
                        assertEquals(2, pandaMethod.blocks.size)
                        pandaMethod.instructions.forEachIndexed { index, inst ->
                            when (index) {
                                0 -> {
                                    val assignInst = inst as PandaAssignInst
                                    val expectedInst = PandaLocalVar(
                                        index = 0,
                                        type = PandaAnyType
                                    )
                                    assertEquals(
                                        expectedInst,
                                        assignInst.lhv
                                    )
                                    val subExpr = assignInst.rhv as PandaSubExpr
                                    assertEquals(
                                        PandaArgument(
                                            index = 3,
                                            name = "arg3"
                                        ),
                                        subExpr.lhv
                                    )
                                    assertEquals(
                                        PandaArgument(
                                            index = 4,
                                            name = "arg4"
                                        ),
                                        subExpr.rhv
                                    )
                                }

                                1 -> {
                                    val returnInst = inst as PandaReturnInst
                                    assertEquals(
                                        PandaLocalVar(
                                            index = 0,
                                            type = PandaAnyType
                                        ),
                                        returnInst.returnValue
                                    )
                                }
                            }
                        }
                    }

                    "subtractFromZero" -> {
                        assertEquals(2, pandaMethod.instructions.size)
                        assertEquals(2, pandaMethod.blocks.size)
                        pandaMethod.instructions.forEachIndexed { index, inst ->
                            when (index) {
                                0 -> {
                                    val assignInst = inst as PandaAssignInst
                                    val expectedInst = PandaLocalVar(
                                        index = 0,
                                        type = PandaAnyType
                                    )
                                    assertEquals(
                                        expectedInst,
                                        assignInst.lhv
                                    )
                                    val subExpr = assignInst.rhv as PandaSubExpr
                                    assertEquals(
                                        PandaNumberConstant(
                                            value = 0
                                        ),
                                        subExpr.lhv
                                    )
                                    assertEquals(
                                        PandaArgument(
                                            index = 3,
                                            name = "arg3"
                                        ),
                                        subExpr.rhv
                                    )
                                }

                                1 -> {
                                    val returnInst = inst as PandaReturnInst
                                    assertEquals(
                                        PandaLocalVar(
                                            index = 0,
                                            type = PandaAnyType
                                        ),
                                        returnInst.returnValue
                                    )
                                }
                            }
                        }
                    }

                    "subtractReversed" -> {
                        assertEquals(2, pandaMethod.instructions.size)
                        assertEquals(2, pandaMethod.blocks.size)
                        pandaMethod.instructions.forEachIndexed { index, inst ->
                            when (index) {
                                0 -> {
                                    val assignInst = inst as PandaAssignInst
                                    val expectedInst = PandaLocalVar(
                                        index = 0,
                                        type = PandaAnyType
                                    )
                                    assertEquals(
                                        expectedInst,
                                        assignInst.lhv
                                    )
                                    val subExpr = assignInst.rhv as PandaSubExpr
                                    assertEquals(
                                        PandaNumberConstant(
                                            value = 5
                                        ),
                                        subExpr.lhv
                                    )
                                    assertEquals(
                                        PandaArgument(
                                            index = 3,
                                            name = "arg3"
                                        ),
                                        subExpr.rhv
                                    )
                                }

                                1 -> {
                                    val returnInst = inst as PandaReturnInst
                                    assertEquals(
                                        PandaLocalVar(
                                            index = 0,
                                            type = PandaAnyType
                                        ),
                                        returnInst.returnValue
                                    )
                                }
                            }
                        }
                    }

                    "subtractMixedNumbers" -> {
                        assertEquals(3, pandaMethod.instructions.size)
                        assertEquals(2, pandaMethod.blocks.size)
                        pandaMethod.instructions.forEachIndexed { index, inst ->
                            when (index) {
                                0 -> {
                                    val assignInst = inst as PandaAssignInst
                                    val expectedInst = PandaLocalVar(
                                        index = 0,
                                        type = PandaAnyType
                                    )
                                    assertEquals(
                                        expectedInst,
                                        assignInst.lhv
                                    )
                                    val negExpr = assignInst.rhv as PandaNegExpr
                                    assertEquals(
                                        PandaArgument(
                                            index = 3,
                                            name = "arg3"
                                        ),
                                        negExpr.arg
                                    )
                                }

                                1 -> {
                                    val assignInst = inst as PandaAssignInst
                                    val expectedInst = PandaLocalVar(
                                        index = 1,
                                        type = PandaAnyType
                                    )
                                    assertEquals(
                                        expectedInst,
                                        assignInst.lhv
                                    )
                                    val subExpr = assignInst.rhv as PandaSubExpr
                                    assertEquals(
                                        PandaLocalVar(
                                            index = 0,
                                            type = PandaAnyType
                                        ),
                                        subExpr.lhv
                                    )
                                    assertEquals(
                                        PandaArgument(
                                            index = 4,
                                            name = "arg4"
                                        ),
                                        subExpr.rhv
                                    )
                                }

                                2 -> {
                                    val returnInst = inst as PandaReturnInst
                                    assertEquals(
                                        PandaLocalVar(
                                            index = 1,
                                            type = PandaAnyType
                                        ),
                                        returnInst.returnValue
                                    )
                                }
                            }
                        }
                    }

                    "subtractNumbersReversed" -> {
                        assertEquals(2, pandaMethod.instructions.size)
                        assertEquals(2, pandaMethod.blocks.size)
                        pandaMethod.instructions.forEachIndexed { index, inst ->
                            when (index) {
                                0 -> {
                                    val assignInst = inst as PandaAssignInst
                                    val expectedInst = PandaLocalVar(
                                        index = 0,
                                        type = PandaAnyType
                                    )
                                    assertEquals(
                                        expectedInst,
                                        assignInst.lhv
                                    )
                                    val subExpr = assignInst.rhv as PandaSubExpr
                                    assertEquals(
                                        PandaArgument(
                                            index = 4,
                                            name = "arg4"
                                        ),
                                        subExpr.lhv
                                    )
                                    assertEquals(
                                        PandaArgument(
                                            index = 3,
                                            name = "arg3"
                                        ),
                                        subExpr.rhv
                                    )
                                }

                                1 -> {
                                    val returnInst = inst as PandaReturnInst
                                    assertEquals(
                                        PandaLocalVar(
                                            index = 0,
                                            type = PandaAnyType
                                        ),
                                        returnInst.returnValue
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
