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

package ets

import ets.utils.loadDto
import ets.utils.loadEtsFile
import org.jacodb.panda.dynamic.ets.base.EtsAssignStmt
import org.jacodb.panda.dynamic.ets.base.EtsInstanceFieldRef
import org.jacodb.panda.dynamic.ets.base.EtsThis
import org.jacodb.panda.dynamic.ets.model.EtsFile
import org.jacodb.panda.dynamic.ets.utils.dumpDot
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

private val logger = mu.KotlinLogging.logger {}

class EtsFileTest {

    companion object {
        private const val BASE_PATH = "/etsir/samples"

        private fun loadSample(name: String): EtsFile {
            return loadEtsFile("$BASE_PATH/$name.ts.json")
        }
    }

    @Disabled("Run manually, if needed")
    @Test
    fun dumpDot() {
        val etsFileDto = loadDto("/etsir/samples/object.ts.json")
        etsFileDto.classes.forEach { cls ->
            cls.methods.forEach { method ->
                logger.info {
                    "Method '${method.signature}', locals = ${method.body.locals}, typeParameters = ${method.typeParameters}, blocks: ${method.body.cfg.blocks.size}"
                }
                method.body.cfg.blocks.forEach { block ->
                    logger.info { "BLOCK ${block.id}" }
                    block.stmts.forEachIndexed { i, inst ->
                        logger.info { "${i + 1}. $inst" }
                    }
                    logger.info { "-----" }
                }
            }
        }
        etsFileDto.dumpDot("object.ts.json.dot")
        Runtime.getRuntime().exec("dot -Tpdf -O object.ts.json.dot")
    }

    @Test
    fun printEtsInstructions() {
        val etsFile = loadSample("classes/SimpleClass")
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
    fun `test etsFile on TypeMismatch`() {
        val etsFile = loadSample("TypeMismatch")
        etsFile.classes.forEach { cls ->
            cls.methods.forEach { etsMethod ->
                when (etsMethod.name) {
                    "add" -> {
                        Assertions.assertEquals(9, etsMethod.cfg.instructions.size)
                    }

                    "main" -> {
                        Assertions.assertEquals(4, etsMethod.cfg.instructions.size)
                    }
                }
            }
        }
    }

    @Test
    fun `test initializers prepended to class constructor`() {
        val etsFile = loadSample("PrependInitializer")
        val cls = etsFile.classes.single { it.name == "Foo" }
        val ctorBegin = cls.ctor.cfg.instructions.first() as EtsAssignStmt
        val fieldRef = ctorBegin.lhv as EtsInstanceFieldRef
        Assertions.assertTrue(fieldRef.instance is EtsThis)
        Assertions.assertEquals("x", fieldRef.field.name)
    }

    @Test
    fun `test static field should not be initialized in constructor`() {
        val etsFile = loadSample("StaticField")
        val cls = etsFile.classes.single { it.name == "Foo" }
        Assertions.assertFalse(cls.ctor.cfg.stmts.any {
            it is EtsAssignStmt && it.lhv is EtsInstanceFieldRef
        })
    }

    @Test
    fun `test default constructor should be synthesized`() {
        val etsFile = loadSample("DefaultConstructor")
        val cls = etsFile.classes.single { it.name == "Foo" }
        val fieldInit = cls.ctor.cfg.instructions.first() as EtsAssignStmt
        val fieldRef = fieldInit.lhv as EtsInstanceFieldRef
        Assertions.assertTrue(fieldRef.instance is EtsThis)
        Assertions.assertEquals("x", fieldRef.field.name)
    }
}
