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

import org.jacodb.ets.utils.EtsIrGenerationException
import org.jacodb.ets.utils.EtsIrProvider
import org.jacodb.ets.utils.generateEtsIR
import org.junit.jupiter.api.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class EtsIrGenerationTest {
    @Test
    fun `generation fails instead of returning an invalid output path`() {
        val frontend = createTempDirectory("failing-ets-frontend")
        frontend.resolve("dist").createDirectories()
        frontend.resolve("dist/index.js").writeText("console.error('frontend failed'); process.exit(7);")
        val source = frontend.resolve("input.ts").also { it.writeText("const value = 1;") }

        System.setProperty("ets.frontend.dir", frontend.toString())
        try {
            val error = assertFailsWith<EtsIrGenerationException> {
                generateEtsIR(source, provider = EtsIrProvider.TS_FRONTEND)
            }
            assertTrue(error.message.orEmpty().contains("exit code 7"))
            assertTrue(error.message.orEmpty().contains("frontend failed"))
        } finally {
            System.clearProperty("ets.frontend.dir")
        }
    }
}
