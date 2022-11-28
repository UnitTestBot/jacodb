package org.utbot.jcdb.impl.cfg

import org.utbot.jcdb.api.*
import org.utbot.jcdb.api.cfg.ext.*
import org.utbot.jcdb.api.ext.findCommonSupertype
import org.utbot.jcdb.impl.cfg.util.ExprMapper
import org.utbot.jcdb.impl.cfg.util.FullExprSetCollector
import org.utbot.jcdb.impl.cfg.util.InstructionFilter
import org.utbot.jcdb.impl.cfg.util.typeName

internal class Simplifier {

    fun simplify(jcClasspath: JcClasspath, instList: JcRawInstList): JcRawInstList {
        var instructionList = cleanRepeatedAssignments(instList)

        do {
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
            val uses = computeUseCases(instructionList)
            val (replacements, instructionsToDelete) = computeReplacements(instructionList, uses)
            instructionList = instructionList
                .map(ExprMapper(replacements.toMap()))
                .filter(InstructionFilter { it !in instructionsToDelete })
        } while (replacements.isNotEmpty())

        instructionList = cleanSelfAssignments(instructionList)
        instructionList = normalizeTypes(jcClasspath, instructionList)

        return instructionList
    }


    private fun computeUseCases(instList: JcRawInstList): Map<JcRawSimpleValue, Set<JcRawInst>> {
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

    private fun cleanRepeatedAssignments(instList: JcRawInstList): JcRawInstList {
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
        return JcRawInstList(instructions)
    }

    private fun cleanSelfAssignments(instList: JcRawInstList): JcRawInstList {
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
        return JcRawInstList(instructions)
    }

    private fun computeReplacements(
        instList: JcRawInstList,
        uses: Map<JcRawSimpleValue, Set<JcRawInst>>
    ): Pair<Map<JcRawRegister, JcRawValue>, Set<JcRawInst>> {
        val replacements = mutableMapOf<JcRawRegister, JcRawValue>()
        val reservedValues = mutableSetOf<JcRawValue>()
        val replacedInsts = mutableSetOf<JcRawInst>()

        for (inst in instList) {
            if (inst is JcRawAssignInst) {
                val rhv = inst.rhv
                if (inst.lhv is JcRawSimpleValue
                    && rhv is JcRawRegister
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

    private fun computeAssignments(instList: JcRawInstList): Map<JcRawSimpleValue, Set<JcRawSimpleValue>> {
        val assignments = mutableMapOf<JcRawSimpleValue, MutableSet<JcRawSimpleValue>>()
        for (inst in instList) {
            if (inst is JcRawAssignInst) {
                val lhv = inst.lhv
                val rhv = inst.rhv
                if (lhv is JcRawRegister && rhv is JcRawRegister) {
                    assignments.getOrPut(lhv, ::mutableSetOf).add(rhv)
                }
            }
        }
        return assignments
    }

    private fun normalizeTypes(jcClasspath: JcClasspath, instList: JcRawInstList): JcRawInstList {
        val types = mutableMapOf<JcRawRegister, MutableSet<JcType>>()
        for (inst in instList) {
            if (inst is JcRawAssignInst && inst.lhv is JcRawRegister && inst.rhv !is JcRawNullConstant) {
                types.getOrPut(
                    inst.lhv as JcRawRegister,
                    ::mutableSetOf
                ) += jcClasspath.findTypeOrNull(inst.rhv.typeName.typeName)
                    ?: error("Could not fin type")
            }
        }
        val replacement = types.filterValues { it.size > 1 }
            .mapValues {
                val supertype = jcClasspath.findCommonSupertype(it.value)
                    ?: error("Could not find common supertype of ${it.value.joinToString { it.typeName }}")
                JcRawRegister(it.key.index, supertype.typeName.typeName())
            }
        return instList.map(ExprMapper(replacement.toMap()))
    }
}
