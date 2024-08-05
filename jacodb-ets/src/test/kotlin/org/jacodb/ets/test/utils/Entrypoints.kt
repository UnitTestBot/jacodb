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

package org.jacodb.ets.test.utils

import org.jacodb.ets.dto.EtsFileDto
import org.jacodb.ets.utils.dumpDot
import org.jacodb.ets.utils.dumpHuimpleTo
import org.jacodb.ets.utils.render

/**
 * Visualize classes and methods in [EtsFileDto].
 */
object DumpEtsFileDtoToDot {
    private const val PATH = "/etsir/samples/basic.ts.json"
    private const val DOT_PATH = "basicDto.dot"

    @JvmStatic
    fun main(args: Array<String>) {
        val etsFileDto: EtsFileDto = loadEtsFileDtoFromResource(PATH)
        val output = System.out.bufferedWriter()
        etsFileDto.dumpHuimpleTo(output)
        render(DOT_PATH) { file ->
            etsFileDto.dumpDot(file)
        }
    }
}

object DumpEtsFileToDot {
    private const val PATH = "/etsir/samples/basic.ts.json"
    private const val DOT_PATH = "basic.dot"

    @JvmStatic
    fun main(args: Array<String>) {
        val etsFile = loadEtsFileFromResource(PATH)
        val output = System.out.bufferedWriter()
        etsFile.dumpHuimpleTo(output)
        render(DOT_PATH) { file ->
            etsFile.dumpDot(file)
        }
    }
}
