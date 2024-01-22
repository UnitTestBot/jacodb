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

package org.jacodb.analysis.library.analyzers

import org.jacodb.analysis.config.BasicConditionEvaluator
import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.config.TaintActionEvaluator
import org.jacodb.analysis.engine.AbstractAnalyzer
import org.jacodb.analysis.engine.AnalysisDependentEvent
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.EdgeForOtherRunnerQuery
import org.jacodb.analysis.engine.FlowFunctionsSpace
import org.jacodb.analysis.engine.IfdsEdge
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.engine.NewSummaryFact
import org.jacodb.analysis.ifds2.Tainted
import org.jacodb.analysis.engine.VulnerabilityLocation
import org.jacodb.analysis.engine.ZEROFact
import org.jacodb.analysis.ifds2.toDomainFact
import org.jacodb.analysis.logger
import org.jacodb.analysis.paths.minus
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.analysis.sarif.VulnerabilityDescription
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.locals
import org.jacodb.api.cfg.values
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.findTypeOrNull
import org.jacodb.taint.configuration.AnyArgument
import org.jacodb.taint.configuration.Argument
import org.jacodb.taint.configuration.AssignMark
import org.jacodb.taint.configuration.Result
import org.jacodb.taint.configuration.ResultAnyElement
import org.jacodb.taint.configuration.TaintEntryPointSource
import org.jacodb.taint.configuration.TaintMethodSink
import org.jacodb.taint.configuration.This

fun isSourceMethodToGenerates(isSourceMethod: (JcMethod) -> Boolean): (JcInst) -> List<TaintAnalysisNode> {
    return generates@{ inst: JcInst ->
        val callExpr = inst.callExpr?.takeIf { isSourceMethod(it.method.method) }
            ?: return@generates emptyList()
        if (inst is JcAssignInst && isSourceMethod(callExpr.method.method)) {
            listOf(TaintAnalysisNode(inst.lhv.toPath(), nodeType = "TAINT"))
        } else {
            emptyList()
        }
    }
}

fun isSinkMethodToSinks(isSinkMethod: (JcMethod) -> Boolean): (JcInst) -> List<TaintAnalysisNode> {
    return sinks@{ inst: JcInst ->
        val callExpr = inst.callExpr?.takeIf { isSinkMethod(it.method.method) }
            ?: return@sinks emptyList()
        callExpr.values
            .mapNotNull { it.toPathOrNull() }
            .map { TaintAnalysisNode(it, nodeType = "TAINT") }
    }
}

fun isSanitizeMethodToSanitizes(isSanitizeMethod: (JcMethod) -> Boolean): (JcExpr, TaintNode) -> Boolean {
    return sanitizes@{ expr: JcExpr, fact: TaintNode ->
        if (expr !is JcCallExpr) {
            false
        } else {
            if (isSanitizeMethod(expr.method.method) && fact.activation == null) {
                expr.values.any {
                    it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull())
                }
            } else {
                false
            }
        }
    }
}

internal val List<String>.asMethodMatchers: (JcMethod) -> Boolean
    get() = { method: JcMethod ->
        any { it.toRegex().matches("${method.enclosingClass.name}#${method.name}") }
    }

abstract class TaintAnalyzer(
    graph: JcApplicationGraph,
    maxPathLength: Int,
) : AbstractAnalyzer(graph) {
    abstract val generates: (JcInst) -> List<DomainFact>
    abstract val sanitizes: (JcExpr, TaintNode) -> Boolean
    abstract val sinks: (JcInst) -> List<TaintAnalysisNode>

    override val flowFunctions: FlowFunctionsSpace by lazy {
        TaintForwardFunctions(graph, maxPathLength, generates, sanitizes)
    }

    override val isMainAnalyzer: Boolean
        get() = true

    // private val skipped: MutableMap<JcMethod, Boolean> = mutableMapOf()
    // override fun isSkipped(method: JcMethod): Boolean {
    //     return skipped.getOrPut(method) {
    //         // TODO: read the config and assign True if there is a MethodSource item, and False otherwise.
    //         // Note: the computed value is cached.
    //
    //         fun <T> magic(): T = TODO()
    //         val current: JcInst = magic()
    //         val conditionEvaluator = BasicConditionEvaluator(CallPositionToJcValueResolver(current))
    //
    //         // FIXME: we need the call itself in order to evaluate the condition
    //         for (item in config.items) {
    //             if (item is TaintMethodSource) {
    //                 item.condition.accept(conditionEvaluator)
    //             }
    //         }
    //
    //         TODO()
    //     }
    // }

    protected abstract fun generateDescriptionForSink(sink: IfdsVertex): VulnerabilityDescription

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        val configOk = run {
            val callExpr = edge.to.statement.callExpr ?: return@run false
            val callee = callExpr.method.method

            val config = (flowFunctions as TaintForwardFunctions)
                .taintConfigurationFeature?.let { feature ->
                    logger.trace { "Extracting config for $callee" }
                    feature.getConfigForMethod(callee)
                } ?: return@run false

            // TODO: not always we want to skip sinks on ZeroFacts. Some rules might have ConstantTrue or just true (when evaluated with ZeroFact) condition.
            if (edge.to.domainFact !is TaintNode) {
                return@run false
            }

            // Determine whether 'edge.to' is a sink via config:
            val conditionEvaluator = FactAwareConditionEvaluator(
                Tainted(edge.to.domainFact),
                CallPositionToJcValueResolver(edge.to.statement),
            )
            var isSink = false
            var triggeredItem: TaintMethodSink? = null
            for (item in config.filterIsInstance<TaintMethodSink>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    isSink = true
                    triggeredItem = item
                    break
                }
                // FIXME: unconditionally let it be the sink.
                // isSink = true
                // triggeredItem = item
                // break
            }
            if (isSink) {
                val desc = generateDescriptionForSink(edge.to)
                val vulnerability = VulnerabilityLocation(desc, edge.to, edge, rule = triggeredItem)
                logger.info { "Found sink: $vulnerability in ${vulnerability.method}" }
                add(NewSummaryFact(vulnerability))
                verticesWithTraceGraphNeeded.add(edge.to)
            }
            true
        }

        if (!configOk) {
            // "config"-less behavior:
            if (edge.to.domainFact in sinks(edge.to.statement)) {
                val desc = generateDescriptionForSink(edge.to)
                val vulnerability = VulnerabilityLocation(desc, edge.to, edge)
                logger.info { "Found sink: $vulnerability in ${vulnerability.method}" }
                add(NewSummaryFact(vulnerability))
                verticesWithTraceGraphNeeded.add(edge.to)
            }
        }

        // "Default" behavior:
        addAll(super.handleNewEdge(edge))
    }
}

abstract class TaintBackwardAnalyzer(
    graph: JcApplicationGraph,
    maxPathLength: Int,
) : AbstractAnalyzer(graph) {
    abstract val generates: (JcInst) -> List<DomainFact>
    abstract val sinks: (JcInst) -> List<TaintAnalysisNode>

    override val isMainAnalyzer: Boolean
        get() = false

    override val flowFunctions: FlowFunctionsSpace by lazy {
        TaintBackwardFunctions(graph, generates, sinks, maxPathLength)
    }

    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        if (edge.to.statement in graph.exitPoints(edge.method)) {
            add(EdgeForOtherRunnerQuery(IfdsEdge(edge.to, edge.to)))
        }
    }
}

private class TaintForwardFunctions(
    graph: JcApplicationGraph,
    private val maxPathLength: Int,
    private val generates: (JcInst) -> List<DomainFact>,
    private val sanitizes: (JcExpr, TaintNode) -> Boolean,
) : AbstractTaintForwardFunctions(graph.classpath) {

    override fun transmitDataFlow(
        from: JcExpr,
        to: JcValue,
        atInst: JcInst,
        fact: DomainFact,
        dropFact: Boolean,
    ): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(atInst)
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        val default: List<DomainFact> = if (
            dropFact ||
            (sanitizes(from, fact) && fact.variable == (from as? JcInstanceCallExpr)?.instance?.toPath())
        ) {
            emptyList()
        } else {
            listOf(fact)
        }

        val toPath = to.toPathOrNull()?.limit(maxPathLength) ?: return default
        val newPossibleTaint = if (sanitizes(from, fact)) emptyList() else listOf(fact.moveToOtherPath(toPath))

        val fromPath = from.toPathOrNull()
        if (fromPath != null) {
            return if (sanitizes(from, fact)) {
                default
            } else if (fromPath.startsWith(fact.variable)) {
                default + newPossibleTaint
            } else {
                normalFactFlow(fact, fromPath, toPath, dropFact, maxPathLength)
            }
        }

        if (
            from.values.any {
                it.toPathOrNull().startsWith(fact.variable) ||
                    fact.variable.startsWith(it.toPathOrNull())
            }
        ) {
            val instanceOrNull = (from as? JcInstanceCallExpr)?.instance
            if (instanceOrNull != null && !sanitizes(from, fact)) {
                val instancePath = instanceOrNull.toPathOrNull()
                if (instancePath != null) {
                    return default + newPossibleTaint + fact.moveToOtherPath(instancePath)
                }
            }
            return default + newPossibleTaint
        } else if (fact.variable.startsWith(toPath)) {
            return emptyList()
        }
        return default
    }

    override fun transmitDataFlowAtNormalInst(
        inst: JcInst,
        nextInst: JcInst, // unused
        fact: DomainFact,
    ): List<DomainFact> {
        // Generate new facts:
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + generates(inst)
        }

        if (fact !is TaintNode) {
            return emptyList()
        }

        // Pass-through:
        val callExpr = inst.callExpr ?: return listOf(fact)
        if (callExpr !is JcInstanceCallExpr) {
            return listOf(fact)
        }
        val instance = callExpr.instance

        // Sanitize:
        if (instance.toPath() == fact.variable && sanitizes(callExpr, fact)) {
            return emptyList()
        }

        // TODO: do no do this:
        // val factIsPassed = callExpr.values.any {
        //     it.toPathOrNull().startsWith(fact.variable) || fact.variable.startsWith(it.toPathOrNull())
        // }
        // return if (factIsPassed && !sanitizes(callExpr, fact)) {
        //     // Pass-through, but also (?) taint the 'instance'
        //     listOf(fact) + fact.moveToOtherPath(instance.toPath())
        // } else {
        //     // Pass-through
        //     listOf(fact)
        // }

        // Pass-through
        return listOf(fact)
    }

    override fun obtainPossibleStartFacts(startStatement: JcInst): List<DomainFact> = buildList {
        add(ZEROFact)

        val method = startStatement.location.method
        val config = taintConfigurationFeature?.let { feature ->
            logger.trace { "Extracting config for $method" }
            feature.getConfigForMethod(method)
        }
        if (config != null) {
            val conditionEvaluator = BasicConditionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance

                    is Argument -> method.flowGraph().locals
                        .filterIsInstance<JcArgument>()
                        .singleOrNull { it.index == position.index }
                        ?: error("Cannot resolve $position for $method")

                    AnyArgument -> error("Unexpected $position")
                    Result -> error("Unexpected $position")
                    ResultAnyElement -> error("Unexpected $position")
                }
            }
            val actionEvaluator = TaintActionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance.toPathOrNull()
                        ?: error("Cannot resolve $position for $method")

                    is Argument -> {
                        val p = method.parameters[position.index]
                        val t = cp.findTypeOrNull(p.type)
                        if (t != null) {
                            JcArgument.of(p.index, p.name, t).toPathOrNull()
                        } else {
                            null
                        }
                            ?: error("Cannot resolve $position for $method")
                    }

                    AnyArgument -> error("Unexpected $position")
                    Result -> error("Unexpected $position")
                    ResultAnyElement -> error("Unexpected $position")
                }
            }
            for (item in config.filterIsInstance<TaintEntryPointSource>()) {
                if (item.condition.accept(conditionEvaluator)) {
                    for (action in item.actionsAfter) {
                        when (action) {
                            is AssignMark -> {
                                add(actionEvaluator.evaluate(action).toDomainFact())
                            }

                            else -> error("$action is not supported for $item")
                        }
                    }
                }
            }
        }
    }
}

private class TaintBackwardFunctions(
    graph: JcApplicationGraph,
    val generates: (JcInst) -> List<DomainFact>,
    val sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {

    override fun transmitBackDataFlow(
        from: JcValue,
        to: JcExpr,
        atInst: JcInst,
        fact: DomainFact,
        dropFact: Boolean,
    ): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(ZEROFact) + sinks(atInst)
        }

        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val factPath = fact.variable
        val default = if (dropFact || fact in generates(atInst)) emptyList() else listOf(fact)
        val fromPath = from.toPathOrNull() ?: return default
        val toPath = to.toPathOrNull()

        if (toPath != null) {
            val diff = factPath.minus(fromPath)
            if (diff != null) {
                val newPath = (toPath / diff).limit(maxPathLength)
                return listOf(fact.moveToOtherPath(newPath))
            }
        } else if (factPath.startsWith(fromPath) || (to is JcInstanceCallExpr && factPath.startsWith(to.instance.toPath()))) {
            return to.values.mapNotNull { it.toPathOrNull() }.map { TaintAnalysisNode(it, nodeType = "TAINT") }
        }
        return default
    }

    override fun transmitDataFlowAtNormalInst(
        inst: JcInst,
        nextInst: JcInst,
        fact: DomainFact,
    ): List<DomainFact> {
        if (fact == ZEROFact) {
            return listOf(fact) + sinks(inst)
        }
        if (fact !is TaintAnalysisNode) {
            return emptyList()
        }

        val callExpr = inst.callExpr as? JcInstanceCallExpr ?: return listOf(fact)
        if (fact.variable.startsWith(callExpr.instance.toPath())) {
            return inst.values.mapNotNull { it.toPathOrNull() }.map { TaintAnalysisNode(it, nodeType = "TAINT") }
        }

        return listOf(fact)
    }
}
