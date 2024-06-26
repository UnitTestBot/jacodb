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

package ark

import ark.utils.loadIr
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

private val logger = mu.KotlinLogging.logger {}

class ArkFileTest {

    companion object {
        private const val SAMPLE_NAME = "classes/SimpleClass"

        private fun load(name: String): ArkFile {
            return loadIr(
                filePath = "/arkir/samples/$name.json",
            )
        }
    }

    @Test
    fun getArkMethods() {
        val arkFile = load(SAMPLE_NAME)
        arkFile.classes.forEach { cls ->
            cls.methods.forEach { arkMethod ->
                assertNotNull(arkMethod.name)
                assertNotNull(arkMethod.cfg.instructions)
                logger.info { "Ark method '$arkMethod', instructions: ${arkMethod.cfg.instructions}" }

            }
        }
    }

    @Test
    fun printArkInstructions() {
        val arkFile = load(SAMPLE_NAME)
        arkFile.classes.forEach { cls ->
            cls.methods.forEach { arkMethod ->
                assertNotNull(arkMethod.name)
                assertNotNull(arkMethod.cfg.instructions)
                logger.info { "Ark method '$arkMethod'" }
                arkMethod.cfg.instructions.forEach { inst ->
                    logger.info { "${inst.location.index}. $inst" }
                }
                logger.info { "-------------------------------------" }
            }
        }
    }

    @Test
    fun `test arkFile on TypeMismatch`() {
        val arkFile = load("TypeMismatch")
        arkFile.classes.forEach { cls ->
            cls.methods.forEach { arkMethod ->
                assertNotNull(arkMethod.name)
                assertNotNull(arkMethod.cfg.instructions)
                when (arkMethod.name) {
                    "add" -> {
                        assertEquals(9, arkMethod.cfg.instructions.size)
                    }

                    "main" -> {
                        assertEquals(4, arkMethod.cfg.instructions.size)
                    }
                }
            }
        }
    }
}
