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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import parser.loadIr

private val logger = mu.KotlinLogging.logger {}

class PrimaryStaticAnalysisTest {
    @Nested
    inner class ArgumentParameterCorrespondenceTest {

        private fun analyse(programName: String, startMethods: List<String>): List<Pair<PandaInst, PandaMethod>> {
            val parser = loadIr("/samples/${programName}.json")
            val project = parser.getProject()
            val graph = PandaApplicationGraphImpl(project)
            val methods = graph.project.classes.flatMap { it.methods }
            val mismatches = mutableListOf<Pair<PandaInst, PandaMethod>>()
            for (method in methods) {
                if (startMethods.contains(method.name)) {
                    for (inst in method.instructions) {
                        var callExpr: PandaCallExpr? = null
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

        @Disabled("reconcile arguments number for class methods (is 'this' count?)")
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

    // TODO(): expand for writing (trysttoglobalbyname) and constants (stconsttoglobalbyname)
    @Nested
    inner class UnresolvedVariableTest {

        private val orderedInstructions = mutableListOf<PandaInst>()
        private fun dfs(inst: PandaInst, graph: PandaApplicationGraph, visited: MutableMap<PandaInst, Boolean>) {
            visited[inst] = true
            for (successorInst in graph.successors(inst)) {
                if (!visited.getOrDefault(successorInst, false)) {
                    dfs(successorInst, graph, visited)
                }
            }
            for (callee in graph.callees(inst)) {
                val firstInstInCallee = callee.instructions.first()
                if (!visited.getOrDefault(firstInstInCallee, false)) {
                    dfs(firstInstInCallee, graph, visited)
                }
            }
            orderedInstructions.add(inst)
        }

        private fun topologicalSort(graph: PandaApplicationGraph) {
            orderedInstructions.clear()
            val visited = mutableMapOf<PandaInst, Boolean>()
            val methods = graph.project.classes.flatMap { it.methods }
            val startInst = methods.filter { it.name == "func_main_0" }.single().instructions.first()
            dfs(startInst, graph, visited)
            for (method in methods) {
                val firstInst = method.instructions.first()
                if (!visited.getOrDefault(startInst, false)) {
                    dfs(firstInst, graph, visited)
                }
            }
            orderedInstructions.reverse()
        }

        private fun getOperands(inst: PandaInst): List<PandaValue> {
            return inst.operands.flatMap { expr -> expr.operands }
        }

        private fun analyse(programName: String, startMethods: List<String>? = null): List<Pair<String, PandaInst>> {
            val parser = loadIr("/samples/${programName}.json")
            val project = parser.getProject()
            val graph = PandaApplicationGraphImpl(project)

            topologicalSort(graph)

            val instToGlobalVars = mutableMapOf<PandaInst, MutableSet<String>>()

            val unresolvedVariables = mutableListOf<Pair<String, PandaInst>>()

            for (inst in orderedInstructions) {
                var predecessorInstructions = mutableListOf<PandaInst>()
                if (inst.location.index == 0) {
                    predecessorInstructions.addAll(graph.callers(inst.location.method))
                }
                predecessorInstructions.addAll(graph.predecessors(inst).toList())
                if (predecessorInstructions.isEmpty()) {
                    instToGlobalVars[inst] = mutableSetOf()
                } else {
                    instToGlobalVars[inst] = instToGlobalVars[predecessorInstructions.first()]!!.toMutableSet()
                }
                for (predecessorInst in graph.predecessors(inst).drop(1)) {
                    instToGlobalVars[inst]!!.intersect(instToGlobalVars[predecessorInst]!!)
                }
                if (inst is PandaAssignInst && inst.varName != null) {
                    instToGlobalVars[inst]!!.add(inst.varName!!)
                }
                // adhoc check for tryldglobalname, TODO(): check trysttoglobalname (for both will be cooler after better global variable processing)
                val probablyUndefinedVarNames = getOperands(inst).mapNotNull { op ->
                    if (op is PandaLoadedValue && op.instance is PandaStringConstant) {
                        ((op.instance) as PandaStringConstant).value
                    } else null
                }

                val stdVarNames = listOf("console") // TODO(): need more smart filter
                probablyUndefinedVarNames.forEach { varName ->
                    if (varName !in stdVarNames && varName !in instToGlobalVars[inst]!!) {
                        unresolvedVariables.add(Pair(varName, inst))
                        logger.info { "unresolved variable $varName in $inst with location: (method:${inst.location.method}, index: ${inst.location.index})" }
                    }
                }
            }

            return unresolvedVariables
        }

        @Test
        fun `counterexample - program that read some unresolved variables`() {
            val unresolvedVariables = analyse(
                programName = "codeqlSamples/unresolvedVariable",
            )
            assert(unresolvedVariables.size == 4)
        }
    }


    enum class ImplicitCastAnalysisMode {
        DETECTION,
        POSSIBILITY_CHECK
    }
    @Nested
    inner class ImplicitCastingTest {
        private fun analyse(programName: String, startMethods: List<String>, mode: ImplicitCastAnalysisMode = ImplicitCastAnalysisMode.DETECTION): List<PandaInst> {
            val parser = loadIr("/samples/${programName}.json")
            val project = parser.getProject()
            val graph = PandaApplicationGraphImpl(project)
            val methods = graph.project.classes.flatMap { it.methods }
            val typeMismatches = mutableListOf<PandaInst>()
            for (method in methods) {
                if (startMethods.contains(method.name)) {
                    for (inst in method.instructions) {
                        if (inst is PandaAssignInst && inst.rhv is PandaBinaryExpr) {
                            val operation = inst.rhv
                            val (leftOp, rightOp) = operation.operands
                            // TODO(): extend analysis for more complex scenarios
                            if (leftOp.type == PandaAnyType || rightOp.type == PandaAnyType) {
                                continue
                            }
                            if (mode == ImplicitCastAnalysisMode.DETECTION) {
                                if (leftOp.type != rightOp.type) {
                                    logger.info { "implicit casting in: $inst ($leftOp has ${leftOp.type} type, but $rightOp has ${rightOp.type} type)" }
                                    typeMismatches.add(inst)
                                }
                            }
                            if (mode == ImplicitCastAnalysisMode.POSSIBILITY_CHECK) {
                                val fineOperations = listOf(
                                    PandaAddExpr::class,
                                    PandaCmpExpr::class,
                                    PandaConditionExpr::class
                                )
                                val numberOperations = listOf(
                                    PandaSubExpr::class,
                                    PandaDivExpr::class,
                                    PandaModExpr::class,
                                    PandaExpExpr::class,
                                    PandaMulExpr::class
                                )
                                val isNumeric: (Any) -> Boolean = { value ->
                                    when (value) {
                                        is String -> value.toDoubleOrNull() != null
                                        is Int -> true
                                        else -> throw IllegalArgumentException("Unexpected type")
                                    }
                                }
                                val typePriority = mapOf(
                                    PandaStringType::class to 0,
                                    PandaNumberType::class to 1,
                                    PandaBoolType::class to 2
                                )
                                val sortedOps = listOf(leftOp, rightOp)
                                sortedOps.sortedBy { elem ->
                                    typePriority[elem.type::class]
                                }
                                if (numberOperations.any { it.isInstance(operation) }) {
                                    if (sortedOps[0] is PandaConstantWithValue && sortedOps[1] is PandaConstantWithValue) {
                                        if (!isNumeric((sortedOps[0] as PandaConstantWithValue).value!!) || !isNumeric((sortedOps[1] as PandaConstantWithValue).value!!)) {
                                            logger.info { "implicit cast won't work in: $inst (both operands should implicitly cast to number)" }
                                            typeMismatches.add(inst)
                                        }
                                        else {
                                            logger.info { "successful implicit cast in: $inst (both operands implicitly cast to number)" }
                                        }
                                    }
                                    else {
                                        TODO("Extend on constants with no value!")
                                    }
                                }
                                else {
                                    logger.info { "implicit cast is not needed in: $inst" }
                                }
                            }
                        }
                    }
                }
            }
            return typeMismatches
        }

        @Test
        fun `test implicit casting observation in binary expressions with primitive literals`() {
            val typeMismatches = analyse(
                programName = "codeqlSamples/implicitCasting",
                startMethods = listOf("primitiveLiterals")
            )
            assert(typeMismatches.size == 10)
        }

        @Disabled("No type support for arrays and objects")
        @Test
        fun `test implicit casting observation in binary expressions with complex literals`() {
            val typeMismatches = analyse(
                programName = "codeqlSamples/implicitCasting",
                startMethods = listOf("complexLiterals")
            )
            assert(typeMismatches.size == 4)
        }

        @Test
        fun `test implicit casting checking in binary expressions with strings`() {
            val impossibleImplicitCasts = analyse(
                programName = "codeqlSamples/implicitCasting",
                startMethods = listOf("binaryOperationsWithStrings"),
                mode = ImplicitCastAnalysisMode.POSSIBILITY_CHECK
            )
            assert(impossibleImplicitCasts.size == 8)
        }

        @Disabled("Not supported yet")
        @Test
        fun `complicate test implicit casting checking in binary expressions with strings`() {
            val impossibleImplicitCasts = analyse(
                programName = "codeqlSamples/implicitCasting",
                startMethods = listOf("binaryOperationsWithStrings2"),
                mode = ImplicitCastAnalysisMode.POSSIBILITY_CHECK
            )
            assert(impossibleImplicitCasts.size == 2)
        }
    }
}
