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

package analysis

import org.jacodb.analysis.ifds.SingletonUnit
import org.jacodb.analysis.ifds.UnitResolver
import org.jacodb.analysis.taint.TaintManager
import org.jacodb.panda.dynamic.api.PandaApplicationGraphImpl
import org.jacodb.panda.dynamic.api.PandaMethod
import org.jacodb.panda.dynamic.parser.ByteCodeParser
import org.jacodb.panda.dynamic.parser.IRParser
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test

private val logger = mu.KotlinLogging.logger {}

class IfdsPandaTest : BaseTest() {

    companion object : WithDB()

    private val bcFilePath = javaClass.getResource("/samples/ProgramByteCode.abc")?.path ?: ""
    private val bytes = FileInputStream(bcFilePath).readBytes()
    private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    private val bcParser = ByteCodeParser(buffer)

    init {
        bcParser.parseABC()
    }

    private val sampleFilePath = javaClass.getResource("/samples/ProgramIR.json")?.path ?: ""
    private val parser = IRParser(sampleFilePath, bcParser)
    private val project = parser.getProject()

    @Test
    fun test1() {
        val methods = project.classes.flatMap { it.methods }
        logger.info { "Methods: ${methods.size}" }
        for (method in methods) {
            logger.info { "  ${method.name}" }
        }
        val graph = PandaApplicationGraphImpl(project)
        val unitResolver = UnitResolver<PandaMethod> { SingletonUnit }
        val manager = TaintManager(graph, unitResolver)
        val sinks = manager.analyze(methods)
        logger.info { "Sinks: $sinks" }
    }

}
