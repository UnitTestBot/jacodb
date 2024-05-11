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

import org.jacodb.panda.dynamic.api.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import parser.loadIr

private val logger = mu.KotlinLogging.logger {}

class SimpleStaticAnalysisTest {
    @Nested
    inner class ArgumentParameterCorrespondenceTest {

        private fun analyse(programName: String, startMethods: List<String>) : List<Pair<PandaInst, PandaMethod>> {
            val parser = loadIr("/samples/${programName}.json")
            val project = parser.getProject()
            val graph = PandaApplicationGraphImpl(project)
            val methods = graph.project.classes.flatMap { it.methods }
            val mismatches = mutableListOf<Pair<PandaInst, PandaMethod>>()
            for (method in methods) {
                if (startMethods.contains(method.name)) {
                    for (inst in method.instructions) {
                        var callExpr : PandaCallExpr? = null
                        if (inst is PandaCallInst) {
                            callExpr = inst.callExpr
                        }
                        if (inst is PandaAssignInst) {
                            if (inst.rhv is PandaCallExpr) {
                                callExpr = inst.callExpr
                            }
                        }
                        if (callExpr == null) {
                            continue
                        }
                        val callee = callExpr.method

                        // TODO(): need more smart check that callee is not variadic function
                        if (callee.name == "log") {
                            continue
                        }
                        if (callExpr.args.size != callee.parameters.size) {
                            mismatches.add(Pair(inst, callee))
                            logger.info { "parameter-argument count mismatch for call: $inst (expected ${callee.parameters.size} arguments, but got ${callExpr.args.size})" }
                        }
                    }
                }
            }
            return mismatches
        }

        @Test
        fun `test for mismatch detection in regular function call`() {
            val mismatches = analyse(
                programName = "codeqlSamples/parametersArgumentsMismatch",
                startMethods = listOf("foo")
            )
            assert(mismatches.size == 1)
        }

        @Test
        fun `positive example - test for mismatch detection in class method call`() {
            val mismatches = analyse(
                programName = "codeqlSamples/parametersArgumentsMismatch",
                startMethods = listOf("rightUsage")
            )
            assert(mismatches.isEmpty())
        }

        @Disabled("Don't work cause we can't resolve constructors yet")
        @Test
        fun `counterexample - test for mismatch detection in class method call`() {
            val mismatches = analyse(
                programName = "codeqlSamples/parametersArgumentsMismatch",
                startMethods = listOf("wrongUsage")
            )
            assert(mismatches.size == 3)
        }
    }
}