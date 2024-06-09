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

class UndeclaredVariablesAnalyser(val project: PandaProject) {
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

    // TODO(): expand for writing (trysttoglobalbyname) and constants (stconsttoglobalbyname)
    fun analyse(): List<Pair<String, PandaInst>> {
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
            val probablyUndefinedVarNames = inst.recursiveOperands.mapNotNull { op ->
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
}