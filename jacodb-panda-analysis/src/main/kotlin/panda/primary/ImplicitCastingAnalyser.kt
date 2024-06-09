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

enum class ImplicitCastAnalysisMode {
    DETECTION,
    POSSIBILITY_CHECK
}

class ImplicitCastingAnalyser(val project: PandaProject) {
    val graph = PandaApplicationGraphImpl(project)

    fun analyseOneCase(
        startMethods: List<String>,
        mode: ImplicitCastAnalysisMode = ImplicitCastAnalysisMode.DETECTION
    ): List<PandaInst> {
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
                                    } else {
                                        logger.info { "successful implicit cast in: $inst (both operands implicitly cast to number)" }
                                    }
                                } else {
                                    TODO("Extend on constants with no value!")
                                }
                            } else {
                                logger.info { "implicit cast is not needed in: $inst" }
                            }
                        }
                    }
                }
            }
        }
        return typeMismatches
    }
}

