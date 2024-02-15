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

import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.decodeFromStream
import org.jacodb.panda.staticvm.PandaApplicationGraph
import org.jacodb.panda.staticvm.PandaInstListBuilder
import org.jacodb.panda.staticvm.PandaProgramInfo
import java.io.FileInputStream


internal class IrDeserializationTest {
    private val json = PandaProgramInfo.json
    private val sampleFilePath = javaClass.getResource("sample.json")?.path ?: ""

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun deserializationTest() {
        val input = FileInputStream(sampleFilePath)
        val program = json.decodeFromStream<PandaProgramInfo>(input)
        val project = program.toProject()
        val methodName = "A.greet:i32;std.core.void;"
        val methodNode = project.methods.find { it.name == methodName }
        val builder = PandaInstListBuilder(methodNode!!)
        val insts = builder.build()
        print(insts)

        val applicationGraph = PandaApplicationGraph(project)
    }
}
