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

package org.jacodb.impl.cfg

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcType
import org.jacodb.api.cfg.JcRawAssignInst
import org.jacodb.api.cfg.JcRawCatchInst
import org.jacodb.api.cfg.JcRawComplexValue
import org.jacodb.api.cfg.JcRawConstant
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.cfg.JcRawLabelInst
import org.jacodb.api.cfg.JcRawLocalVar
import org.jacodb.api.cfg.JcRawNullConstant
import org.jacodb.api.cfg.JcRawSimpleValue
import org.jacodb.api.cfg.JcRawValue
import org.jacodb.api.ext.cfg.applyAndGet
import org.jacodb.impl.cfg.util.ExprMapper
import org.jacodb.impl.cfg.util.FullExprSetCollector
import org.jacodb.impl.cfg.util.InstructionFilter

/**
 * a class that simplifies the instruction list after construction
 * a simplification process is required, because the construction process
 * naturally introduces some redundancy into the code (mainly because of
 * the frames merging)
 */
internal class Simplifier {

    fun simplify(jcClasspath: JcClasspath, instList: JcInstListImpl<JcRawInst>): JcInstListImpl<JcRawInst> {
        // clear the assignments that are repeated inside single basic block
        var instructionList = cleanRepeatedAssignments(instList)

        do {
            // delete the assignments that are not used anywhere in the code
            // need to run this repeatedly, because deleting one instruction may
            // free another one
            val uses = computeUseCases(instructionList)
            val oldSize = instructionList.instructions.size
            instructionList = instructionList.filterNot(InstructionFilter {
                it is JcRawAssignInst
                        && it.lhv is JcRawSimpleValue
                        && it.rhv is JcRawValue
                        && uses.getOrDefault(it.lhv, 0) == 0
            })
        } while (instructionList.instructions.size != oldSize)

        do {
            // delete the assignments that are mutually dependent only on one another
            // (e.g. `a = b` and `b = a`) and not used anywhere else; also need to run several times
            // because of potential dependencies between such variables
            val assignmentsMap = computeAssignments(instructionList)
            val replacements = assignmentsMap.filterValues { it.size == 1 }.map { it.key to it.value.first() }.toMap()
            instructionList = instructionList
                .filterNot(InstructionFilter {
                    if (it !is JcRawAssignInst) return@InstructionFilter false
                    val lhv = it.lhv as? JcRawSimpleValue ?: return@InstructionFilter false
                    val rhv = it.rhv as? JcRawSimpleValue ?: return@InstructionFilter false
                    replacements[lhv] == rhv && replacements[rhv] == lhv
                })
                .map(ExprMapper(replacements.toMap()))
                .filterNot(InstructionFilter {
                    it is JcRawAssignInst && it.rhv == it.lhv
                })
        } while (replacements.isNotEmpty())

        do {
            // trying to remove all the simple variables that are equivalent to some other simple variable
            val uses = computeUseCases(instructionList)
            val (replacements, instructionsToDelete) = computeReplacements(instructionList, uses)
            instructionList = instructionList
                .map(ExprMapper(replacements.toMap()))
                .filter(InstructionFilter { it !in instructionsToDelete })
        } while (replacements.isNotEmpty())

        // remove instructions like `a = a`
        instructionList = cleanSelfAssignments(instructionList)
        // fix some typing errors and normalize the types of all local variables
        instructionList = normalizeTypes(jcClasspath, instructionList)

        return instructionList
    }


    private fun computeUseCases(instList: JcInstListImpl<JcRawInst>): Map<JcRawSimpleValue, Set<JcRawInst>> {
        val uses = mutableMapOf<JcRawSimpleValue, MutableSet<JcRawInst>>()
        for (inst in instList) {
            when (inst) {
                is JcRawAssignInst -> {
                    if (inst.lhv is JcRawComplexValue) {
                        inst.lhv.applyAndGet(FullExprSetCollector()) { it.exprs }
                            .filterIsInstance<JcRawSimpleValue>()
                            .filter { it !is JcRawConstant }
                            .forEach {
                                uses.getOrPut(it, ::mutableSetOf).add(inst)
                            }
                    }
                    inst.rhv.applyAndGet(FullExprSetCollector()) { it.exprs }
                        .filterIsInstance<JcRawSimpleValue>()
                        .filter { it !is JcRawConstant }
                        .forEach {
                            uses.getOrPut(it, ::mutableSetOf).add(inst)
                        }
                }

                is JcRawCatchInst -> {}
                else -> {
                    inst.operands
                        .flatMapTo(mutableSetOf()) { expr -> expr.applyAndGet(FullExprSetCollector()) { it.exprs } }
                        .filterIsInstance<JcRawSimpleValue>()
                        .filter { it !is JcRawConstant }
                        .forEach {
                            uses.getOrPut(it, ::mutableSetOf).add(inst)
                        }
                }
            }
        }
        return uses
    }

    private fun cleanRepeatedAssignments(instList: JcInstListImpl<JcRawInst>): JcInstListImpl<JcRawInst> {
        val instructions = mutableListOf<JcRawInst>()
        val equalities = mutableMapOf<JcRawSimpleValue, JcRawSimpleValue>()
        for (inst in instList) {
            when (inst) {
                is JcRawAssignInst -> {
                    val lhv = inst.lhv
                    val rhv = inst.rhv
                    if (lhv is JcRawSimpleValue && rhv is JcRawSimpleValue) {
                        if (equalities[lhv] != rhv) {
                            equalities[lhv] = rhv
                            instructions += inst
                        }
                    } else {
                        instructions += inst
                    }
                }

                is JcRawLabelInst -> {
                    instructions += inst
                    equalities.clear()
                }

                else -> instructions += inst
            }
        }
        return JcInstListImpl(instructions)
    }

    private fun cleanSelfAssignments(instList: JcInstListImpl<JcRawInst>): JcInstListImpl<JcRawInst> {
        val instructions = mutableListOf<JcRawInst>()
        for (inst in instList) {
            when (inst) {
                is JcRawAssignInst -> {
                    if (inst.lhv != inst.rhv) {
                        instructions += inst
                    }
                }

                else -> instructions += inst
            }
        }
        return JcInstListImpl(instructions)
    }

    private fun computeReplacements(
        instList: JcInstListImpl<JcRawInst>,
        uses: Map<JcRawSimpleValue, Set<JcRawInst>>
    ): Pair<Map<JcRawLocalVar, JcRawValue>, Set<JcRawInst>> {
        val replacements = mutableMapOf<JcRawLocalVar, JcRawValue>()
        val reservedValues = mutableSetOf<JcRawValue>()
        val replacedInsts = mutableSetOf<JcRawInst>()

        for (inst in instList) {
            if (inst is JcRawAssignInst) {
                val rhv = inst.rhv
                if (inst.lhv is JcRawSimpleValue
                    && rhv is JcRawLocalVar
                    && uses.getOrDefault(inst.rhv, emptySet()).firstOrNull() == inst
                    && rhv !in reservedValues
                ) {
                    replacements[rhv] = inst.lhv
                    reservedValues += inst.lhv
                    replacedInsts += inst
                }
            }
        }

        return replacements to replacedInsts
    }

    private fun computeAssignments(instList: JcInstListImpl<JcRawInst>): Map<JcRawSimpleValue, Set<JcRawSimpleValue>> {
        val assignments = mutableMapOf<JcRawSimpleValue, MutableSet<JcRawSimpleValue>>()
        for (inst in instList) {
            if (inst is JcRawAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv
                if (lhv is JcRawLocalVar && rhv is JcRawLocalVar) {
                    assignments.getOrPut(lhv, ::mutableSetOf).add(rhv)
                }
            }
        }
        return assignments
    }

    private fun normalizeTypes(jcClasspath: JcClasspath, instList: JcInstListImpl<JcRawInst>): JcInstListImpl<JcRawInst> {
        val types = mutableMapOf<JcRawLocalVar, MutableSet<JcType>>()
        for (inst in instList) {
            if (inst is JcRawAssignInst && inst.lhv is JcRawLocalVar && inst.rhv !is JcRawNullConstant) {
                types.getOrPut(
                    inst.lhv as JcRawLocalVar,
                    ::mutableSetOf
                ) += jcClasspath.findTypeOrNull(inst.rhv.typeName.typeName)
                    ?: error("Could not find type")
            }
        }
        val replacement = types.filterValues { it.size > 1 }
            .mapValues {
                JcRawLocalVar(it.key.name, it.key.typeName)
            }
        return instList.map(ExprMapper(replacement.toMap()))
    }
}