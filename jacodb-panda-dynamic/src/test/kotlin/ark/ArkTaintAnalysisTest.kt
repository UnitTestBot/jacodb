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

import org.jacodb.panda.dynamic.ark.dto.ArkFileDto
import org.jacodb.panda.dynamic.ark.dto.convertToArkFile
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.junit.jupiter.api.Test

class ArkTaintAnalysisTest {
    private fun loadArkFile(name: String): ArkFile {
        val path = "arkir/$name.ts.json"
        val stream = object {}::class.java.getResourceAsStream("/$path")
            ?: error("Resource not found: $path")
        val arkDto = ArkFileDto.loadFromJson(stream)
        println("arkDto = $arkDto")
        val ark = convertToArkFile(arkDto)
        println("ark = $ark")
        return ark
    }

    @Test
    fun `test taint analysis`() {
        val arkFile = loadArkFile("taint")
        // val graph = ArkApplicationGraph(arkFile)
    }
}
