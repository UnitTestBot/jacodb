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

package panda

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import org.jacodb.panda.staticvm.cfg.PandaApplicationGraph
import org.jacodb.panda.staticvm.classpath.PandaProject
import org.jacodb.panda.staticvm.ir.PandaProgramIr
import org.jacodb.panda.staticvm.ir.PandaProgramIr.Companion.json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf
import java.io.FileInputStream
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class PandaIrDeserializationTest {

    companion object {
        private const val SAMPLE_FILE_PATH: String = "sample.abc.ir"
    }

    private fun stdlibAvailable() = EtsStdlib.stdlibAvailable()

    @EnabledIf("stdlibAvailable")
    @Test
    fun deserializationTest() {
        val filePath = SAMPLE_FILE_PATH
        val program = loadProgram("/$filePath")
        val project = PandaProject.fromProgramIr(program, withStdLib = true)
        val applicationGraph = PandaApplicationGraph(project)
    }

    @EnabledIf("stdlibAvailable")
    @Test
    fun catchTest() {
        val filePath = "testCatch.ir"
        val program = loadProgram("/$filePath")
        val project = PandaProject.fromProgramIr(program, withStdLib = true)
        val applicationGraph = PandaApplicationGraph(project)
    }

    @EnabledIf("stdlibAvailable")
    @OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)
    @Test
    fun pandaStdLibTest() {
        val input = FileInputStream(EtsStdlib.STDLIB_FILE_PATH!!.path)

        val (program, deserializationDuration) = measureTimedValue {
            json.decodeFromStream<PandaProgramIr>(input)
        }
        val (project, linkageDuration) = measureTimedValue {
            PandaProject.fromProgramIr(program)
        }

        println("deserialization: $deserializationDuration, linkage: $linkageDuration")
        println("total: ${deserializationDuration + linkageDuration}")
    }


    @EnabledIf("stdlibAvailable")
    @Test
    fun pandaClasspathFlowGraphTest() {
        val filePath = SAMPLE_FILE_PATH
        val program = loadProgram("/$filePath")
        val project = PandaProject.fromProgramIr(program, withStdLib = true)
        val method = project.findMethod("A.greet:i32;void;")
        val flowGraph = method.flowGraph()
    }
}
