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
import org.jacodb.api.cfg.AbstractFullRawExprSetCollector
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcRawAssignInst
import org.jacodb.api.cfg.JcRawCatchInst
import org.jacodb.api.cfg.JcRawComplexValue
import org.jacodb.api.cfg.JcRawConstant
import org.jacodb.api.cfg.JcRawExpr
import org.jacodb.api.cfg.JcRawInst
import org.jacodb.api.cfg.JcRawLabelInst
import org.jacodb.api.cfg.JcRawLocalVar
import org.jacodb.api.cfg.JcRawNullConstant
import org.jacodb.api.cfg.JcRawSimpleValue
import org.jacodb.api.cfg.JcRawValue
import org.jacodb.api.ext.cfg.applyAndGet
import org.jacodb.impl.cfg.util.ExprMapper
import org.jacodb.impl.cfg.util.InstructionFilter

/**
 * a class that simplifies the instruction list after construction
 * a simplification process is required, because the construction process
 * naturally introduces some redundancy into the code (mainly because of
 * the frames merging)
 */
internal class Simplifier {

    fun simplify(jcClasspath: JcClasspath, instList: JcInstList<JcRawInst>): JcInstList<JcRawInst> {
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
            val replacements = assignmentsMap
                .filter { (assignmentsMap[it.value.first()]?.let { it.size == 1 } ?: true) }
                .filterValues { it.first() is JcRawLocalVar && it.drop(1).all { it !is JcRawLocalVar } }
                .map { it.key to it.value.first() }
                .toMap()
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
        return normalizeTypes(instructionList)
    }

    private fun computeUseCases(instList: JcInstList<JcRawInst>): Map<JcRawSimpleValue, Set<JcRawInst>> {
        val uses = hashMapOf<JcRawSimpleValue, MutableSet<JcRawInst>>()
        for (inst in instList) {
            when (inst) {
                is JcRawAssignInst -> {
                    if (inst.lhv is JcRawComplexValue) {
                        inst.lhv.applyAndGet(SimplifierCollector()) { it.exprs }
                            .forEach {
                                uses.getOrPut(it, ::mutableSetOf).add(inst)
                            }
                    }
                    inst.rhv.applyAndGet(SimplifierCollector()) { it.exprs }
                        .forEach {
                            uses.getOrPut(it, ::mutableSetOf).add(inst)
                        }
                }

                is JcRawCatchInst -> {}

                else -> {
                    inst.applyAndGet(SimplifierCollector()) { it.exprs }
                        .forEach {
                            uses.getOrPut(it, ::mutableSetOf).add(inst)
                        }
                }
            }
        }
        return uses
    }

    private fun cleanRepeatedAssignments(instList: JcInstList<JcRawInst>): JcInstList<JcRawInst> {
        val instructions = mutableListOf<JcRawInst>()
        val equalities = hashMapOf<JcRawSimpleValue, JcRawSimpleValue>()
        for (inst in instList) {
            when (inst) {
                is JcRawAssignInst -> {
                    val lhv = inst.lhv
                    val rhv = inst.rhv
                    if (lhv is JcRawSimpleValue && rhv is JcRawSimpleValue) {
                        val iterator = equalities.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (entry.value == lhv) {
                                iterator.remove()
                            }
                        }
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

    private fun cleanSelfAssignments(instList: JcInstList<JcRawInst>): JcInstList<JcRawInst> {
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
        instList: JcInstList<JcRawInst>,
        uses: Map<JcRawSimpleValue, Set<JcRawInst>>,
    ): Pair<Map<JcRawLocalVar, JcRawValue>, Set<JcRawInst>> {
        val replacements = mutableMapOf<JcRawLocalVar, JcRawValue>()
        val reservedValues = mutableSetOf<JcRawValue>()
        val replacedInsts = mutableSetOf<JcRawInst>()

        for (inst in instList) {
            if (inst is JcRawAssignInst) {
                val rhv = inst.rhv
                if (inst.lhv is JcRawSimpleValue
                    && rhv is JcRawLocalVar
                    && uses.getOrDefault(inst.rhv, emptySet()).let { it.size == 1 && it.firstOrNull() == inst }
                    && rhv !in reservedValues
                ) {
                    val lhv = inst.lhv
                    val lhvUsage = uses.getOrDefault(lhv, emptySet()).firstOrNull()
                    val assignInstructionToReplacement = instList.firstOrNull { it is JcRawAssignInst && it.lhv == lhv }
                    val didNotAssignedBefore =
                        lhvUsage == null ||
                            assignInstructionToReplacement == null ||
                            !instList.isBefore(assignInstructionToReplacement, lhvUsage)
                    if (lhvUsage == null || !instList.isBefore(lhvUsage, inst)) {
                        if (didNotAssignedBefore) {
                            replacements[rhv] = lhv
                            reservedValues += lhv
                            replacedInsts += inst
                        }
                    }
                }
            }
        }

        return replacements to replacedInsts
    }

    private fun JcInstList<JcRawInst>.isBefore(one: JcRawInst, another: JcRawInst): Boolean {
        return indexOf(one) < indexOf(another)
    }

    private fun computeAssignments(instList: JcInstList<JcRawInst>): Map<JcRawSimpleValue, Set<JcRawExpr>> {
        val assignments = mutableMapOf<JcRawSimpleValue, MutableSet<JcRawExpr>>()
        for (inst in instList) {
            if (inst is JcRawAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv
                if (lhv is JcRawLocalVar) {
                    assignments.getOrPut(lhv, ::mutableSetOf).add(rhv)
                }
            }
        }
        return assignments
    }

    private fun normalizeTypes(
        instList: JcInstList<JcRawInst>,
    ): JcInstList<JcRawInst> {
        val types = mutableMapOf<JcRawLocalVar, MutableSet<String>>()
        for (inst in instList) {
            if (inst is JcRawAssignInst && inst.lhv is JcRawLocalVar && inst.rhv !is JcRawNullConstant) {
                types.getOrPut(
                    inst.lhv as JcRawLocalVar,
                    ::mutableSetOf
                ) += inst.rhv.typeName.typeName
            }
        }
        val replacement = types.filterValues { it.size > 1 }
            .mapValues {
                JcRawLocalVar(it.key.name, it.key.typeName)
            }
        return instList.map(ExprMapper(replacement.toMap()))
    }
}

private class SimplifierCollector : AbstractFullRawExprSetCollector() {
    val exprs = hashSetOf<JcRawSimpleValue>()

    override fun ifMatches(expr: JcRawExpr) {
        if (expr is JcRawSimpleValue && expr !is JcRawConstant) {
            exprs.add(expr)
        }
    }

}

private class RawLocalVarCollector(private val localVar: JcRawValue) : AbstractFullRawExprSetCollector() {

    var hasVar = false

    override fun ifMatches(expr: JcRawExpr) {
        if (!hasVar) {
            hasVar = expr is JcRawValue && expr == localVar
        }
    }
}

fun JcRawInst.hasExpr(variable: JcRawValue): Boolean {
    return RawLocalVarCollector(variable).also {
        accept(it)
    }.hasVar
}
