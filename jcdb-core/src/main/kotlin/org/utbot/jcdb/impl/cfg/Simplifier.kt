package org.utbot.jcdb.impl.cfg

import org.utbot.jcdb.api.*
import org.utbot.jcdb.api.cfg.DefaultJcRawInstVisitor
import org.utbot.jcdb.api.cfg.ext.applyAndGet
import org.utbot.jcdb.api.cfg.ext.filter
import org.utbot.jcdb.api.cfg.ext.map
import org.utbot.jcdb.api.cfg.ext.mapNotNull
import org.utbot.jcdb.impl.cfg.util.ExprMapper
import org.utbot.jcdb.impl.cfg.util.FullExprSetCollector
import org.utbot.jcdb.impl.cfg.util.InstructionFilter

internal class UseCaseComputer : DefaultJcRawInstVisitor<Unit> {
    val uses = mutableMapOf<JcRawSimpleValue, MutableSet<JcRawInst>>()
    override val defaultInstHandler: (JcRawInst) -> Unit
        get() = { inst ->
            inst.operands
                .flatMapTo(mutableSetOf()) { expr -> expr.applyAndGet(FullExprSetCollector()) { it.exprs } }
                .filterIsInstance<JcRawSimpleValue>()
                .filter { it !is JcRawConstant }
                .forEach {
                    uses.getOrPut(it, ::mutableSetOf).add(inst)
                }
        }

    override fun visitJcRawAssignInst(inst: JcRawAssignInst) {
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

    override fun visitJcRawCatchInst(inst: JcRawCatchInst) {}
}

internal class RepeatedAssignmentCleaner : DefaultJcRawInstVisitor<JcRawInst?> {
    private val equalities = mutableMapOf<JcRawValue, JcRawValue>()
    override val defaultInstHandler: (JcRawInst) -> JcRawInst?
        get() = { it }

    override fun visitJcRawAssignInst(inst: JcRawAssignInst): JcRawInst? {
        val rhv = inst.rhv
        if (rhv is JcRawValue) {
            if (equalities[inst.lhv] == rhv) {
                return null
            } else {
                equalities[inst.lhv] = rhv
            }
        }
        return inst
    }

    override fun visitJcRawLabelInst(inst: JcRawLabelInst): JcRawInst {
        equalities.clear()
        return inst
    }
}

internal class ReplacementComputer(private val uses: Map<JcRawValue, Set<JcRawInst>>) : DefaultJcRawInstVisitor<Unit> {
    val replacements = mutableMapOf<JcRawRegister, JcRawValue>()
    val reservedValues = mutableSetOf<JcRawValue>()
    val replacedInsts = mutableSetOf<JcRawInst>()

    override val defaultInstHandler: (JcRawInst) -> Unit
        get() = { }

    override fun visitJcRawAssignInst(inst: JcRawAssignInst) {
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

internal class Simplifier {
    fun simplify(instList: JcRawInstList): JcRawInstList {
        var instructionList = instList.mapNotNull(RepeatedAssignmentCleaner())

        do {
            val uses = instructionList.applyAndGet(UseCaseComputer()) { it.uses }
            val oldSize = instructionList.instructions.size
            instructionList = instructionList.filter(InstructionFilter {
                !(it is JcRawAssignInst
                        && it.lhv is JcRawSimpleValue
                        && it.rhv is JcRawValue
                        && uses.getOrDefault(it.lhv, 0) == 0)
            })
        } while (instructionList.instructions.size != oldSize)

        do {
            val uses = instructionList.applyAndGet(UseCaseComputer()) { it.uses }
            val (replacements, instructionsToDelete) = instructionList.applyAndGet(ReplacementComputer(uses.toMap())) {
                it.replacements to it.replacedInsts
            }
            instructionList = instructionList
                .map(ExprMapper(replacements.toMap()))
                .filter(InstructionFilter { it !in instructionsToDelete })
        } while (replacements.isNotEmpty())

        return instructionList
    }
}
