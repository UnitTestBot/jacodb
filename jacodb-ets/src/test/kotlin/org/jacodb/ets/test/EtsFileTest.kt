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

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsFile
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.test.utils.loadEtsFileFromResource
import org.jacodb.ets.utils.INSTANCE_INIT_METHOD_NAME
import org.jacodb.ets.utils.STATIC_INIT_METHOD_NAME
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val logger = mu.KotlinLogging.logger {}

class EtsFileTest {

    companion object {
        private const val BASE = "/samples/etsir/ast"

        private fun load(name: String): EtsFile {
            return loadEtsFileFromResource("$BASE/$name.ts.json")
        }
    }

    @Test
    fun printEtsInstructions() {
        val etsFile = load("classes/SimpleClass")
        etsFile.classes.forEach { cls ->
            cls.methods.forEach { method ->
                logger.info {
                    "Method '$method', instructions: ${method.cfg.stmts.size}"
                }
                method.cfg.stmts.forEach { inst ->
                    logger.info { "${inst.location.index}: $inst" }
                }
            }
        }
    }

    @Test
    fun `test sample TypeMismatch`() {
        val etsFile = load("TypeMismatch")
        etsFile.classes.forEach { cls ->
            cls.methods.forEach { method ->
                when (method.name) {
                    "add" -> {
                        assertTrue(method.cfg.stmts.size > 2)
                    }

                    "main" -> {
                        assertTrue(method.cfg.stmts.size > 2)
                    }
                }
            }
        }
    }

    @Test
    fun `test sample FieldInitializers`() {
        val etsFile = load("classes/FieldInitializers")

        val cls = etsFile.classes.single { it.name == "Foo" }

        // instance initializer
        run {
            val method = cls.methods.single { it.name == INSTANCE_INIT_METHOD_NAME }
            assertEquals(3, method.cfg.stmts.size)

            // this := ThisRef
            run {
                val stmt = method.cfg.stmts[0]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsLocal>(lhv)
                assertEquals("this", lhv.name)

                val rhv = stmt.rhv
                assertIs<EtsThis>(rhv)
                // assertEquals("Foo", rhv.type.typeName)
            }

            // this.x := 99
            run {
                val stmt = method.cfg.stmts[1]
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
                val stmt = method.cfg.stmts[2]
                assertIs<EtsReturnStmt>(stmt)
                assertEquals(null, stmt.returnValue)
            }
        }

        // static initializer
        run {
            val method = cls.methods.single { it.name == STATIC_INIT_METHOD_NAME }
            assertEquals(3, method.cfg.stmts.size)

            // this := ThisRef
            run {
                val stmt = method.cfg.stmts[0]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsLocal>(lhv)
                assertEquals("this", lhv.name)

                val rhv = stmt.rhv
                assertIs<EtsThis>(rhv)
                // assertEquals("Foo", rhv.type.typeName)
            }

            // this.y := 111
            run {
                val stmt = method.cfg.stmts[1]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsStaticFieldRef>(lhv)

                val clazz = lhv.field.enclosingClass
                assertEquals("Foo", clazz.name)

                val field = lhv.field
                assertEquals("y", field.name)

                val rhv = stmt.rhv
                assertIs<EtsNumberConstant>(rhv)
                assertEquals(111.0, rhv.value)
            }

            // return
            run {
                val stmt = method.cfg.stmts.last()
                assertIs<EtsReturnStmt>(stmt)
                assertEquals(null, stmt.returnValue)
            }
        }

        // static field in instance method
        run {
            val method = cls.methods.single { it.name == "foo" }

            // this := ThisRef
            run {
                val stmt = method.cfg.stmts[0]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsLocal>(lhv)
                assertEquals("this", lhv.name)

                val rhv = stmt.rhv
                assertIs<EtsThis>(rhv)
                // assertEquals("Foo", rhv.type.typeName)
            }

            // Foo.y := 222
            run {
                val stmt = method.cfg.stmts[1]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsStaticFieldRef>(lhv)

                val clazz = lhv.field.enclosingClass
                assertEquals("Foo", clazz.name)

                val field = lhv.field
                assertEquals("y", field.name)

                val rhv = stmt.rhv
                assertIs<EtsNumberConstant>(rhv)
                assertEquals(222.0, rhv.value)
            }

            // return
            run {
                val stmt = method.cfg.stmts.last()
                assertIs<EtsReturnStmt>(stmt)
                assertEquals(null, stmt.returnValue)
            }
        }

        // static field in static method
        run {
            val method = cls.methods.single { it.name == "bar" }

            // this := ThisRef
            run {
                val stmt = method.cfg.stmts[0]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsLocal>(lhv)
                assertEquals("this", lhv.name)

                val rhv = stmt.rhv
                assertIs<EtsThis>(rhv)
                // assertEquals("Foo", rhv.type.typeName)
            }

            // this.y := 333
            run {
                val stmt = method.cfg.stmts[1]
                assertIs<EtsAssignStmt>(stmt)

                val lhv = stmt.lhv
                assertIs<EtsStaticFieldRef>(lhv)

                val clazz = lhv.field.enclosingClass
                assertEquals("Foo", clazz.name)

                val field = lhv.field
                assertEquals("y", field.name)

                val rhv = stmt.rhv
                assertIs<EtsNumberConstant>(rhv)
                assertEquals(333.0, rhv.value)
            }

            // return
            run {
                val stmt = method.cfg.stmts.last()
                assertIs<EtsReturnStmt>(stmt)
                assertEquals(null, stmt.returnValue)
            }
        }
    }
}
