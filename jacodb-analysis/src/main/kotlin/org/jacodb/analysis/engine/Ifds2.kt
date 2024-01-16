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

@file:Suppress("LiftReturnOrAssignment")

package org.jacodb.analysis.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jacodb.analysis.config.BasicConditionEvaluator
import org.jacodb.analysis.config.CallPositionToAccessPathResolver
import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.config.TaintActionEvaluator
import org.jacodb.analysis.library.analyzers.NpeTaintNode
import org.jacodb.analysis.library.analyzers.TaintAnalysisNode
import org.jacodb.analysis.library.analyzers.TaintNode
import org.jacodb.analysis.library.analyzers.getFormalParamsOf
import org.jacodb.analysis.library.analyzers.thisInstance
import org.jacodb.analysis.paths.AccessPath
import org.jacodb.analysis.paths.minus
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.taint.configuration.AnyArgument
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.CopyAllMarks
import org.jacodb.taint.configuration.CopyMark
import org.jacodb.taint.configuration.RemoveAllMarks
import org.jacodb.taint.configuration.RemoveMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.ResultAnyElement
import org.jacodb.taint.configuration.TaintCleaner
import org.jacodb.taint.configuration.TaintConfigurationFeature
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMark
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.jacodb.taint.configuration.This
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

interface Fact

object Zero : Fact {
    override fun toString(): String = "[ZERO]"
}

data class Tainted(
    val variable: AccessPath,
    val mark: TaintMark,
) : Fact {
    constructor(fact: TaintNode) : this(fact.variable, TaintMark(fact.nodeType))
}

fun DomainFact.toFact(): Fact = when (this) {
    ZEROFact -> Zero
    is TaintNode -> Tainted(this)
    else -> object : Fact {}
}

fun Fact.toDomainFact(): DomainFact = when (this) {
    Zero -> ZEROFact

    is Tainted -> {
        when (mark.name) {
            "NPE" -> NpeTaintNode(variable)
            else -> TaintAnalysisNode(variable, nodeType = mark.name)
        }
    }

    else -> object : DomainFact {}
}

data class Vertex(
    val statement: JcInst,
    val fact: Fact,
) {
    val method: JcMethod get() = statement.location.method

    constructor(vertex: IfdsVertex) : this(vertex.statement, vertex.domainFact.toFact())
}

fun Vertex.toIfds(): IfdsVertex = IfdsVertex(statement, fact.toDomainFact())

data class Edge(
    val from: Vertex,
    val to: Vertex,
) {
    init {
        require(from.method == to.method)
    }

    var reason: Edge? = null

    val method: JcMethod get() = from.method

    constructor(edge: IfdsEdge) : this(Vertex(edge.from), Vertex(edge.to))
}

fun Edge.toIfds(): IfdsEdge = IfdsEdge(from.toIfds(), to.toIfds())

/**
 * Represents a path edge which starts in an entrypoint
 * and ends in an exit-point of a method.
 */
data class SummaryEdge(
    val edge: Edge,
) : SummaryFact {
    override val method: JcMethod get() = edge.method
}

data class Vulnerability(
    val message: String,
    val sink: Vertex,
    val edge: Edge? = null,
    val rule: TaintMethodSink? = null,
) : SummaryFact {
    override val method: JcMethod get() = sink.method
}

fun interface FlowFunction {
    fun compute(fact: Fact): Collection<Fact>
}

interface FlowFunctionsSpace2 {
    /**
     * Method for obtaining initial domain facts at the method entrypoint.
     * Commonly, it is only `listOf(Zero)`.
     */
    fun obtainPossibleStartFacts(method: JcMethod): Collection<Fact>

    /**
     * Sequent flow function.
     *
     * ```
     *   [ DO() ] :: current
     *     |
     *     | (sequent edge)
     *     |
     *   [ DO() ]
     * ```
     */
    fun obtainSequentFlowFunction(
        current: JcInst,
    ): FlowFunction

    /**
     * Call-to-return-site flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   :
     *   : (call-to-return-site edge)
     *   :
     * [ RETURN FROM p ] :: returnSite
     * ```
     */
    fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
    ): FlowFunction

    /**
     * Call-to-start flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   : \
     *   :  \ (call-to-start edge)
     *   :   \
     *   :  [ START p ]
     *   :    |
     *   :  [ EXIT p ]
     *   :   /
     *   :  /
     * [ RETURN FROM p ]
     * ```
     */
    fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod,
    ): FlowFunction

    /**
     * Exit-to-return-site flow function.
     *
     * ```
     * [ CALL p ] :: callStatement
     *   :  \
     *   :   \
     *   :  [ START p ]
     *   :    |
     *   :  [ EXIT p ] :: exitStatement
     *   :   /
     *   :  / (exit-to-return-site edge)
     *   : /
     * [ RETURN FROM p ] :: returnSite
     * ```
     */
    fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
    ): FlowFunction
}

@Suppress("PublicApiImplicitType")
class TaintForwardFlowFunctions(
    private val cp: JcClasspath,
    private val graph: JcApplicationGraph,
) : FlowFunctionsSpace2 {

    internal val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    // TODO: consider Set<Fact> or Collection<Fact>
    override fun obtainPossibleStartFacts(method: JcMethod): Collection<Fact> = buildList {
        // Zero (reachability) fact always present at entrypoint:
        add(Zero)

        // Extract initial facts from the config:
        val config = taintConfigurationFeature?.let { feature ->
            // logger.debug { "Extracting config for $method" }
            feature.getConfigForMethod(method)
        }
        if (config != null) {
            // Note: both condition and action evaluator require a custom position resolver.
            val conditionEvaluator = BasicConditionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance

                    is Argument -> run {
                        val p = method.parameters[position.index]
                        cp.findTypeOrNull(p.type)?.let { t ->
                            JcArgument.of(p.index, p.name, t)
                        }
                    } ?: error("Cannot resolve $position for $method")

                    AnyArgument -> error("Unexpected $position")
                    Result -> error("Unexpected $position")
                    ResultAnyElement -> error("Unexpected $position")
                }
            }
            val actionEvaluator = TaintActionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance.toPathOrNull()
                        ?: error("Cannot resolve $position for $method")

                    is Argument -> run {
                        val p = method.parameters[position.index]
                        cp.findTypeOrNull(p.type)?.let { t ->
                            JcArgument.of(p.index, p.name, t).toPathOrNull()
                        }
                    } ?: error("Cannot resolve $position for $method")

                    AnyArgument -> error("Unexpected $position")
                    Result -> error("Unexpected $position")
                    ResultAnyElement -> error("Unexpected $position")
                }
            }

            // Handle EntryPointSource config items:
            for (item in config.filterIsInstance<TaintEntryPointSource>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        when (action) {
                            is AssignMark -> {
                                add(actionEvaluator.evaluate(action))
                            }

                            else -> error("$action is not supported for $item")
                        }
                    }
                }
            }
        }
    }

    // TODO: impl
    private fun copyTaint(
        fact: Tainted,
        from: JcExpr,
        to: JcValue,
    ): List<Fact> {
        TODO()
    }

    // TODO: impl
    private fun moveTaint(
        fact: Tainted,
        from: JcExpr,
        to: JcValue,
    ): List<Fact> {
        TODO()
    }

    private fun transmitTaintAssign(
        fact: Tainted,
        from: JcExpr,
        to: JcValue,
    ): Collection<Fact> {
        val toPath = to.toPath()
        val fromPath = from.toPathOrNull()

        if (fromPath != null) {
            if (fromPath == fact.variable) {
                // Both 'from' and 'to' are tainted now:
                val newTaint = fact.copy(variable = toPath)
                return setOf(fact, newTaint)
            } else {
                val tail = fact.variable - fromPath
                if (tail != null) {
                    // Both 'from' and 'to' are tainted now:
                    val newPath = toPath / tail
                    val newTaint = fact.copy(variable = newPath)
                    return setOf(fact, newTaint)
                }
            }
        }

        if (toPath == fact.variable) {
            // 'to' was tainted, but it is now overridden by 'from':
            return emptySet()
        } else {
            // Neither 'from' nor 'to' are tainted, simply pass-through:
            return setOf(fact)
        }
    }

    private fun transmitTaintReturn(
        fact: Tainted,
        from: JcValue,
        to: JcValue,
    ): Collection<Fact> {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        if (fromPath == fact.variable) {
            // 'to' is tainted now:
            val newTaint = fact.copy(variable = toPath)
            return setOf(newTaint)
        } else {
            val tail = (fact.variable - fromPath) ?: return emptySet()
            val newPath = toPath / tail
            val newTaint = fact.copy(variable = newPath)
            return setOf(newTaint)
        }
    }

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: JcValue, // instance
        to: JcThis, // this
    ): Collection<Fact> {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        if (fromPath == fact.variable) {
            // 'to' is tainted now:
            val newTaint = fact.copy(variable = toPath)
            return setOf(newTaint)
        } else {
            // TODO: check
            val tail = (fact.variable - fromPath) ?: return emptySet()
            val newPath = toPath / tail
            val newTaint = fact.copy(variable = newPath)
            return setOf(newTaint)
        }
    }

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: JcThis, // this
        to: JcValue, // instance
    ): Collection<Fact> {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        if (fromPath == fact.variable) {
            // 'to' is tainted now:
            val newTaint = fact.copy(variable = toPath)
            return setOf(newTaint)
        } else {
            // TODO: check
            val tail = (fact.variable - fromPath) ?: return emptySet()
            val newPath = toPath / tail
            val newTaint = fact.copy(variable = newPath)
            return setOf(newTaint)
        }
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: JcValue, // actual
        to: JcArgument, // formal
    ): Collection<Fact> {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        if (fromPath == fact.variable) {
            // 'to' is tainted now:
            val newTaint = fact.copy(variable = toPath)
            return setOf(newTaint)
        } else {
            // TODO: check
            val tail = (fact.variable - fromPath) ?: return emptySet()
            val newPath = toPath / tail
            val newTaint = fact.copy(variable = newPath)
            return setOf(newTaint)
        }
    }

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: JcArgument, // formal
        to: JcValue, // actual
    ): Collection<Fact> {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        if (fromPath == fact.variable) {
            // 'to' is tainted now:
            val newTaint = fact.copy(variable = toPath)
            return setOf(newTaint)
        } else {
            // TODO: check
            val tail = (fact.variable - fromPath) ?: return emptySet()
            val newPath = toPath / tail
            val newTaint = fact.copy(variable = newPath)
            return setOf(newTaint)
        }
    }

    // TODO: rename / refactor
    // TODO: consider splitting into transmitTaintAssign / transmitTaintArgument
    // TODO: consider: moveTaint/copyTaint
    private fun transmitTaint(
        fact: Tainted,
        from: JcExpr,
        to: JcValue,
    ): Collection<Fact> {
        val fromPath = from.toPathOrNull()
        val toPath = to.toPath()

        if (fromPath != null && fromPath == fact.variable) {
            // Both 'from' and 'to' are now tainted with 'fact':
            val newTaint = fact.copy(variable = toPath)
            return setOf(fact, newTaint)
        }

        if (toPath == fact.variable) {
            // 'to' is tainted, but overridden by 'from':
            return emptySet()
        } else {
            // Neither 'from' nor 'to' is tainted with 'fact', simply pass-through:
            return setOf(fact)
        }

        // if (fromPath != null) {
        //     if (fromPath == fact.variable) {
        //         // Both 'from' and 'to' are now tainted with 'fact':
        //         val newTaint = fact.copy(variable = toPath)
        //         return setOf(fact, newTaint)
        //     } else {
        //         if (toPath == fact.variable) {
        //             // 'to' is tainted, but overridden by 'from':
        //             return emptySet()
        //         } else {
        //             // Neither 'from' nor 'to' is tainted with 'fact', simply pass-through:
        //             return setOf(fact)
        //         }
        //     }
        // } else {
        //     if (toPath == fact.variable) {
        //         // 'to' is tainted with 'fact', but overridden by 'from':
        //         return emptySet()
        //     } else {
        //         // Neither 'from' nor 'to' is tainted with 'fact', simply pass-through:
        //         return setOf(fact)
        //     }
        // }

        // // // 'from' is tainted with 'fact':
        // // // TODO: replace with ==, in general case
        // // if (fromPath.startsWith(fact.variable)) {
        // //     val newTaint = fact.copy(variable = toPath)
        // //     // Both 'from' and 'to' are now tainted:
        // //     return listOf(fact, newTaint)
        // // }
        //
        // // // TODO: check
        // // // Some sub-path in 'to' is tainted with 'fact':
        // // if (fact.variable.startsWith(toPath)) {
        // //     // Drop 'fact' taint:
        // //     return emptyList()
        // // }
        //
        // // // TODO: check
        // // // 'to' is tainted (strictly) with 'fact':
        // // // Note: "non-strict" case is handled above.
        // // if (toPath.startsWith(fact.variable)) {
        // //     // No drop:
        // //     return listOf(fact)
        // // }
        //
        // // Neither 'from' nor 'to' is tainted with 'fact', simply pass-through:
        // return listOf(fact)
    }

    // TODO: rename (consider "propagate")
    // TODO: refactor (consider adding 'transmitTaintSequent' / 'transmitTaintCall')
    private fun transmitTaintNormal(
        fact: Tainted,
        inst: JcInst,
    ): List<Fact> {
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: JcInst,
    ) = FlowFunction { fact ->
        if (fact == Zero) {
            // FIXME: calling 'generates' here is not correct, since sequent flow function are NOT for calls,
            //        and 'generates' is only applicable for calls.
            return@FlowFunction listOf(Zero) // + generates(current)
        }

        if (fact !is Tainted) {
            return@FlowFunction emptyList()
        }

        if (current is JcAssignInst) {
            transmitTaintAssign(fact, from = current.rhv, to = current.lhv)
        } else {
            transmitTaintNormal(fact, current)
        }
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst, // FIXME: unused?
    ) = FlowFunction { fact ->
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val callee = callExpr.method.method

        // FIXME: adhoc for constructors:
        if (callee.isConstructor) {
            return@FlowFunction listOf(fact)
        }

        // FIXME: handle taint pass-through on invokedynamic-based String concatenation:
        if (fact is Tainted && callExpr is JcDynamicCallExpr && callee.enclosingClass.name == "java.lang.invoke.StringConcatFactory" && callStatement is JcAssignInst) {
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

        val config = taintConfigurationFeature?.let { feature ->
            // logger.debug { "Extracting config for $callee" }
            feature.getConfigForMethod(callee)
        }

        // If 'fact' is ZeroFact, handle MethodSource. If there are no suitable MethodSource items, perform default.
        // For other facts (Tainted only?), handle PassThrough/Cleaner items.
        // TODO: what to do with "other facts" on CopyAllMarks/RemoveAllMarks?

        // TODO: the call-to-return flow function should also return (or somehow mark internally)
        //  whether we need to analyze the callee. For example, when we have MethodSource,
        //  PassThrough or Cleaner for a call statement, we do not need to analyze the callee at all.
        //  However, when we do not have such items in our config, we have to perform the whole analysis
        //  of the callee: calling call-to-start flow function, launching the analysis of the callee,
        //  awaiting for summary edges, and finally executing the exit-to-return flow function.
        //  In such case, the call-to-return flow function should return empty list of facts,
        //  since they are going to be "handled by the summary edge".

        if (fact == Zero) {
            if (config != null) {
                val facts: MutableSet<Fact> = mutableSetOf()

                // Add Zero fact:
                facts += Zero

                val conditionEvaluator = BasicConditionEvaluator(CallPositionToJcValueResolver(callStatement))
                val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))

                // Handle MethodSource config items:
                for (item in config.filterIsInstance<TaintMethodSource>()) {
                    if (item.condition.accept(conditionEvaluator)) {
                        for (action in item.actionsAfter) {
                            when (action) {
                                is AssignMark -> {
                                    facts += actionEvaluator.evaluate(action)
                                }

                                else -> error("$action is not supported for $item")
                            }
                        }
                    }
                }

                return@FlowFunction facts
            } else {
                return@FlowFunction listOf(Zero)
            }
        }

        // FIXME: adhoc to satisfy types
        if (fact !is Tainted) {
            // TODO: what to return here?
            return@FlowFunction emptyList()
        }

        // TODO: handle 'activation' (c.f. Boomerang) here

        // if (config == null) {
        //     return@FlowFunction emptyList()
        // }

        if (config != null) {
            val facts = mutableSetOf<Tainted>()
            val conditionEvaluator = FactAwareConditionEvaluator(fact, CallPositionToJcValueResolver(callStatement))
            val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))
            var defaultBehavior = true

            // Handle PassThrough config items:
            for (item in config.filterIsInstance<TaintPassThrough>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    defaultBehavior = false
                    for (action in item.actionsAfter) {
                        when (action) {
                            is CopyMark -> {
                                facts += actionEvaluator.evaluate(action, fact)
                            }

                            is CopyAllMarks -> {
                                facts += actionEvaluator.evaluate(action, fact)
                            }

                            is RemoveMark -> {
                                facts += actionEvaluator.evaluate(action, fact)
                            }

                            is RemoveAllMarks -> {
                                facts += actionEvaluator.evaluate(action, fact)
                            }

                            else -> error("$action is not supported for $item")
                        }
                    }
                }
            }

            // Handle Cleaner config items:
            for (item in config.filterIsInstance<TaintCleaner>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    defaultBehavior = false
                    for (action in item.actionsAfter) {
                        when (action) {
                            is RemoveMark -> {
                                facts += actionEvaluator.evaluate(action, fact)
                            }

                            is RemoveAllMarks -> {
                                facts += actionEvaluator.evaluate(action, fact)
                            }

                            else -> error("$action is not supported for $item")
                        }
                    }
                }
            }

            if (!defaultBehavior) {
                if (facts.size > 0) {
                    logger.debug { "Got ${facts.size} facts from config for $callee: $facts" }
                }
                return@FlowFunction facts
            } else {
                // Fall back to the default behavior, as if there were no config at all.
            }
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

            if (callExpr is JcInstanceCallExpr) {
                // Possibly tainted instance:
                if (fact.variable.startsWith(callExpr.instance.toPathOrNull())) {
                    return@FlowFunction emptyList() // Will be handled by summary edge
                }
            }

        }

        if (callStatement is JcAssignInst) {
            // Possibly tainted lhv:
            if (fact.variable.startsWith(callStatement.lhv.toPathOrNull())) {
                return@FlowFunction emptyList() // Overridden by rhv
            }
        }

        // The "most default" behaviour is encapsulated here:
        transmitTaintNormal(fact, callStatement)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod,
    ) = FlowFunction { fact ->
        if (fact == Zero) {
            return@FlowFunction obtainPossibleStartFacts(callee)
        }

        if (fact !is Tainted) {
            // TODO: what to return here?
            return@FlowFunction emptyList()
        }

        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val formalParams = cp.getFormalParamsOf(callee)

        buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(transmitTaintArgumentActualToFormal(fact, from = actual, to = formal))
            }

            // Transmit facts on instance (from 'instance' to 'this'):
            if (callExpr is JcInstanceCallExpr) {
                addAll(transmitTaintInstanceToThis(fact, from = callExpr.instance, to = callee.thisInstance))
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
    ) = FlowFunction { fact ->
        // TODO: do we even need to return non-empty list for zero fact here?
        if (fact == Zero) {
            return@FlowFunction listOf(Zero)
        }

        if (fact !is Tainted) {
            // TODO: what to return here?
            return@FlowFunction emptyList()
        }

        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val actualParams = callExpr.args
        val callee = exitStatement.location.method
        val formalParams = cp.getFormalParamsOf(callee)

        buildSet {
            // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
            // TODO: "if passed by-ref" part is not implemented here yet
            for ((formal, actual) in formalParams.zip(actualParams)) {
                addAll(transmitTaintArgumentFormalToActual(fact, from = formal, to = actual))
            }

            // Transmit facts on instance (from 'this' to 'instance'):
            if (callExpr is JcInstanceCallExpr) {
                addAll(transmitTaintThisToInstance(fact, from = callee.thisInstance, to = callExpr.instance))
            }

            // Transmit facts on static values:
            if (fact.variable.isStatic) {
                add(fact)
            }

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (exitStatement is JcReturnInst && callStatement is JcAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                exitStatement.returnValue?.let { returnValue ->
                    addAll(transmitTaintReturn(fact, from = returnValue, to = callStatement.lhv))
                }
            }
        }
    }
}

class Manager(
    private val graph: JcApplicationGraph,
    private val unitResolver: UnitResolver,
) {

    private val methodsForUnit: MutableMap<UnitType, MutableSet<JcMethod>> = mutableMapOf()
    private val runnerForUnit: MutableMap<UnitType, IfdsRunner> = mutableMapOf()
    // private val aliveRunners: MutableMap<UnitType, IfdsRunner> = ConcurrentHashMap()

    private val summaryEdgesStorage = SummaryStorageImpl<SummaryEdge>()
    private val vulnerabilitiesStorage = SummaryStorageImpl<Vulnerability>()

    private fun newRunner(
        unit: UnitType,
        analyzer: Analyzer2,
    ): IfdsRunner {
        check(unit !in runnerForUnit) { "Runner for $unit already exists" }
        val runner = IfdsRunner(graph, analyzer, this@Manager, unitResolver, unit)
        runnerForUnit[unit] = runner
        return runner
    }

    private fun addStart(method: JcMethod) {
        logger.info { "Adding start method: $method" }
        val unit = unitResolver.resolve(method)
        methodsForUnit.getOrPut(unit) { mutableSetOf() }.add(method)
        // TODO: val isNew = (...).add(); if (isNew) { deps.forEach { addStart(it) } }
    }

    private val dispatcher = Executors.newFixedThreadPool(1).asCoroutineDispatcher()

    fun analyze(startMethods: List<JcMethod>): List<Vulnerability> = runBlocking(dispatcher) {
        // Add start methods:
        for (method in startMethods) {
            addStart(method)
        }

        // Determine all units:
        val allUnits = methodsForUnit.keys.toList()
        logger.info { "Starting analysis of ${methodsForUnit.values.sumOf { it.size }} methods in ${allUnits.size} units" }

        // Spawn runner jobs:
        val allJobs = allUnits.map { unit ->
            // Initialize the analyzer:
            val analyzer = TaintAnalyzer(graph)

            // Create the runner:
            val runner = newRunner(unit, analyzer)

            // Start the runner:
            launch {
                val methods = methodsForUnit[unit]!!.toList()
                runner.run(methods)
            }
        }

        // Await all runners:
        withTimeoutOrNull(5.seconds) {
            allJobs.joinAll()
        } ?: run {
            allJobs.forEach { it.cancel() }
            allJobs.joinAll()
        }
        logger.info { "All ${allJobs.size} jobs completed" }

        // Extract found vulnerabilities (sinks):
        val foundVulnerabilities = vulnerabilitiesStorage.knownMethods.flatMap { method ->
            vulnerabilitiesStorage.getCurrentFacts(method)
        }
        logger.info { "Total found ${foundVulnerabilities.size} vulnerabilities" }
        for (vulnerability in foundVulnerabilities) {
            logger.info { "$vulnerability in ${vulnerability.method}" }
        }
        logger.info { "Total sinks: ${foundVulnerabilities.size}" }

        foundVulnerabilities
    }

    suspend fun handleEvent(event: Event) {
        when (event) {
            is EdgeForOtherRunner -> {
                val method = event.edge.method
                val unit = unitResolver.resolve(method)
                val otherRunner = runnerForUnit[unit] ?: error("No runner for $unit")
                otherRunner.submitNewEdge(event.edge)
            }

            is SubscriptionForSummaryEdges2 -> {
                summaryEdgesStorage
                    .getFacts(event.method)
                    .map { it.edge }
                    .collect(event.collector)
            }

            is SubscriptionForSummaryEdges3 -> {
                summaryEdgesStorage
                    .getFacts(event.method)
                    .map { it.edge }
                    .collect(event.handler)
            }

            is NewSummaryEdge -> {
                summaryEdgesStorage.add(SummaryEdge(event.edge))
            }

            is NewVulnerability -> {
                vulnerabilitiesStorage.add(event.vulnerability)
            }
        }
    }
}

sealed interface Event

data class SubscriptionForSummaryEdges2(
    val method: JcMethod,
    val collector: FlowCollector<Edge>,
) : Event

data class SubscriptionForSummaryEdges3(
    val method: JcMethod,
    val handler: (Edge) -> Unit,
) : Event

data class NewSummaryEdge(
    val edge: Edge,
) : Event

// TODO: replace with 'BeginAnalysis(val statement: Vertex)', where 'statement' is
//       the first instruction of the analyzed method together with a fact.
data class EdgeForOtherRunner(
    val edge: Edge,
) : Event {
    init {
        check(edge.from == edge.to) { "Edge for another runner must be a loop" }
    }
}

data class NewVulnerability(
    val vulnerability: Vulnerability,
) : Event

interface Analyzer2 {
    val flowFunctions: FlowFunctionsSpace2

    fun isSkipped(method: JcMethod): Boolean = false

    fun handleNewEdge(edge: Edge): List<Event>
    fun handleCrossUnitCall(caller: Vertex, callee: Vertex): List<Event>
}

class TaintAnalyzer(
    private val graph: JcApplicationGraph,
) : Analyzer2 {
    override val flowFunctions: FlowFunctionsSpace2 by lazy {
        TaintForwardFlowFunctions(graph.classpath, graph)
    }

    private fun isExitPoint(statement: JcInst): Boolean {
        return statement in graph.exitPoints(statement.location.method)
    }

    private fun isSink(statement: JcInst, fact: Fact): Boolean {
        // TODO
        return false
    }

    override fun handleNewEdge(edge: Edge): List<Event> = buildList {
        if (isExitPoint(edge.to.statement)) {
            add(NewSummaryEdge(edge))
        }

        val configOk = run {
            val callExpr = edge.to.statement.callExpr ?: return@run false
            val callee = callExpr.method.method

            val config = (flowFunctions as TaintForwardFlowFunctions)
                .taintConfigurationFeature?.let { feature ->
                    // logger.debug { "Extracting config for $callee" }
                    feature.getConfigForMethod(callee)
                } ?: return@run false

            // TODO: not always we want to skip sinks on Zero facts.
            //  Some rules might have ConstantTrue or just true (when evaluated with Zero fact) condition.
            if (edge.to.fact !is Tainted) {
                return@run false
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                edge.to.fact,
                CallPositionToJcValueResolver(edge.to.statement),
            )
            var triggeredItem: TaintMethodSink? = null
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    triggeredItem = item
                    break
                }
                // FIXME: unconditionally let it be the sink.
                // triggeredItem = item
                // break
            }
            if (triggeredItem != null) {
                // logger.info { "Found sink at ${edge.to} in ${edge.method} on $triggeredItem" }
                val message = "SINK" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge, rule = triggeredItem)
                logger.info { "Found $vulnerability in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
                // verticesWithTraceGraphNeeded.add(edge.to)
            }
            true
        }

        if (!configOk) {
            // "config"-less behavior:
            if (isSink(edge.to.statement, edge.to.fact)) {
                // logger.info { "Found sink at ${edge.to} in ${edge.method}" }
                val message = "SINK" // TODO
                val vulnerability = Vulnerability(message, sink = edge.to, edge = edge)
                logger.info { "Found $vulnerability in ${vulnerability.method}" }
                add(NewVulnerability(vulnerability))
                // verticesWithTraceGraphNeeded.add(edge.to)
            }
        }
    }

    override fun handleCrossUnitCall(caller: Vertex, callee: Vertex): List<Event> = buildList {
        add(EdgeForOtherRunner(Edge(callee, callee)))
    }
}

class IfdsRunner(
    private val graph: JcApplicationGraph,
    private val analyzer: Analyzer2,
    private val manager: Manager,
    private val unitResolver: UnitResolver,
    private val unit: UnitType,
) {
    private val flowSpace: FlowFunctionsSpace2 = analyzer.flowFunctions
    private val workList: Channel<Edge> = Channel(Channel.UNLIMITED)
    private val pathEdges: MutableSet<Edge> = mutableSetOf() // TODO: replace with concurrent set
    private val summaryEdges: MutableMap<Vertex, MutableSet<Vertex>> = mutableMapOf()
    private val callSitesOf: MutableMap<Vertex, MutableSet<Edge>> = mutableMapOf()

    suspend fun run(startMethods: List<JcMethod>) {
        for (method in startMethods) {
            addStart(method)
        }

        tabulationAlgorithm()
    }

    // TODO: should 'addStart' be public?
    // TODO: should 'addStart' replace 'submitNewEdge'?
    private suspend fun addStart(method: JcMethod) {
        require(unitResolver.resolve(method) == unit)
        val startFacts = flowSpace.obtainPossibleStartFacts(method)
        for (startFact in startFacts) {
            for (start in graph.entryPoints(method)) {
                val vertex = Vertex(start, startFact)
                val edge = Edge(vertex, vertex) // loop
                propagate(edge)
            }
        }
    }

    suspend fun submitNewEdge(edge: Edge) {
        propagate(edge)
    }

    private suspend fun propagate(edge: Edge, reason: Edge? = null): Boolean {
        require(unitResolver.resolve(edge.method) == unit) {
            "Propagated edge must be in the same unit"
        }

        if (reason != null) {
            edge.reason = reason
        }

        if (pathEdges.add(edge)) {
            val doPrintZero = false
            if (edge.from.statement.toString() == "noop") {
                if (doPrintZero || edge.to.fact != Zero) {
                    logger.debug { "Propagating $edge in ${edge.method} via ${edge.reason}" }
                }
            }

            // Add edge to worklist:
            workList.send(edge)

            // Send edge to analyzer/manager:
            for (event in analyzer.handleNewEdge(edge)) {
                manager.handleEvent(event)
            }

            return true
        }

        return false
    }

    private suspend fun tabulationAlgorithm(): Unit = coroutineScope {
        while (isActive) {
            val edge = workList.tryReceive().getOrNull() ?: break
            tabulationAlgorithmStep(edge)
        }
    }

    private val JcMethod.isExtern: Boolean
        get() = unitResolver.resolve(this) != unit

    private suspend fun tabulationAlgorithmStep(currentEdge: Edge) {
        val (startVertex, currentVertex) = currentEdge
        val (current, currentFact) = currentVertex

        val currentCallees = graph.callees(current).toList()
        // val currentIsCall = currentCallees.isNotEmpty()
        val currentIsCall = current.callExpr != null
        val currentIsExit = current in graph.exitPoints(current.location.method)

        if (currentIsCall) {
            // Propagate through the call-to-return-site edge:
            for (returnSite in graph.successors(current)) {
                val factsAtReturnSite = flowSpace
                    .obtainCallToReturnSiteFlowFunction(current, returnSite)
                    .compute(currentFact)
                for (returnSiteFact in factsAtReturnSite) {
                    val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                    val newEdge = Edge(startVertex, returnSiteVertex)
                    propagate(newEdge, reason = currentEdge)
                }
            }

            // Propagate through the call:
            for (callee in currentCallees) {
                // TODO: check whether we need to analyze the callee (or it was skipped due to MethodSource)
                if (analyzer.isSkipped(callee)) {
                    logger.info { "Skipping method $callee" }
                } else {
                    val factsAtCalleeStart = flowSpace
                        .obtainCallToStartFlowFunction(current, callee)
                        .compute(currentFact)
                    for (calleeStart in graph.entryPoints(callee)) {
                        for (calleeStartFact in factsAtCalleeStart) {
                            val calleeStartVertex = Vertex(calleeStart, calleeStartFact)

                            if (callee.isExtern) {
                                // Initialize analysis of callee:
                                for (event in analyzer.handleCrossUnitCall(currentVertex, calleeStartVertex)) {
                                    manager.handleEvent(event)
                                }

                                // Subscribe on summary edges:
                                val event = SubscriptionForSummaryEdges2(callee) { edge ->
                                    if (edge.from == calleeStartVertex) {
                                        handleSummaryEdge(edge, startVertex, current)
                                    } else {
                                        logger.debug { "Skipping unsuitable edge $edge" }
                                    }
                                }
                                manager.handleEvent(event)
                            } else {
                                // Save info about the call for summary edges that will be found later:
                                callSitesOf.getOrPut(calleeStartVertex) { mutableSetOf() }.add(currentEdge)

                                // Initialize analysis of callee:
                                run {
                                    val newEdge = Edge(calleeStartVertex, calleeStartVertex) // loop
                                    propagate(newEdge, reason = currentEdge)
                                }

                                // Handle already-found summary edges:
                                for (exitVertex in summaryEdges[calleeStartVertex].orEmpty()) {
                                    val edge = Edge(calleeStartVertex, exitVertex)
                                    handleSummaryEdge(edge, startVertex, current)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (currentIsExit) {
                // Propagate through the summary edge:
                for (callerPathEdge in callSitesOf[startVertex].orEmpty()) {
                    val callerStartVertex = callerPathEdge.from
                    val caller = callerPathEdge.to.statement
                    handleSummaryEdge(currentEdge, callerStartVertex, caller)
                }

                // Add new summary edge:
                summaryEdges.getOrPut(startVertex) { mutableSetOf() }.add(currentVertex)
            }

            // Simple (sequential) propagation to the next instruction:
            for (next in graph.successors(current)) {
                val factsAtNext = flowSpace
                    .obtainSequentFlowFunction(current/*, next*/)
                    .compute(currentFact)
                for (nextFact in factsAtNext) {
                    val nextVertex = Vertex(next, nextFact)
                    val newEdge = Edge(startVertex, nextVertex)
                    propagate(newEdge, reason = currentEdge)
                }
            }
        }
    }

    private suspend fun handleSummaryEdge(
        edge: Edge,
        startVertex: Vertex,
        caller: JcInst,
    ) {
        for (returnSite in graph.successors(caller)) {
            // val calleeStartVertex = edge.from
            val (exit, exitFact) = edge.to
            val finalFacts = flowSpace
                .obtainExitToReturnSiteFlowFunction(caller, returnSite, exit)
                .compute(exitFact)
            for (returnSiteFact in finalFacts) {
                val returnSiteVertex = Vertex(returnSite, returnSiteFact)
                val newEdge = Edge(startVertex, returnSiteVertex)
                propagate(newEdge, reason = edge)
            }
        }
    }
}

fun main() {
    //
}
