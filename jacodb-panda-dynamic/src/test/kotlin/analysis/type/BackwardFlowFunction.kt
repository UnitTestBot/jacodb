package analysis.type

import analysis.type.BackwardTypeDomainFact.TypedVariable
import analysis.type.BackwardTypeDomainFact.Zero
import org.jacodb.analysis.ifds.FieldAccessor
import org.jacodb.analysis.ifds.FlowFunction
import org.jacodb.analysis.ifds.FlowFunctions
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.panda.dynamic.ets.base.EtsAssignStmt
import org.jacodb.panda.dynamic.ets.base.EtsInstanceCallExpr
import org.jacodb.panda.dynamic.ets.base.EtsLValue
import org.jacodb.panda.dynamic.ets.base.EtsRef
import org.jacodb.panda.dynamic.ets.base.EtsReturnStmt
import org.jacodb.panda.dynamic.ets.base.EtsStmt
import org.jacodb.panda.dynamic.ets.model.EtsMethod
import org.jacodb.panda.dynamic.ets.utils.callExpr

class BackwardFlowFunction(
    val graph: ApplicationGraph<EtsMethod, EtsStmt>
) : FlowFunctions<BackwardTypeDomainFact, EtsMethod, EtsStmt> {
    override fun obtainPossibleStartFacts(method: EtsMethod) = listOf(Zero)

    override fun obtainSequentFlowFunction(
        current: EtsStmt,
        next: EtsStmt
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> sequentZero(current)
            is TypedVariable -> sequentFact(current, fact)
        }
    }

    private fun sequentZero(current: EtsStmt): List<BackwardTypeDomainFact> {
        val result = mutableListOf<BackwardTypeDomainFact>(Zero)

        if (current is EtsReturnStmt) {
            val variable = current.returnValue?.toBase()
            if (variable != null) {
                result += TypedVariable(variable, EtsTypeFact.UnknownEtsTypeFact)
            }
        }

        return result
    }

    private fun sequentFact(current: EtsStmt, fact: TypedVariable): List<BackwardTypeDomainFact> {
        if (current !is EtsAssignStmt) {
            return listOf(fact)
        }

        val lhv = current.lhv.toPath()

        val rhv = when (val r = current.rhv) {
            is EtsRef -> r.toPath()
            is EtsLValue -> r.toPath()
            else -> {
                System.err.println("TODO backward assign: $current")
                return listOf(fact)
            }
        }

        if (fact.variable != lhv.base) return listOf(fact)

        if (lhv.accesses.isEmpty() && rhv.accesses.isEmpty()) {
            return listOf(TypedVariable(rhv.base, fact.type))
        }

        if (lhv.accesses.isEmpty()) {
            val rhvAccessor = rhv.accesses.single()

            if (rhvAccessor !is FieldAccessor) {
                TODO("$rhvAccessor")
            }

            val rhvType = EtsTypeFact.ObjectEtsTypeFact(cls = null, properties = mapOf(rhvAccessor.name to fact.type))
            return listOf(TypedVariable(rhv.base, rhvType))
        }

        check(lhv.accesses.isNotEmpty() && rhv.accesses.isEmpty())
        val lhvAccessor = lhv.accesses.single()

        if (lhvAccessor !is FieldAccessor) {
            TODO("$lhvAccessor")
        }

        // todo: check fact has object type
        val (typeWithoutProperty, propertyType) = fact.type.removePropertyType(lhvAccessor.name)

        val updatedFact = TypedVariable(fact.variable, typeWithoutProperty)
        val rhvType = propertyType?.let { TypedVariable(rhv.base, it) }
        return listOfNotNull(updatedFact, rhvType)
    }

    private fun EtsTypeFact.removePropertyType(propertyName: String): Pair<EtsTypeFact, EtsTypeFact?> = when (this) {
        is EtsTypeFact.ObjectEtsTypeFact -> {
            val propertyType = properties[propertyName]
            val updatedThis = EtsTypeFact.ObjectEtsTypeFact(cls, properties.minus(propertyName))
            updatedThis to propertyType
        }

        is EtsTypeFact.IntersectionEtsTypeFact -> TODO()
        is EtsTypeFact.UnionEtsTypeFact -> TODO()
        else -> this to null
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> callToReturn(callStatement, returnSite, fact)
        }
    }

    private fun callToReturn(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        fact: TypedVariable
    ): List<BackwardTypeDomainFact> {
        val result = mutableListOf<BackwardTypeDomainFact>()

        val callExpr = callStatement.callExpr ?: error("No call")
        if (callExpr is EtsInstanceCallExpr) {
            val instance = callExpr.instance.toBase()

            val objectWithMethod = EtsTypeFact.ObjectEtsTypeFact(
                cls = null,
                properties = mapOf(callExpr.method.name to EtsTypeFact.FunctionEtsTypeFact)
            )
            result.add(TypedVariable(instance, objectWithMethod))
        }

        val callResultValue = (callStatement as? EtsAssignStmt)?.lhv
        if (callResultValue != null) {
            val callResultPath = callResultValue.toBase()
            if (fact.variable == callResultPath) return result
        }

        result.add(fact)
        return result
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: EtsStmt,
        calleeStart: EtsStmt
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> callToStart(callStatement, calleeStart, fact)
        }
    }

    private fun callToStart(
        callStatement: EtsStmt,
        calleeStart: EtsStmt,
        fact: TypedVariable
    ): List<BackwardTypeDomainFact> {
        val callResultValue = (callStatement as? EtsAssignStmt)?.lhv ?: return emptyList()

        val callResultPath = callResultValue.toBase()

        if (fact.variable != callResultPath) return emptyList()

        if (calleeStart !is EtsReturnStmt) return emptyList()
        val exitValuePath = calleeStart.returnValue?.toBase() ?: return emptyList()

        return listOf(TypedVariable(exitValuePath, fact.type))
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt
    ): FlowFunction<BackwardTypeDomainFact> = FlowFunction { fact ->
        when (fact) {
            Zero -> listOf(fact)
            is TypedVariable -> exitToReturn(callStatement, returnSite, exitStatement, fact)
        }
    }

    private fun exitToReturn(
        callStatement: EtsStmt,
        returnSite: EtsStmt,
        exitStatement: EtsStmt,
        fact: TypedVariable
    ): List<BackwardTypeDomainFact> {
        val factVariableBase = fact.variable
        val callExpr = callStatement.callExpr ?: error("No call")

        when (factVariableBase) {
            is AccessPathBase.This -> {
                if (callExpr !is EtsInstanceCallExpr) {
                    return emptyList()
                }

                val instance = callExpr.instance.toBase()
                return listOf(TypedVariable(instance, fact.type))
            }

            is AccessPathBase.Arg -> {
                val arg = callExpr.args.getOrNull(factVariableBase.index)?.toBase() ?: return emptyList()
                return listOf(TypedVariable(arg, fact.type))
            }

            else -> return emptyList()
        }
    }
}