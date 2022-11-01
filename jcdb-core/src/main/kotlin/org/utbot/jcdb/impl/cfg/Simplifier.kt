package org.utbot.jcdb.impl.cfg

import org.utbot.jcdb.api.*
import org.utbot.jcdb.api.cfg.DefaultJcRawInstVisitor
import org.utbot.jcdb.api.cfg.JcRawExprVisitor
import org.utbot.jcdb.impl.cfg.util.ExprMapper

private fun JcRawExpr.fullExprSet(): Set<JcRawExpr> = FullExprSetCollector().let {
    this.accept(it)
    it.exprs
}

internal class FullExprSetCollector : JcRawExprVisitor<Unit> {
    val exprs = mutableSetOf<JcRawExpr>()

    override fun visitJcRawAddExpr(expr: JcRawAddExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawAndExpr(expr: JcRawAndExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmpExpr(expr: JcRawCmpExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmpgExpr(expr: JcRawCmpgExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawCmplExpr(expr: JcRawCmplExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawDivExpr(expr: JcRawDivExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawMulExpr(expr: JcRawMulExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawEqExpr(expr: JcRawEqExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawNeqExpr(expr: JcRawNeqExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawGeExpr(expr: JcRawGeExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawGtExpr(expr: JcRawGtExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLeExpr(expr: JcRawLeExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLtExpr(expr: JcRawLtExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawOrExpr(expr: JcRawOrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawRemExpr(expr: JcRawRemExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawShlExpr(expr: JcRawShlExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawShrExpr(expr: JcRawShrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawSubExpr(expr: JcRawSubExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawUshrExpr(expr: JcRawUshrExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawXorExpr(expr: JcRawXorExpr) {
        exprs.add(expr)
        expr.lhv.accept(this)
        expr.rhv.accept(this)
    }

    override fun visitJcRawLengthExpr(expr: JcRawLengthExpr) {
        exprs.add(expr)
        expr.array.accept(this)
    }

    override fun visitJcRawNegExpr(expr: JcRawNegExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawCastExpr(expr: JcRawCastExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawNewExpr(expr: JcRawNewExpr) {
        exprs.add(expr)
    }

    override fun visitJcRawNewArrayExpr(expr: JcRawNewArrayExpr) {
        exprs.add(expr)
        expr.dimensions.forEach { it.accept(this) }
    }

    override fun visitJcRawInstanceOfExpr(expr: JcRawInstanceOfExpr) {
        exprs.add(expr)
        expr.operand.accept(this)
    }

    override fun visitJcRawDynamicCallExpr(expr: JcRawDynamicCallExpr) {
        exprs.add(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawVirtualCallExpr(expr: JcRawVirtualCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawInterfaceCallExpr(expr: JcRawInterfaceCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawStaticCallExpr(expr: JcRawStaticCallExpr) {
        exprs.add(expr)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawSpecialCallExpr(expr: JcRawSpecialCallExpr) {
        exprs.add(expr)
        expr.instance.accept(this)
        expr.args.forEach { it.accept(this) }
    }

    override fun visitJcRawThis(value: JcRawThis) {
        exprs.add(value)
    }

    override fun visitJcRawArgument(value: JcRawArgument) {
        exprs.add(value)
    }

    override fun visitJcRawLocal(value: JcRawLocal) {
        exprs.add(value)
    }

    override fun visitJcRawRegister(value: JcRawRegister) {
        exprs.add(value)
    }

    override fun visitJcRawFieldRef(value: JcRawFieldRef) {
        exprs.add(value)
        value.instance?.accept(this)
    }

    override fun visitJcRawArrayAccess(value: JcRawArrayAccess) {
        exprs.add(value)
        value.array.accept(this)
        value.index.accept(this)
    }

    override fun visitJcRawBool(value: JcRawBool) {
        exprs.add(value)
    }

    override fun visitJcRawByte(value: JcRawByte) {
        exprs.add(value)
    }

    override fun visitJcRawChar(value: JcRawChar) {
        exprs.add(value)
    }

    override fun visitJcRawShort(value: JcRawShort) {
        exprs.add(value)
    }

    override fun visitJcRawInt(value: JcRawInt) {
        exprs.add(value)
    }

    override fun visitJcRawLong(value: JcRawLong) {
        exprs.add(value)
    }

    override fun visitJcRawFloat(value: JcRawFloat) {
        exprs.add(value)
    }

    override fun visitJcRawDouble(value: JcRawDouble) {
        exprs.add(value)
    }

    override fun visitJcRawNullConstant(value: JcRawNullConstant) {
        exprs.add(value)
    }

    override fun visitJcRawStringConstant(value: JcRawStringConstant) {
        exprs.add(value)
    }

    override fun visitJcRawClassConstant(value: JcRawClassConstant) {
        exprs.add(value)
    }

    override fun visitJcRawMethodConstant(value: JcRawMethodConstant) {
        exprs.add(value)
    }
}

internal class UseCaseComputer : DefaultJcRawInstVisitor<Unit> {
    val uses = mutableMapOf<JcRawValue, MutableSet<JcRawInst>>()
    override val defaultInstHandler: (JcRawInst) -> Unit
        get() = { inst ->
            inst.operands
                .flatMapTo(mutableSetOf()) { it.fullExprSet() }
                .filterIsInstance<JcRawValue>()
                .filter { it !is JcRawConstant }
                .forEach {
                    uses.getOrPut(it, ::mutableSetOf).add(inst)
                }
        }

    override fun visitJcRawAssignInst(inst: JcRawAssignInst) {
        inst.rhv.fullExprSet()
            .filterIsInstance<JcRawValue>()
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

internal class InstructionFilterer(val predicate: (JcRawInst) -> Boolean) : DefaultJcRawInstVisitor<JcRawInst?> {
    override val defaultInstHandler: (JcRawInst) -> JcRawInst?
        get() = {
            when {
                predicate(it) -> it
                else -> null
            }
        }
}

internal class ReplacementComputer(private val uses: Map<JcRawValue, Set<JcRawInst>>) : DefaultJcRawInstVisitor<Unit> {
    val replacements = mutableMapOf<JcRawRegister, JcRawValue>()
    val replacedInsts = mutableSetOf<JcRawInst>()

    override val defaultInstHandler: (JcRawInst) -> Unit
        get() = { }

    override fun visitJcRawAssignInst(inst: JcRawAssignInst) {
        val rhv = inst.rhv
        if (rhv is JcRawRegister && uses.getOrDefault(inst.rhv, emptySet()).firstOrNull() == inst) {
            replacements[rhv] = inst.lhv
            replacedInsts += inst
        }
    }
}

internal class Simplifier {
    fun simplify(instructionList: List<JcRawInst>): List<JcRawInst> {
        var instructions = RepeatedAssignmentCleaner().let { visitor ->
            instructionList.mapNotNull { it.accept(visitor) }
        }

        var deletions = true
        while (deletions) {
            deletions = false
            val uses = UseCaseComputer().let { visitor ->
                instructions.forEach { it.accept(visitor) }
                visitor.uses
            }
            instructions = InstructionFilterer {
                deletions = it is JcRawAssignInst && it.rhv is JcRawValue && uses.getOrDefault(it.lhv, 0) == 0
                !deletions
            }.let { visitor ->
                instructions.mapNotNull { it.accept(visitor) }
            }
        }

        deletions = true
        while (deletions) {
            deletions = false
            val uses = UseCaseComputer().let { visitor ->
                instructions.forEach { it.accept(visitor) }
                visitor.uses
            }
            val (replacements, deletionInsts) = ReplacementComputer(uses).let { visitor ->
                instructions.forEach { it.accept(visitor) }
                visitor.replacements to visitor.replacedInsts
            }
            deletions = replacements.isNotEmpty()
            instructions = ExprMapper(replacements.toMap()).let { visitor ->
                instructions.map { it.accept(visitor) }
            }
            instructions = InstructionFilterer { it !in deletionInsts }.let { visitor ->
                instructions.mapNotNull { it.accept(visitor) }
            }
        }

        return instructions
    }
}
