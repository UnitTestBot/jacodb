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

import org.jacodb.ets.base.EtsAssignStmt
import org.jacodb.ets.base.EtsInstanceFieldRef
import org.jacodb.ets.base.EtsLocal
import org.jacodb.ets.base.EtsNumberConstant
import org.jacodb.ets.base.EtsReturnStmt
import org.jacodb.ets.base.EtsThis
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.test.utils.loadEtsFileFromResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val logger = mu.KotlinLogging.logger {}

class EtsFileTest {

    companion object {
        private fun load(name: String): EtsFile {
            return loadEtsFileFromResource("/$name.ts.json")
        }
    }

    @Test
    fun printEtsInstructions() {
        val etsFile = load("etsir/samples/classes/SimpleClass")
        etsFile.classes.forEach { cls ->
            cls.methods.forEach { method ->
                logger.info {
                    "Method '$method', locals: ${method.localsCount}, instructions: ${method.cfg.instructions.size}"
                }
                method.cfg.instructions.forEach { inst ->
                    logger.info { "${inst.location.index}. $inst" }
                }
            }
        }
    }

    @Test
    fun `test sample TypeMismatch`() {
        val etsFile = load("etsir/samples/TypeMismatch")
        etsFile.classes.forEach { cls ->
            cls.methods.forEach { method ->
                when (method.name) {
                    "add" -> {
                        assertEquals(10, method.cfg.instructions.size)
                    }

                    "main" -> {
                        assertEquals(5, method.cfg.instructions.size)
                    }
                }
            }
        }
    }

    @Test
    fun `test sample FieldInitializers`() {
        val etsFile = load("etsir/samples/classes/FieldInitializers")

        val cls = etsFile.classes.single { it.name == "Foo" }

        // instance initializer
        run {
            val method = cls.methods.single { it.name == "@instance_init" }
            assertEquals(3, method.cfg.instructions.size)

            // Local("this") := ThisRef
            run {
                val stmt = method.cfg.instructions[0]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsLocal>(lhv)
                assertEquals("this", lhv.name)

                val rhv = stmt.rhv
                assertIs<EtsThis>(rhv)
                assertEquals("Foo", rhv.type.typeName)
            }

            // Local("this").x := 99
            run {
                val stmt = method.cfg.instructions[1]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsInstanceFieldRef>(lhv)

                val instance = lhv.instance
                assertIs<EtsLocal>(instance)
                assertEquals("this", instance.name)

                val field = lhv.field
                assertEquals("x", field.name)

                val rhv = stmt.rhv
                assertIs<EtsNumberConstant>(rhv)
                assertEquals(99.0, rhv.value)
            }

            // return
            run {
                val stmt = method.cfg.instructions[2]
                assertIs<EtsReturnStmt>(stmt)
                assertEquals(null, stmt.returnValue)
            }
        }

        // static initializer
        run {
            val method = cls.methods.single { it.name == "@static_init" }
            assertEquals(3, method.cfg.instructions.size)

            // Local("this") := ThisRef
            run {
                val stmt = method.cfg.instructions[0]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsLocal>(lhv)
                assertEquals("this", lhv.name)

                val rhv = stmt.rhv
                assertIs<EtsThis>(rhv)
                assertEquals("Foo", rhv.type.typeName)
            }

            // Local("this").y := 111
            run {
                val stmt = method.cfg.instructions[1]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsInstanceFieldRef>(lhv)

                val instance = lhv.instance
                assertIs<EtsLocal>(instance)
                assertEquals("this", instance.name)

                val field = lhv.field
                assertEquals("y", field.name)

                val rhv = stmt.rhv
                assertIs<EtsNumberConstant>(rhv)
                assertEquals(111.0, rhv.value)
            }

            // return
            run {
                val stmt = method.cfg.instructions[2]
                assertIs<EtsReturnStmt>(stmt)
                assertEquals(null, stmt.returnValue)
            }
        }
    }
}
