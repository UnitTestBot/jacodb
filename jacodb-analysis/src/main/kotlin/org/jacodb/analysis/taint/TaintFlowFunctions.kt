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

package org.jacodb.analysis.taint

import org.jacodb.analysis.config.BasicConditionEvaluator
import org.jacodb.analysis.config.CallPositionToAccessPathResolver
import org.jacodb.analysis.config.CallPositionToValueResolver
import org.jacodb.analysis.config.EntryPointPositionToAccessPathResolver
import org.jacodb.analysis.config.EntryPointPositionToValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.config.TaintActionEvaluator
import org.jacodb.analysis.ifds.ElementAccessor
import org.jacodb.analysis.ifds.FlowFunction
import org.jacodb.analysis.ifds.FlowFunctions
import org.jacodb.analysis.ifds.isOnHeap
import org.jacodb.analysis.ifds.isStatic
import org.jacodb.analysis.ifds.minus
import org.jacodb.analysis.ifds.onSome
import org.jacodb.analysis.util.Traits
import org.jacodb.analysis.util.startsWith
import org.jacodb.api.common.CommonMethod
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.common.cfg.CommonAssignInst
import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.common.cfg.CommonInstanceCallExpr
import org.jacodb.api.common.cfg.CommonReturnInst
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.api.common.cfg.CommonValue
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.analysis.JcApplicationGraph
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBinaryExpr
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcDynamicCallExpr
import org.jacodb.api.jvm.cfg.JcNegExpr
import org.jacodb.ets.base.EtsBinaryExpr
import org.jacodb.ets.base.EtsCastExpr
import org.jacodb.ets.base.EtsPhiExpr
import org.jacodb.ets.base.EtsUnaryExpr
import org.jacodb.ets.utils.getOperands
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.RemoveAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.TaintCleaner
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintConfigurationItem
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.jacodb.panda.staticvm.cfg.PandaBinaryExpr as StaticPandaBinaryExpr
import org.jacodb.panda.staticvm.cfg.PandaCastExpr as StaticPandaCastExpr
import org.jacodb.panda.staticvm.cfg.PandaNegExpr as StaticPandaNegExpr
import org.jacodb.panda.staticvm.cfg.PandaNotExpr as StaticPandaNotExpr
import org.jacodb.panda.staticvm.cfg.PandaPhiExpr as StaticPandaPhiExpr
import org.jacodb.panda.staticvm.cfg.PandaPhiInst as StaticPandaPhiInst

private val logger = mu.KotlinLogging.logger {}

// TODO: replace <Method> with CommonMethod, <Statement> with CommonInst
context(Traits<Method, Statement>)
class ForwardTaintFlowFunctions<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
    val getConfigForMethod: ForwardTaintFlowFunctions<Method, Statement>.(Method) -> List<TaintConfigurationItem>? = { method ->
        taintConfigurationFeature?.let { feature ->
            if (method is JcMethod) {
                logger.trace { "Extracting config for $method" }
                feature.getConfigForMethod(method)
            } else {
                error("Cannot extract config for $method")
            }
        }
    },
) : FlowFunctions<TaintDomainFact, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    // // TODO: inline
    // private fun CommonExpr.toPathOrNull(): AccessPath? = traits.toPathOrNull(this)
    // private fun CommonValue.toPath(): AccessPath = traits.toPath(this)

    private val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        if (graph is JcApplicationGraph) {
            graph.cp.features
                ?.singleOrNull { it is TaintConfigurationFeature }
                ?.let { it as TaintConfigurationFeature }
        } else {
            null
        }
    }

    override fun obtainPossibleStartFacts(
        method: Method,
    ): Collection<TaintDomainFact> = buildSet {
        // Zero (reachability) fact always present at entrypoint:
        add(TaintZeroFact)

        // Extract initial facts from the config:
        val config = getConfigForMethod(method)
        if (config != null) {
            val conditionEvaluator = BasicConditionEvaluator(
                EntryPointPositionToValueResolver(method)
            )
            val actionEvaluator = TaintActionEvaluator(
                EntryPointPositionToAccessPathResolver(method)
            )

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
        from: CommonExpr,
        to: CommonValue,
    ): Collection<Tainted> {
        val toPath = to.toPath()
        val fromPath = from.toPathOrNull()

        if (fromPath != null) {
            // Adhoc taint array:
            if (fromPath.accesses.isNotEmpty()
                && fromPath.accesses.last() is ElementAccessor
                && fromPath == (fact.variable + ElementAccessor)
            ) {
                val newTaint = fact.copy(variable = toPath)
                return setOf(fact, newTaint)
            }

            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath + tail
                val newTaint = fact.copy(variable = newPath)
                return setOf(fact, newTaint)
            }
        }

        if (fact.variable.startsWith(toPath)) {
            // 'to' was (sub-)tainted, but it is now overridden by 'from':
            return emptySet()
        } else {
            // Neither 'from' nor 'to' are tainted:
            return setOf(fact)
        }
    }

    private fun transmitTaintNormal(
        fact: Tainted,
        inst: Statement,
    ): List<Tainted> {
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: Statement,
        next: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact is TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        if (current is CommonAssignInst) {
            when (val rhv = current.rhv) {
                is StaticPandaPhiExpr -> {
                    val facts: MutableSet<TaintDomainFact> = mutableSetOf()
                    for (input in rhv.inputs) {
                        facts += transmitTaintAssign(fact, from = input, to = current.lhv)
                    }
                    // For empty phi, pass-through:
                    val pass = transmitTaintNormal(fact, current)
                    facts + pass
                }

                is EtsPhiExpr -> {
                    val facts: MutableSet<TaintDomainFact> = mutableSetOf()
                    for (input in rhv.getOperands()) {
                        facts += transmitTaintAssign(fact, from = input, to = current.lhv)
                    }
                    // For empty phi, pass-through:
                    val pass = transmitTaintNormal(fact, current)
                    facts + pass
                }

                is JcBinaryExpr -> {
                    val facts: MutableSet<TaintDomainFact> = mutableSetOf()
                    facts += transmitTaintAssign(fact, from = rhv.lhv, to = current.lhv)
                    facts += transmitTaintAssign(fact, from = rhv.rhv, to = current.lhv)
                    facts
                }

                is JcNegExpr -> {
                    transmitTaintAssign(fact, from = rhv.operand, to = current.lhv)
                }

                is JcCastExpr -> {
                    transmitTaintAssign(fact, from = rhv.operand, to = current.lhv)
                }

                is StaticPandaBinaryExpr -> {
                    val facts: MutableSet<TaintDomainFact> = mutableSetOf()
                    facts += transmitTaintAssign(fact, from = rhv.lhv, to = current.lhv)
                    facts += transmitTaintAssign(fact, from = rhv.rhv, to = current.lhv)
                    facts
                }

                is EtsBinaryExpr -> {
                    val facts: MutableSet<TaintDomainFact> = mutableSetOf()
                    facts += transmitTaintAssign(fact, from = rhv.left, to = current.lhv)
                    facts += transmitTaintAssign(fact, from = rhv.right, to = current.lhv)
                    facts
                }

                is StaticPandaNegExpr -> {
                    transmitTaintAssign(fact, from = rhv.arg, to = current.lhv)
                }

                is EtsUnaryExpr -> {
                    transmitTaintAssign(fact, from = rhv.getOperands().first(), to = current.lhv)
                }

                is StaticPandaNotExpr -> {
                    transmitTaintAssign(fact, from = rhv.arg, to = current.lhv)
                }

                is StaticPandaCastExpr -> {
                    transmitTaintAssign(fact, from = rhv.arg, to = current.lhv)
                }

                is EtsCastExpr -> {
                    transmitTaintAssign(fact, from = rhv.getOperands().first(), to = current.lhv)
                }

                else -> {
                    transmitTaintAssign(fact, from = current.rhv, to = current.lhv)
                }
            }
        } else if (current is StaticPandaPhiInst) {
            val facts: MutableSet<TaintDomainFact> = mutableSetOf()
            for (input in current.inputs) {
                facts += transmitTaintAssign(fact, from = input, to = current.lhv)
            }
            // For empty phi, pass-through:
            val pass = transmitTaintNormal(fact, current)
            facts + pass
        } else {
            transmitTaintNormal(fact, current)
        }
    }

    private fun transmitTaint(
        fact: Tainted,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath + tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: CommonValue, // actual
        to: CommonValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: CommonValue, // formal
        to: CommonValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: CommonValue, // instance
        to: CommonThis, // this
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: CommonThis, // this
        to: CommonValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement, // FIXME: unused?
    ) = FlowFunction<TaintDomainFact> { fact ->
        val callExpr = callStatement.getCallExpr()
            ?: error("Call statement should have non-null callExpr")

        val callee = callExpr.callee

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        if (fact is Tainted
            && callExpr is JcDynamicCallExpr
            && (callee as JcMethod).enclosingClass.name == "java.lang.invoke.StringConcatFactory"
            && callStatement is JcAssignInst
        ) {
            for (arg in callExpr.args) {
                if (arg.toPath() == fact.variable) {
                    return@FlowFunction setOf(
                        fact,
                        fact.copy(variable = callStatement.lhv.toPath())
                    )
                }
            }
            return@FlowFunction setOf(fact)
        }

        val config = getConfigForMethod(callee)

        if (fact == TaintZeroFact) {
            return@FlowFunction buildSet {
                add(TaintZeroFact)

                if (config != null) {
                    val conditionEvaluator = BasicConditionEvaluator(
                        CallPositionToValueResolver(callStatement)
                    )
                    val actionEvaluator = TaintActionEvaluator(
                        CallPositionToAccessPathResolver(callStatement)
                    )

                    // Handle MethodSource config items:
                    for (item in config.filterIsInstance<TaintMethodSource>()) {
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
        }
        check(fact is Tainted)

        if (config != null) {
            val facts = mutableSetOf<Tainted>()
            val conditionEvaluator = FactAwareConditionEvaluator(
                fact, CallPositionToValueResolver(callStatement)
            )
            val actionEvaluator = TaintActionEvaluator(
                CallPositionToAccessPathResolver(callStatement)
            )
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
                return@FlowFunction facts
            } else {
                // Fall back to the default behavior, as if there were no config at all.
            }
        }

        // FIXME: adhoc for constructors:
        if (callee.isConstructor) {
            return@FlowFunction listOf(fact)
        }

        // TODO: CONSIDER REFACTORING THIS
        //   Default behavior for "analyzable" method calls is to remove ("temporarily")
        //    all the marks from the 'instance' and arguments, in order to allow them "pass through"
        //    the callee (when it is going to be analyzed), i.e. through "call-to-start" and
        //    "exit-to-return" flow functions.
        //   When we know that we are NOT going to analyze the callee, we do NOT need
        //    to remove any marks from 'instance' and arguments.
        //   Currently, "analyzability" of the callee depends on the fact that the callee
        //    is "accessible" through the JcApplicationGraph::callees().
        if (callee in graph.callees(callStatement)) {

            if (fact.variable.isStatic) {
                return@FlowFunction emptyList()
            }

            for (actual in callExpr.args) {
                // Possibly tainted actual parameter:
                if (fact.variable.startsWith(actual.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is CommonInstanceCallExpr) {
                // Possibly tainted instance:
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

        }

        if (callStatement is CommonAssignInst) {
            // Possibly tainted lhv:
            if (fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return@FlowFunction emptyList() // Overridden by rhv
            }
        }

        // The "most default" behaviour is encapsulated here:
        transmitTaintNormal(fact, callStatement)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        calleeStart: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        val callee = graph.methodOf(calleeStart)

        if (fact == TaintZeroFact) {
            return@FlowFunction obtainPossibleStartFacts(callee)
        }
        check(fact is Tainted)

        val callExpr = callStatement.getCallExpr()
            ?: error("Call statement should have non-null callExpr")

        buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            val actualParams = callExpr.args
            val formalParams = getArgumentsOf(callee)
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(transmitTaintArgumentActualToFormal(fact, from = actual, to = formal))
            }

            // Transmit facts on instance (from 'instance' to 'this'):
            if (callExpr is CommonInstanceCallExpr) {
                addAll(
                    transmitTaintInstanceToThis(
                        fact = fact,
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

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement, // unused
        exitStatement: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact == TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        val callExpr = callStatement.getCallExpr()
            ?: error("Call statement should have non-null callExpr")
        val callee = graph.methodOf(exitStatement)

        buildSet {
            // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
            if (fact.variable.isOnHeap) {
                val actualParams = callExpr.args
                val formalParams = getArgumentsOf(callee)
                for ((formal, actual) in formalParams.zip(actualParams)) {
                    addAll(
                        transmitTaintArgumentFormalToActual(
                            fact = fact,
                            from = formal,
                            to = actual
                        )
                    )
                }
            }

            // Transmit facts on instance (from 'this' to 'instance'):
            if (callExpr is CommonInstanceCallExpr) {
                addAll(
                    transmitTaintThisToInstance(
                        fact = fact,
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
            if (exitStatement is CommonReturnInst && callStatement is CommonAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                exitStatement.returnValue?.let { returnValue ->
                    addAll(transmitTaintReturn(fact, from = returnValue, to = callStatement.lhv))
                }
            }
        }
    }
}

context(Traits<Method, Statement>)
class BackwardTaintFlowFunctions<Method, Statement>(
    private val graph: ApplicationGraph<Method, Statement>,
) : FlowFunctions<TaintDomainFact, Method, Statement>
    where Method : CommonMethod,
          Statement : CommonInst {

    override fun obtainPossibleStartFacts(
        method: Method,
    ): Collection<TaintDomainFact> {
        return listOf(TaintZeroFact)
    }

    private fun transmitTaintBackwardAssign(
        fact: Tainted,
        from: CommonValue,
        to: CommonExpr,
    ): Collection<TaintDomainFact> {
        val fromPath = from.toPath()
        val toPath = to.toPathOrNull()

        if (toPath != null) {
            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath + tail
                val newTaint = fact.copy(variable = newPath)
                return setOf(fact, newTaint)
            }

            if (fact.variable.startsWith(toPath)) {
                // 'to' was (sub-)tainted, but it is now overridden by 'from':
                return emptySet()
            }
        }

        // Pass-through:
        return setOf(fact)
    }

    private fun transmitTaintBackwardNormal(
        fact: Tainted,
        inst: Statement,
    ): List<TaintDomainFact> {
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: Statement,
        next: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact is TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        if (current is CommonAssignInst) {
            transmitTaintBackwardAssign(fact, from = current.lhv, to = current.rhv)
        } else {
            transmitTaintBackwardNormal(fact, current)
        }
    }

    private fun transmitTaint(
        fact: Tainted,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath + tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: CommonValue, // actual
        to: CommonValue, // formal
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: CommonValue, // formal
        to: CommonValue, // actual
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: CommonValue, // instance
        to: CommonThis, // this
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: CommonThis, // this
        to: CommonValue, // instance
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    private fun transmitTaintReturn(
        fact: Tainted,
        from: CommonValue,
        to: CommonValue,
    ): Collection<Tainted> = transmitTaint(fact, from, to)

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement, // FIXME: unused?
    ) = FlowFunction<TaintDomainFact> { fact ->
        // TODO: pass-through on invokedynamic-based String concatenation

        if (fact == TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        val callExpr = callStatement.getCallExpr()
            ?: error("Call statement should have non-null callExpr")
        val callee = callExpr.callee

        if (callee in graph.callees(callStatement)) {

            if (fact.variable.isStatic) {
                return@FlowFunction emptyList()
            }

            for (actual in callExpr.args) {
                // Possibly tainted actual parameter:
                if (fact.variable.startsWith(actual.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

            if (callExpr is CommonInstanceCallExpr) {
                // Possibly tainted instance:
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

        }

        if (callStatement is CommonAssignInst) {
            // Possibly tainted rhv:
            if (fact.variable.startsWith(callStatement.rhv.toPathOrNull())) {
                return@FlowFunction emptyList() // Overridden by lhv
            }
        }

        // The "most default" behaviour is encapsulated here:
        transmitTaintBackwardNormal(fact, callStatement)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: Statement,
        calleeStart: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        val callee = graph.methodOf(calleeStart)

        if (fact == TaintZeroFact) {
            return@FlowFunction obtainPossibleStartFacts(callee)
        }
        check(fact is Tainted)

        val callExpr = callStatement.getCallExpr()
            ?: error("Call statement should have non-null callExpr")

        buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            val actualParams = callExpr.args
            val formalParams = getArgumentsOf(callee)
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(transmitTaintArgumentActualToFormal(fact, from = actual, to = formal))
            }

            // Transmit facts on instance (from 'instance' to 'this'):
            if (callExpr is CommonInstanceCallExpr) {
                addAll(
                    transmitTaintInstanceToThis(
                        fact = fact,
                        from = callExpr.instance,
                        to = callee.thisInstance
                    )
                )
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (calleeStart is CommonReturnInst && callStatement is CommonAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                calleeStart.returnValue?.let { returnValue ->
                    addAll(
                        transmitTaintReturn(
                            fact = fact,
                            from = callStatement.lhv,
                            to = returnValue
                        )
                    )
                }
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: Statement,
        returnSite: Statement,
        exitStatement: Statement,
    ) = FlowFunction<TaintDomainFact> { fact ->
        if (fact == TaintZeroFact) {
            return@FlowFunction listOf(TaintZeroFact)
        }
        check(fact is Tainted)

        val callExpr = callStatement.getCallExpr()
            ?: error("Call statement should have non-null callExpr")
        val callee = graph.methodOf(exitStatement)

        buildSet {
            // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
            if (fact.variable.isOnHeap) {
                val actualParams = callExpr.args
                val formalParams = getArgumentsOf(callee)
                for ((formal, actual) in formalParams.zip(actualParams)) {
                    addAll(
                        transmitTaintArgumentFormalToActual(
                            fact = fact,
                            from = formal,
                            to = actual
                        )
                    )
                }
            }

            // Transmit facts on instance (from 'this' to 'instance'):
            if (callExpr is CommonInstanceCallExpr) {
                addAll(
                    transmitTaintThisToInstance(
                        fact = fact,
                        from = callee.thisInstance,
                        to = callExpr.instance
                    )
                )
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }
        }
    }
}
