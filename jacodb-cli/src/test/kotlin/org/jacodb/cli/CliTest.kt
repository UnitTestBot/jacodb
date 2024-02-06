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

package org.jacodb.cli

import org.jacodb.testing.analysis.NpeExamples
import org.junit.jupiter.api.Test

class CliTest {
    @Test
    fun `test basic analysis cli api`() {
        val args = listOf(
            "-a", CliTest::class.java.getResource("/config.json")?.file ?: error("Can't find file with config"),
            "-s", NpeExamples::class.java.name
        )
        AnalysisMain().run(args)
    }
}
