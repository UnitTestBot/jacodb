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

import org.jacodb.panda.dynamic.api.PandaProject
import org.jacodb.panda.taint.CaseTaintConfig
import org.jacodb.panda.taint.CleanerMethodConfig
import org.jacodb.panda.taint.SinkMethodConfig
import org.jacodb.panda.taint.SourceMethodConfig
import org.jacodb.panda.taint.TaintAnalyzer
import org.jacodb.panda.taint.UntrustedIndexArrayAccessSinkCheck
import org.jacodb.panda.taint.UntrustedLoopBoundSinkCheck
import org.jacodb.taint.configuration.Argument
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import parser.loadCaseTaintConfig
import parser.loadIr

class TaintSamples {
    private fun loadProjectForSample(programName: String): PandaProject {
        val parser = loadIr("/samples/${programName}.json")
        val project = parser.getProject()
        return project
    }

    // private val json = Json {
    //     encodeDefaults = true
    //     prettyPrint = true
    // }
    //
    // private fun saveSerializedConfig(config: CaseTaintConfig, filename: String) {
    //     val serializedConfig = json.encodeToString(config)
    //     val fullPath = "${filename}"
    //     val outputFile = File(fullPath)
    //     outputFile.writeText(serializedConfig)
    // }

    @Nested
    inner class PasswordLeakTest {

        private val project: PandaProject = loadProjectForSample("taintSamples/passwordLeak")
        private val fileTaintAnalyzer = TaintAnalyzer(project)

        @Test
        fun `counterexample - print unencrypted password to console`() {
            val config = loadCaseTaintConfig("passwordLeakTaintConfig1.json")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            assert(sinkResults.size == 1)
        }

        @Test
        fun `positive example - print encrypted password to console (with forgotten cleaner)`() {
            val config = loadCaseTaintConfig("passwordLeakTaintConfig2.json")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            assert(sinkResults.size == 1)
        }

        // @Disabled("Cleaner config don't work as expected")
        @Test
        fun `positive example - print encrypted password to console`() {
            val config = CaseTaintConfig(
                sourceMethodConfigs = listOf(
                    SourceMethodConfig(methodName = "getUserPassword")
                ),
                cleanerMethodConfigs = listOf(
                    CleanerMethodConfig(
                        methodName = "encryptPassword",
                        position = Argument(0)
                    )
                ),
                sinkMethodConfigs = listOf(
                    SinkMethodConfig(
                        methodName = "log",
                        position = Argument(1)
                    )
                ),
                startMethodNamesForAnalysis = listOf("case2")
            )
            // saveSerializedConfig(config, "passwordLeakTaintConfig3.json")

            val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            assert(sinkResults.isEmpty())
        }

    }

    @Nested
    inner class UntrustedLoopBoundTest {

        private val project: PandaProject = loadProjectForSample("taintSamples/untrustedLoopBound")
        private val fileTaintAnalyzer = TaintAnalyzer(project)

        @Test
        fun `counterexample - untrusted bound in for loop`() {
            val config = loadCaseTaintConfig("untrustedLoopBoundConfig1.json")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            assert(sinkResults.size == 1)
        }

        @Disabled("Loop do while is not supported yet")
        @Test
        fun `counterexample - untrusted bound in do while loop`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = "getUserData",
                            markName = "UNTRUSTED"
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("doWhileLoop"),
                    builtInOptions = listOf(UntrustedLoopBoundSinkCheck)
                )
            )
            assert(sinkResults.size == 1)
        }

        @Test
        fun `counterexample - untrusted bound in while loop`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = "getUserData",
                            markName = "UNTRUSTED"
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("whileLoop"),
                    builtInOptions = listOf(UntrustedLoopBoundSinkCheck)
                )
            )
            assert(sinkResults.size == 1)
        }

    }

    @Disabled("missing json")
    @Nested
    inner class UntrustedArraySizeTest {

        private val project: PandaProject = loadProjectForSample("taintSamples/untrustedArraySize")
        private val fileTaintAnalyzer = TaintAnalyzer(project)

        @Test
        fun `counterexample - untrusted size in array constructor and loop bound`() {
            val config = loadCaseTaintConfig("untrustedArraySizeConfig1.json")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(config)
            assert(sinkResults.size == 2)
        }

    }

    @Nested
    inner class IndexOutOfRangeTest {

        private val project: PandaProject = loadProjectForSample("taintSamples/indexOutOfRange")
        private val fileTaintAnalyzer = TaintAnalyzer(project)

        @Disabled("Taint marks don't pass through as expected")
        @Test
        fun `false positive - potential index out of range alert when it's not feasible`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = "readUInt",
                            markName = "UNTRUSTED"
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("main"),
                    builtInOptions = listOf(UntrustedIndexArrayAccessSinkCheck)
                )
            )
            assert(sinkResults.size == 2)
        }

    }

    @Nested
    inner class FieldSampleTest {
        private val project: PandaProject = loadProjectForSample("taintSamples/fieldSample")
        private val fileTaintAnalyzer = TaintAnalyzer(project)

        @Disabled("IFDS do not work properly with virtual methods")
        @Test
        fun `test taint analysis on fieldSample`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = "getNameOption",
                        )
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                        )
                    ),
                )
            )
            assert(sinkResults.size == 2)
        }
    }

    @Nested
    inner class LoopBoundInjectionUseCaseTest {
        private val project: PandaProject = loadProjectForSample("codeqlSamples/loopBoundInjection")
        private val fileTaintAnalyzer = TaintAnalyzer(project)

        private val postHandlerMethodName = "#4722804945120678178#"

        @Disabled("There is no ability to mark method argument as tainted")
        @Test
        fun `counterexample - loop bound injection (could possibly lead to DoS if length is large)`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig(
                            methodName = postHandlerMethodName,
                            markName = "UNTRUSTED",
                            position = Argument(0)
                        )
                    ),
                    startMethodNamesForAnalysis = listOf(postHandlerMethodName),
                    builtInOptions = listOf(UntrustedLoopBoundSinkCheck)
                )
            )
            assert(sinkResults.size == 1)
        }
    }

    @Nested
    inner class SQLInjectionTest {
        private fun getTaintAnalyserByProgramName(programName: String = "taintSamples/SQLInjection"): TaintAnalyzer {
            val project: PandaProject = loadProjectForSample(programName)
            val fileTaintAnalyzer = TaintAnalyzer(project)
            return fileTaintAnalyzer
        }

        @Test
        fun `counterexample - sql injection that lead to dropping table`() {
            val fileTaintAnalyzer = getTaintAnalyserByProgramName()
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig("getUserName")
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "query",
                            position = Argument(0)
                        )
                    ),
                )
            )
            assert(sinkResults.size == 1)
        }

        @Test
        fun `counterexample - more realistic sql injection`() {
            val fileTaintAnalyzer = getTaintAnalyserByProgramName("taintSamples/SQLInjection2")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig("getUser")
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "query",
                            position = Argument(1)
                        )
                    ),
                )
            )
            assert(sinkResults.size == 1)
        }

        @Test
        fun `counterexample - most production-like sql injection`() {
            val fileTaintAnalyzer = getTaintAnalyserByProgramName("taintSamples/SQLInjection3")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig("getUser")
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "query",
                            position = Argument(1)
                        )
                    ),
                )
            )
            assert(sinkResults.size == 1)
        }
    }

    @Nested
    inner class XSSTest {
        private val project: PandaProject = loadProjectForSample("taintSamples/XSS")
        private val fileTaintAnalyzer = TaintAnalyzer(project)

        @Test
        fun `counterexample - not validating user data lead to xss attack`() {
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig("getUserComment")
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "displayComment",
                        )
                    ),
                )
            )
            assert(sinkResults.size == 1)
        }
    }

    @Nested
    inner class PasswordExposureFPTest {
        private fun getTaintAnalyserByProgramName(programName: String = "taintSamples/passwordExposureFP"): TaintAnalyzer {
            val project: PandaProject = loadProjectForSample(programName)
            val fileTaintAnalyzer = TaintAnalyzer(project)
            return fileTaintAnalyzer
        }

        @Test
        fun `counterexample - potential exposure of unencrypted password (false positive tho)`() {
            val fileTaintAnalyzer = getTaintAnalyserByProgramName()
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig("getUserData")
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("usage1")
                )
            )
            assert(sinkResults.size == 1)
        }

        @Test
        fun `counterexample - exposure of unencrypted password`() {
            val fileTaintAnalyzer = getTaintAnalyserByProgramName()
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig("getUserData")
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("usage2")
                )
            )
            assert(sinkResults.size == 1)
        }

        @Disabled("Temporary test. For debug purposes only.")
        @Test
        fun `debug test`() {
            val fileTaintAnalyzer = getTaintAnalyserByProgramName()
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig("getUserData")
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "log",
                            markName = "WRONG MARK NAME",
                            position = Argument(1)
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("usage3")
                )
            )
            assert(sinkResults.size == 1)
        }

        @Test
        fun `control test for false positive refutation with symbolic execution`() {
            val fileTaintAnalyzer = getTaintAnalyserByProgramName("taintSamples/passwordExposureFP2")
            val sinkResults = fileTaintAnalyzer.analyseOneCase(
                caseTaintConfig = CaseTaintConfig(
                    sourceMethodConfigs = listOf(
                        SourceMethodConfig("getUserData")
                    ),
                    sinkMethodConfigs = listOf(
                        SinkMethodConfig(
                            methodName = "printToConsole",
                            position = Argument(0)
                        )
                    ),
                    startMethodNamesForAnalysis = listOf("usage1")
                ),
                withTrace = true
            )
            assert(sinkResults.size == 1)
        }
    }
}
