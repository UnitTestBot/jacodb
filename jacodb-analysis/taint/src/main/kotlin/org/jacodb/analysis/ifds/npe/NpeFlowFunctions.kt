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

package org.jacodb.analysis.ifds.npe

import org.jacodb.analysis.ifds.domain.CallAction
import org.jacodb.analysis.ifds.domain.FlowFunctions
import org.jacodb.analysis.ifds.config.BasicConditionEvaluator
import org.jacodb.analysis.ifds.config.CallPositionToAccessPathResolver
import org.jacodb.analysis.ifds.config.CallPositionToJcValueResolver
import org.jacodb.analysis.ifds.config.EntryPointPositionToAccessPathResolver
import org.jacodb.analysis.ifds.config.EntryPointPositionToJcValueResolver
import org.jacodb.analysis.ifds.config.FactAwareConditionEvaluator
import org.jacodb.analysis.ifds.config.TaintActionEvaluator
import org.jacodb.analysis.ifds.taint.TaintDomainFact
import org.jacodb.analysis.ifds.taint.TaintZeroFact
import org.jacodb.analysis.ifds.taint.Tainted
import org.jacodb.analysis.ifds.util.AccessPath
import org.jacodb.analysis.ifds.util.ElementAccessor
import org.jacodb.analysis.ifds.util.getArgumentsOf
import org.jacodb.analysis.ifds.util.onSome
import org.jacodb.analysis.ifds.util.startsWith
import org.jacodb.analysis.ifds.util.thisInstance
import org.jacodb.analysis.ifds.util.toPath
import org.jacodb.analysis.ifds.util.toPathOrNull
import org.jacodb.api.JcArrayType
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.api.ext.isNullable
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.RemoveAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.TaintCleaner
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough

private val logger = mu.KotlinLogging.logger {}

class ForwardNpeFlowFunctions(
    private val cp: JcClasspath,
    private val taintConfigurationFeature: TaintConfigurationFeature?,
) : FlowFunctions<JcInst, TaintDomainFact, JcMethod> {
    override fun obtainPossibleStartFacts(
        method: JcMethod,
    ): Collection<TaintDomainFact> = buildSet {
        addAll(obtainPossibleStartFactsBasic(method))

        // Possibly null arguments:
        for (p in method.parameters.filter { it.isNullable != false }) {
            val t = cp.findTypeOrNull(p.type)!!
            val arg = JcArgument.of(p.index, p.name, t)
            val path = arg.toPath()
            add(Tainted(path, TaintMark.NULLNESS))
        }
    }

    private fun obtainPossibleStartFactsBasic(
        method: JcMethod,
    ): Collection<TaintDomainFact> = buildSet {
        // Zero (reachability) fact always present at entrypoint:
        add(TaintZeroFact)

        // Extract initial facts from the config:
        val config = taintConfigurationFeature?.getConfigForMethod(method)
        if (config != null) {
            val conditionEvaluator = BasicConditionEvaluator(EntryPointPositionToJcValueResolver(cp, method))
            val actionEvaluator = TaintActionEvaluator(EntryPointPositionToAccessPathResolver(cp, method))

            // Handle EntryPointSource config items:
            for (item in config.filterIsInstance<TaintEntryPointSource>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        val result = when (action) {
                            is AssignMark -> actionEvaluator.evaluate(action)
                            else -> error("$action is not supported for $item")
                        }
                        result.onSome { addAll(it) }
                    }
                }
            }
        }
    }

    private fun transmitTaintAssign(
        fact: Tainted,
        from: JcExpr,
        to: JcValue,
    ): Collection<Tainted> {
        val toPath = to.toPath()
        val fromPath = from.toPathOrNull()

        if (fact.mark == TaintMark.NULLNESS) {
            // TODO: consider
            //  if (from is JcNewExpr
            //      || from is JcNewArrayExpr
            //      || from is JcConstant
            //      || (from is JcCallExpr && from.method.method.isNullable != true))
            if (fact.variable.startsWith(toPath)) {
                // NULLNESS is overridden:
                return emptySet()
            }
        }

        if (fromPath != null) {
            // Adhoc taint array:
            if (fromPath.accesses.isNotEmpty()
                && fromPath.accesses.last() is ElementAccessor
                && fromPath == (fact.variable / ElementAccessor)
            ) {
                val newTaint = fact.copy(variable = toPath)
                return setOf(fact, newTaint)
            }

            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath / tail
                val newTaint = fact.copy(variable = newPath)
                return setOf(fact, newTaint)
            }
        }

        return buildSet {
            if (from is JcNullConstant) {
                add(Tainted(toPath, TaintMark.NULLNESS))
            }

            if (fact.variable.startsWith(toPath)) {
                // 'to' was (sub-)tainted, but it is now overridden by 'from':
                return@buildSet
            } else {
                // Neither 'from' nor 'to' are tainted:
                add(fact)
            }
        }
    }

    private fun transmitTaintNormal(
        fact: Tainted,
        inst: JcInst,
    ): List<Tainted> {
        // Pass-through:
        return listOf(fact)
    }

    private fun generates(inst: JcInst): Collection<TaintDomainFact> = buildList {
        if (inst is JcAssignInst) {
            val toPath = inst.lhv.toPath()
            val from = inst.rhv
            if (from is JcNullConstant || (from is JcCallExpr && from.method.method.isNullable == true)) {
                add(Tainted(toPath, TaintMark.NULLNESS))
            } else if (from is JcNewArrayExpr && (from.type as JcArrayType).elementType.nullable != false) {
                val accessors = List((from.type as JcArrayType).dimensions) { ElementAccessor }
                val path = toPath / accessors
                add(Tainted(path, TaintMark.NULLNESS))
            }
        }
    }

    private val JcIfInst.pathComparedWithNull: AccessPath?
        get() {
            val expr = condition
            return if (expr.rhv is JcNullConstant) {
                expr.lhv.toPathOrNull()
            } else if (expr.lhv is JcNullConstant) {
                expr.rhv.toPathOrNull()
            } else {
                null
            }
        }

    override fun sequent(
        current: JcInst,
        next: JcInst,
        fact: TaintDomainFact,
    ): Collection<TaintDomainFact> {
        if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(current)) {
                return emptySet()
            }
        }

        if (current is JcIfInst) {
            val nextIsTrueBranch = next.location.index == current.trueBranch.index
            val pathComparedWithNull = current.pathComparedWithNull
            if (fact == TaintZeroFact) {
                if (pathComparedWithNull != null) {
                    if ((current.condition is JcEqExpr && nextIsTrueBranch) ||
                        (current.condition is JcNeqExpr && !nextIsTrueBranch)
                    ) {
                        // This is a hack: instructions like `return null` in branch of next will be considered only if
                        //  the fact holds (otherwise we could not get there)
                        // Note the absence of 'Zero' here!
                        return listOf(Tainted(pathComparedWithNull, TaintMark.NULLNESS))
                    }
                }
            } else if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
                val expr = current.condition
                if (pathComparedWithNull != fact.variable) {
                    return listOf(fact)
                }
                if ((expr is JcEqExpr && nextIsTrueBranch) || (expr is JcNeqExpr && !nextIsTrueBranch)) {
                    // comparedPath is null in this branch
                    return listOf(TaintZeroFact)
                } else {
                    return emptyList()
                }
            }
        }

        if (fact is TaintZeroFact) {
            return listOf(TaintZeroFact) + generates(current)
        }
        check(fact is Tainted)

        return if (current is JcAssignInst) {
            transmitTaintAssign(fact, from = current.rhv, to = current.lhv)
        } else {
            transmitTaintNormal(fact, current)
        }
    }

    private fun transmitTaint(
        fact: Tainted,
        at: JcInst,
        from: JcValue,
        to: JcValue,
    ): Collection<Tainted> = buildSet {
        if (fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(at)) {
                return@buildSet
            }
        }

        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        at: JcInst,
        from: JcValue, // actual
        to: JcValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        at: JcInst,
        from: JcValue, // formal
        to: JcValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        at: JcInst,
        from: JcValue, // instance
        to: JcThis, // this
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        at: JcInst,
        from: JcThis, // this
        to: JcValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        at: JcInst,
        from: JcValue,
        to: JcValue,
    ): Collection<Tainted> = transmitTaint(fact, at, from, to)

    override fun call(
        callStatement: JcInst,
        returnSite: JcInst,
        fact: TaintDomainFact,
    ): Collection<CallAction<TaintDomainFact>> {
        if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
            if (fact.variable.isDereferencedAt(callStatement)) {
                return listOf(CallAction.Start(fact))
            }
        }

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = callExpr.method.method

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        if (fact is Tainted
            && callExpr is JcDynamicCallExpr
            && callee.enclosingClass.name == "java.lang.invoke.StringConcatFactory"
            && callStatement is JcAssignInst
        ) {
            for (arg in callExpr.args) {
                if (arg.toPath() == fact.variable) {
                    return setOf(
                        CallAction.Return(fact),
                        CallAction.Return(fact.copy(variable = callStatement.lhv.toPath()))
                    )
                }
            }
            return setOf(CallAction.Return(fact))
        }

        val config = taintConfigurationFeature?.getConfigForMethod(callee)

        if (fact == TaintZeroFact) {
            return buildSet {
                add(CallAction.Return(TaintZeroFact))
                add(CallAction.Start(TaintZeroFact))

                if (callStatement is JcAssignInst) {
                    val toPath = callStatement.lhv.toPath()
                    val from = callStatement.rhv
                    if (from is JcNullConstant || (from is JcCallExpr && from.method.method.isNullable == true)) {
                        add(CallAction.Return(Tainted(toPath, TaintMark.NULLNESS)))
                    } else if (from is JcNewArrayExpr && (from.type as JcArrayType).elementType.nullable != false) {
                        val size = (from.type as JcArrayType).dimensions
                        val accessors = List(size) { ElementAccessor }
                        val path = toPath / accessors
                        add(CallAction.Return(Tainted(path, TaintMark.NULLNESS)))
                    }
                }

                if (config != null) {
                    val conditionEvaluator = BasicConditionEvaluator(CallPositionToJcValueResolver(callStatement))
                    val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))

                    // Handle MethodSource config items:
                    for (item in config.filterIsInstance<TaintMethodSource>()) {
                        if (item.condition.accept(conditionEvaluator)) {
                            for (action in item.actionsAfter) {
                                val result = when (action) {
                                    is AssignMark -> actionEvaluator.evaluate(action)
                                    else -> error("$action is not supported for $item")
                                }
                                result.onSome {
                                    it.mapTo(this) { CallAction.Return(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
        check(fact is Tainted)

        if (config != null) {
            // FIXME: adhoc
            if (callee.enclosingClass.name == "java.lang.StringBuilder" && callee.name == "append") {
                // Skip rules for StringBuilder::append in NPE analysis.
            } else {
                val facts = mutableSetOf<Tainted>()
                val conditionEvaluator = FactAwareConditionEvaluator(fact, CallPositionToJcValueResolver(callStatement))
                val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))
                var defaultBehavior = true

                // Handle PassThrough config items:
                for (item in config.filterIsInstance<TaintPassThrough>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        for (action in item.actionsAfter) {
                            val result = when (action) {
                                is CopyMark -> actionEvaluator.evaluate(action, fact)
                                is CopyAllMarks -> actionEvaluator.evaluate(action, fact)
                                is RemoveMark -> actionEvaluator.evaluate(action, fact)
                                is RemoveAllMarks -> actionEvaluator.evaluate(action, fact)
                                else -> error("$action is not supported for $item")
                            }
                            result.onSome {
                                facts += it
                                defaultBehavior = false
                            }
                        }
                    }
                }

                // Handle Cleaner config items:
                for (item in config.filterIsInstance<TaintCleaner>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        for (action in item.actionsAfter) {
                            val result = when (action) {
                                is RemoveMark -> actionEvaluator.evaluate(action, fact)
                                is RemoveAllMarks -> actionEvaluator.evaluate(action, fact)
                                else -> error("$action is not supported for $item")
                            }
                            result.onSome {
                                facts += it
                                defaultBehavior = false
                            }
                        }
                    }
                }

                if (!defaultBehavior) {
                    if (facts.size > 0) {
                        logger.trace { "Got ${facts.size} facts from config for $callee: $facts" }
                    }
                    return facts.map { CallAction.Return(it) }
                } else {
                    // Fall back to the default behavior, as if there were no config at all.
                }
            }
        }

        if (fact.variable.isStatic) {
            return listOf(CallAction.Start(fact))
        }

        for (actual in callExpr.args) {
            // Possibly tainted actual parameter:
            if (fact.variable.startsWith(actual.toPathOrNull())) {
                return listOf(CallAction.Start(fact)) // Will be handled by summary edge
            }
        }

        if (callExpr is JcInstanceCallExpr) {
            // Possibly tainted instance:
            if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                return listOf(CallAction.Start(fact)) // Will be handled by summary edge
            }
        }

        if (callStatement is JcAssignInst) {
            // Possibly tainted lhv:
            if (fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return listOf(CallAction.Start(fact)) // Overridden by rhv
            }
        }

        // The "most default" behaviour is encapsulated here:
        return transmitTaintNormal(fact, callStatement).map { CallAction.Return(it) }
    }

    override fun callToStart(
        callStatement: JcInst,
        calleeStart: JcInst,
        fact: TaintDomainFact,
    ): Collection<TaintDomainFact> {
        val callee = calleeStart.location.method

        if (fact == TaintZeroFact) {
            return obtainPossibleStartFactsBasic(callee)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        return buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            val actualParams = callExpr.args
            val formalParams = cp.getArgumentsOf(callee)
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(
                    transmitTaintArgumentActualToFormal(
                        fact = fact,
                        at = callStatement,
                        from = actual,
                        to = formal
                    )
                )
            }

            // Transmit facts on instance (from 'instance' to 'this'):
            if (callExpr is JcInstanceCallExpr) {
                addAll(
                    transmitTaintInstanceToThis(
                        fact = fact,
                        at = callStatement,
                        from = callExpr.instance,
                        to = callee.thisInstance
                    )
                )
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }
        }
    }

    override fun exitToReturnSite(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
        fact: TaintDomainFact,
    ): Collection<TaintDomainFact> {
        // TODO: do we even need to return non-empty list for zero fact here?
        if (fact == TaintZeroFact) {
            // returns listOf(Zero)
            return buildSet {
                add(TaintZeroFact)
                if (exitStatement is JcReturnInst && callStatement is JcAssignInst) {
                    // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                    exitStatement.returnValue?.let { returnValue ->
                        if (returnValue is JcNullConstant) {
                            val toPath = callStatement.lhv.toPath()
                            add(Tainted(toPath, TaintMark.NULLNESS))
                        }
                    }
                }
            }
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = exitStatement.location.method

        return buildSet {
            // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
            if (fact.variable.isOnHeap) {
                val actualParams = callExpr.args
                val formalParams = cp.getArgumentsOf(callee)
                for ((formal, actual) in formalParams.zip(actualParams)) {
                    addAll(
                        transmitTaintArgumentFormalToActual(
                            fact = fact,
                            at = callStatement,
                            from = formal,
                            to = actual
                        )
                    )
                }
            }

            // Transmit facts on instance (from 'this' to 'instance'):
            if (callExpr is JcInstanceCallExpr) {
                addAll(
                    transmitTaintThisToInstance(
                        fact = fact,
                        at = callStatement,
                        from = callee.thisInstance,
                        to = callExpr.instance
                    )
                )
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (exitStatement is JcReturnInst && callStatement is JcAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                exitStatement.returnValue?.let { returnValue ->
                    addAll(
                        transmitTaintReturn(
                            fact = fact,
                            at = callStatement,
                            from = returnValue,
                            to = callStatement.lhv
                        )
                    )
                }
            }
        }
    }
}

// TODO: class BackwardNpeFlowFunctions
