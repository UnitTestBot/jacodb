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

package panda.primary

import org.jacodb.panda.dynamic.api.*

private val logger = mu.KotlinLogging.logger {}

enum class VarAccess {
    READ, WRITE
}

class UndeclaredVariablesChecker(val project: PandaProject) {
    val graph = PandaApplicationGraphImpl(project)

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

    fun analyse(
        checkConstReassignment: Boolean = false
    ): List<VariableError> {
        topologicalSort(graph)

        val instToGlobalVars = mutableMapOf<PandaInst, MutableSet<String>>()

        val isVarConstantMap = mutableMapOf<String, Boolean>()
//        isVarConstantMap["unknown."] = true

        val unresolvedVariables = mutableListOf<UndeclaredVariablesCheckerError>()

        val potentialConstReassignments = mutableListOf<UndeclaredVariablesCheckerError>()

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
                val varName = inst.varName!!
                val name = when {
                    varName.startsWith("constant.") -> {
                        val slicedName = varName.substring(9)
                        isVarConstantMap[slicedName] = true
                        slicedName
                    }
                    else -> varName
                }
                instToGlobalVars[inst]!!.add(name)
            }

            // TODO("refactor after better global variable processing in IR")
            // check for tryldglobalname
            val probablyUndefinedVarNames = inst.recursiveOperands.mapNotNull { op ->
                if (op is PandaLoadedValue && op.instance is PandaStringConstant) {
                    Pair(
                        ((op.instance) as PandaStringConstant).value,
                        VarAccess.READ
                    )
                } else null
            }.toMutableList()

            // check for trystglobalbyname
            if (inst is PandaAssignInst) {
                if (inst.lhv is PandaLoadedValue) {
                    val lhvInstance = (inst.lhv as PandaLoadedValue).instance
                    if (lhvInstance is PandaStringConstant) {
                        probablyUndefinedVarNames.add(
                            Pair(
                                lhvInstance.value,
                                VarAccess.WRITE
                            )
                        )
                    }
                }
//                if (inst.lhv is PandaLocalVar && (inst.lhv as PandaLocalVar).isConst) {
//                    probablyUndefinedVarNames.add(
//                        Pair(
//                            "unknown.",
//                            VarAccess.WRITE
//                        )
//                    )
//                }
            }

            val stdVarNames = listOf("console") // TODO(): need more smart filter
            probablyUndefinedVarNames.forEach { varInfo ->
                val varName = varInfo.first
                val varAccess = varInfo.second
                if (varName !in stdVarNames && varName !in instToGlobalVars[inst]!!) {
                    unresolvedVariables.add(
                        UndeclaredVariablesCheckerError(
                            varName = varName,
                            inst = inst,
                            varAccess = varAccess,
                            isConstant = lazy {
                                isVarConstantMap.getOrDefault(varName, false)
                            }
                        )
                    )
                }
                if (checkConstReassignment && varName in instToGlobalVars[inst]!!) {
                    if (varAccess == VarAccess.WRITE) {
                        potentialConstReassignments.add(
                            UndeclaredVariablesCheckerError(
                                varName = varName,
                                inst = inst,
                                varAccess = VarAccess.WRITE,
                                isConstant = lazy {
                                    isVarConstantMap.getOrDefault(varName, false)
                                },
                                fictive = true
                            )
                        )
                    }
                }
            }
        }

        val varAccessToStr: (VarAccess) -> String = { varAccess ->
            when(varAccess) {
                VarAccess.WRITE -> "Write"
                VarAccess.READ -> "Read"
            }
        }

        val isVarConstantToString: (Boolean) -> String = { isVarConstant ->
            when(isVarConstant) {
                true -> "constant"
                false -> "regular"
            }
        }

        if (checkConstReassignment) {
            val constReassignments = potentialConstReassignments.filter { it ->
                it.isConstant.value
            }.toMutableList()
            constReassignments.addAll(
                unresolvedVariables.filter { err ->
                    err.isConstant.value && err.varAccess == VarAccess.WRITE
                }
            )
            val allErrors = constReassignments.map { err ->
                ConstAssignmentCheckerError(
                    varName = err.varName,
                    inst = err.inst,
                    errorType = when(err.fictive) {
                        true -> ConstAssignmentCheckerErrorType.CONST_GLOBAL_VARIABLE_REASSIGNMENT
                        false -> ConstAssignmentCheckerErrorType.ACCESS_TO_UNDECLARED_CONST_VARIABLE
                    }
                )
            }
            return allErrors
        }

        unresolvedVariables.forEach { err ->
            logger.info { "${varAccessToStr(err.varAccess)} access to undeclared ${isVarConstantToString(err.isConstant.value)} variable ${err.varName} in ${err.inst} (location: ${err.inst.location})" }
        }

        return unresolvedVariables
    }
}