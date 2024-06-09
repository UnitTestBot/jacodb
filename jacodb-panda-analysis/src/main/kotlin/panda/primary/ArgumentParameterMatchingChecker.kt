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

class ArgumentParameterMatchingChecker(val project: PandaProject) {
    val graph = PandaApplicationGraphImpl(project)

    fun analyseOneCase(startMethods: List<String>): List<Pair<PandaInst, PandaMethod>> {
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
                    var isError = false
                    if (callExpr.args.size == callee.parameters.size) {
                        continue
                    }
                    if (callExpr.args.size > callee.parameters.size) {
                        isError = true
                    }
                    if (callExpr.args.size < callee.parameters.size) {
                        val diff = callee.parameters.size - callExpr.args.size
                        val paramsWithDefaultValue = callee.parameters.takeLast(diff)

                        val findDefaultValueBlock: (PandaMethodParameter) -> Boolean = { param ->
                            var found = false
                            for (index in callee.instructions.indices) {
                                if (index == callee.instructions.size - 1) {
                                    break
                                }
                                val inst = callee.instructions[index]
                                if (inst is PandaAssignInst && inst.rhv is PandaStrictEqExpr) {
                                    val operands = inst.rhv.operands
                                    if (operands[0] is PandaArgument && (operands[0] as PandaArgument).index == param.index && operands[1].type is PandaUndefinedType) {
                                        val leftOp = inst.lhv
                                        val nextInst = callee.instructions[index + 1]
                                        if (nextInst is PandaAssignInst && nextInst.rhv is PandaCmpExpr) {
                                            val nextOperands = nextInst.rhv.operands
                                            if (nextOperands[0] == leftOp && nextOperands[1] == PandaNumberConstant(0)) {
                                                found = true
                                                break
                                            }
                                        }
                                    }
                                }
                            }
                            found
                        }

                        for (param in paramsWithDefaultValue) {
                            if (!findDefaultValueBlock(param)) {
                                isError = true
                                break
                            }
                        }
                    }
                    if (isError) {
                        mismatches.add(Pair(inst, callee))
                        logger.info { "parameter-argument count mismatch for call: $inst (expected ${callee.parameters.size} arguments, but got ${callExpr.args.size})" }
                    }
                }
            }
        }
        return mismatches
    }
}

