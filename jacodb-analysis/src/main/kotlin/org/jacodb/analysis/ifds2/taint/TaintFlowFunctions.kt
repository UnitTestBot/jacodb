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

package org.jacodb.analysis.ifds2.taint

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jacodb.analysis.config.BasicConditionEvaluator
import org.jacodb.analysis.config.CallPositionToAccessPathResolver
import org.jacodb.analysis.config.CallPositionToJcValueResolver
import org.jacodb.analysis.config.FactAwareConditionEvaluator
import org.jacodb.analysis.config.TaintActionEvaluator
import org.jacodb.analysis.ifds2.FlowFunction
import org.jacodb.analysis.ifds2.FlowFunctions
import org.jacodb.analysis.library.analyzers.getArgument
import org.jacodb.analysis.library.analyzers.getArgumentsOf
import org.jacodb.analysis.library.analyzers.thisInstance
import org.jacodb.analysis.paths.ElementAccessor
import org.jacodb.analysis.paths.minus
import org.jacodb.analysis.paths.startsWith
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
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
import org.jacodb.taint.configuration.TaintMethodSource
import org.jacodb.taint.configuration.TaintPassThrough
import org.jacodb.taint.configuration.This

private val logger = KotlinLogging.logger {}

@Suppress("PublicApiImplicitType")
class ForwardTaintFlowFunctions(
    private val cp: JcClasspath,
    private val graph: JcApplicationGraph,
) : FlowFunctions<TaintFact> {

    internal val taintConfigurationFeature: TaintConfigurationFeature? by lazy {
        cp.features
            ?.singleOrNull { it is TaintConfigurationFeature }
            ?.let { it as TaintConfigurationFeature }
    }

    override fun obtainPossibleStartFacts(
        method: JcMethod,
    ): Collection<TaintFact> = buildSet {
        // Zero (reachability) fact always present at entrypoint:
        add(Zero)

        // // Possibly null arguments:
        // for (p in method.parameters.filter { it.isNullable != false }) {
        //     val t = cp.findTypeOrNull(p.type)!!
        //     val arg = JcArgument.of(p.index, p.name, t)
        //     val path = AccessPath.from(arg)
        //     add(Tainted(path, TaintMark.NULLNESS))
        // }

        // Extract initial facts from the config:
        val config = taintConfigurationFeature?.let { feature ->
            logger.trace { "Extracting config for $method" }
            feature.getConfigForMethod(method)
        }
        if (config != null) {
            // Note: both condition and action evaluator require a custom position resolver.
            val conditionEvaluator = BasicConditionEvaluator { position ->
                when (position) {
                    This -> method.thisInstance

                    is Argument -> run {
                        val p = method.parameters[position.index]
                        cp.getArgument(p)
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
                        cp.getArgument(p)?.toPathOrNull()
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

    private fun transmitTaintAssign(
        fact: Tainted,
        from: JcExpr,
        to: JcValue,
    ): Collection<TaintFact> {
        val toPath = to.toPath()
        val fromPath = from.toPathOrNull()

        // if (fact.mark == TaintMark.NULLNESS) {
        //     // if (from is JcNewExpr ||
        //     //     from is JcNewArrayExpr ||
        //     //     from is JcConstant ||
        //     //     (from is JcCallExpr && from.method.method.isNullable != true)
        //     // ) {
        //     if (fact.variable.startsWith(toPath)) {
        //         // NULLNESS is overridden:
        //         return emptySet()
        //     }
        // }

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
            // if (from is JcNullConstant) {
            //     add(Tainted(toPath, TaintMark.NULLNESS))
            // }

            if (fact.variable.startsWith(toPath)) {
                // 'to' was (sub-)tainted, but it is now overridden by 'from':
                return@buildSet
            } else {
                // Neither 'from' nor 'to' are tainted:
                add(fact)
            }
        }
    }

    private fun transmitTaintReturn(
        fact: Tainted,
        from: JcValue,
        to: JcValue,
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        // if (from is JcNullConstant) {
        //     add(Tainted(toPath, TaintMark.NULLNESS))
        // }

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: JcValue, // instance
        to: JcThis, // this
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: JcThis, // this
        to: JcValue, // instance
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: JcValue, // actual
        to: JcValue, // formal
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        // if (from is JcNullConstant) {
        //     add(Tainted(toPath, TaintMark.NULLNESS))
        // }

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: JcValue, // formal
        to: JcValue, // actual
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    // TODO: rename (consider "propagate")
    // TODO: refactor (consider adding 'transmitTaintSequent' / 'transmitTaintCall')
    private fun transmitTaintNormal(
        fact: Tainted,
        inst: JcInst,
    ): List<TaintFact> {
        // Pass-through:
        return listOf(fact)
    }

    // private fun generates(inst: JcInst): Collection<Fact> = buildList {
    //     if (inst is JcAssignInst) {
    //         val toPath = inst.lhv.toPath()
    //         val from = inst.rhv
    //         if (from is JcNullConstant || (from is JcCallExpr && from.method.method.isNullable == true)) {
    //             add(Tainted(toPath, TaintMark.NULLNESS))
    //         } else if (from is JcNewArrayExpr && (from.type as JcArrayType).elementType.nullable != false) {
    //             val accessors = List((from.type as JcArrayType).dimensions) { ElementAccessor(null) }
    //             val path = toPath / accessors
    //             add(Tainted(path, TaintMark.NULLNESS))
    //         }
    //     }
    // }

    // private val JcIfInst.pathComparedWithNull: JcAccessPath?
    //     get() {
    //         val expr = condition
    //         return if (expr.rhv is JcNullConstant) {
    //             expr.lhv.toPathOrNull()
    //         } else if (expr.lhv is JcNullConstant) {
    //             expr.rhv.toPathOrNull()
    //         } else {
    //             null
    //         }
    //     }

    override fun obtainSequentFlowFunction(
        current: JcInst,
        next: JcInst,
    ) = FlowFunction<TaintFact> { fact ->
        // if (fact is Tainted && fact.mark == TaintMark.NULLNESS) {
        //     if (fact.variable.isDereferencedAt(current)) {
        //         return@FlowFunction emptyList()
        //     }
        // }

        // if (current is JcIfInst) {
        //     val nextIsTrueBranch = next.location.index == current.trueBranch.index
        //     val pathComparedWithNull = current.pathComparedWithNull
        //     if (fact == Zero) {
        //         if (pathComparedWithNull != null) {
        //             if ((current.condition is JcEqExpr && nextIsTrueBranch) ||
        //                 (current.condition is JcNeqExpr && !nextIsTrueBranch)
        //             ) {
        //                 // This is a hack: instructions like `return null` in branch of next will be considered only if
        //                 //  the fact holds (otherwise we could not get there)
        //                 // Note the absense of 'Zero' here!
        //                 return@FlowFunction listOf(Tainted(pathComparedWithNull, TaintMark.NULLNESS))
        //             }
        //         }
        //     } else if (fact is Tainted) {
        //         if (fact.mark == TaintMark.NULLNESS) {
        //             val expr = current.condition
        //             if (pathComparedWithNull != fact.variable) {
        //                 return@FlowFunction listOf(fact)
        //             }
        //             if ((expr is JcEqExpr && nextIsTrueBranch) || (expr is JcNeqExpr && !nextIsTrueBranch)) {
        //                 // comparedPath is null in this branch
        //                 return@FlowFunction listOf(Zero)
        //             } else {
        //                 return@FlowFunction emptyList()
        //             }
        //         }
        //     }
        // }

        if (fact is Zero) {
            // return@FlowFunction listOf(Zero) // + generates(current)
            return@FlowFunction buildSet {
                add(Zero)

                // if (current is JcAssignInst) {
                //     val toPath = current.lhv.toPath()
                //     val from = current.rhv
                //     if (from is JcNullConstant || (from is JcCallExpr && from.method.method.isNullable == true)) {
                //         add(Tainted(toPath, TaintMark.NULLNESS))
                //     } else if (from is JcNewArrayExpr && (from.type as JcArrayType).elementType.nullable != false) {
                //         val size = (from.type as JcArrayType).dimensions
                //         val accessors = List(size) { ElementAccessor }
                //         val path = toPath / accessors
                //         add(Tainted(path, TaintMark.NULLNESS))
                //     }
                // }
            }
        }
        check(fact is Tainted)

        if (current is JcAssignInst) {
            transmitTaintAssign(fact, from = current.rhv, to = current.lhv)
        } else {
            transmitTaintNormal(fact, current)
        }
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst, // FIXME: unused?
    ) = FlowFunction<TaintFact> { fact ->
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
                    return@FlowFunction setOf(
                        fact,
                        fact.copy(variable = callStatement.lhv.toPath())
                    )
                }
            }
            return@FlowFunction setOf(fact)
        }

        val config = taintConfigurationFeature?.let { feature ->
            logger.trace { "Extracting config for $callee" }
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
            return@FlowFunction buildSet {
                add(Zero)

                // if (callStatement is JcAssignInst) {
                //     val toPath = callStatement.lhv.toPath()
                //     val from = callStatement.rhv
                //     if (from is JcNullConstant || (from is JcCallExpr && from.method.method.isNullable == true)) {
                //         add(Tainted(toPath, TaintMark.NULLNESS))
                //     } else if (from is JcNewArrayExpr && (from.type as JcArrayType).elementType.nullable != false) {
                //         val size = (from.type as JcArrayType).dimensions
                //         val accessors = List(size) { ElementAccessor }
                //         val path = toPath / accessors
                //         add(Tainted(path, TaintMark.NULLNESS))
                //     }
                // }

                if (config != null) {
                    val conditionEvaluator = BasicConditionEvaluator(CallPositionToJcValueResolver(callStatement))
                    val actionEvaluator = TaintActionEvaluator(CallPositionToAccessPathResolver(callStatement))

                    // Handle MethodSource config items:
                    for (item in config.filterIsInstance<TaintMethodSource>()) {
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
        }
        check(fact is Tainted)

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
        calleeStart: JcInst,
    ) = FlowFunction<TaintFact> { fact ->
        val callee = calleeStart.location.method

        if (fact == Zero) {
            return@FlowFunction obtainPossibleStartFacts(callee)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            val actualParams = callExpr.args
            val formalParams = cp.getArgumentsOf(callee)
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
        returnSite: JcInst, // unused
        exitStatement: JcInst,
    ) = FlowFunction<TaintFact> { fact ->
        // TODO: do we even need to return non-empty list for zero fact here?
        if (fact == Zero) {
            // return@FlowFunction listOf(Zero)
            return@FlowFunction buildSet {
                add(Zero)
                // if (exitStatement is JcReturnInst && callStatement is JcAssignInst) {
                //     // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                //     exitStatement.returnValue?.let { returnValue ->
                //         if (returnValue is JcNullConstant) {
                //             val toPath = callStatement.lhv.toPath()
                //             add(Tainted(toPath, TaintMark.NULLNESS))
                //         }
                //     }
                // }
            }
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = exitStatement.location.method

        buildSet {
            // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
            // TODO: "if passed by-ref" part is not implemented here yet
            val actualParams = callExpr.args
            val formalParams = cp.getArgumentsOf(callee)
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

@Suppress("PublicApiImplicitType")
class BackwardTaintFlowFunctions(
    private val project: JcClasspath,
    private val graph: JcApplicationGraph,
) : FlowFunctions<TaintFact> {

    override fun obtainPossibleStartFacts(
        method: JcMethod,
    ): Collection<TaintFact> {
        return listOf(Zero)
    }

    private fun transmitTaintBackwardAssign(
        fact: Tainted,
        from: JcValue,
        to: JcExpr,
    ): Collection<TaintFact> {
        val fromPath = from.toPath()
        val toPath = to.toPathOrNull()

        if (toPath != null) {
            // TODO: think about arrays here
            // // Adhoc taint array:
            // if (fromPath.accesses.isNotEmpty()
            //     && fromPath.accesses.last() is ElementAccessor
            //     && fromPath.copy(accesses = fromPath.accesses.dropLast(1)) == fact.variable
            // ) {
            //     val newTaint = fact.copy(variable = toPath)
            //     return setOf(fact, newTaint)
            // }

            val tail = fact.variable - fromPath
            if (tail != null) {
                // Both 'from' and 'to' are tainted now:
                val newPath = toPath / tail
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

    private fun transmitTaintReturn(
        fact: Tainted,
        from: JcValue,
        to: JcValue,
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintInstanceToThis(
        fact: Tainted,
        from: JcValue, // instance
        to: JcThis, // this
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintThisToInstance(
        fact: Tainted,
        from: JcThis, // this
        to: JcValue, // instance
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentActualToFormal(
        fact: Tainted,
        from: JcValue, // actual
        to: JcValue, // formal
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintArgumentFormalToActual(
        fact: Tainted,
        from: JcValue, // formal
        to: JcValue, // actual
    ): Collection<TaintFact> = buildSet {
        val fromPath = from.toPath()
        val toPath = to.toPath()

        val tail = (fact.variable - fromPath) ?: return@buildSet
        val newPath = toPath / tail
        val newTaint = fact.copy(variable = newPath)
        add(newTaint)
    }

    private fun transmitTaintBackwardNormal(
        fact: Tainted,
        inst: JcInst,
    ): List<TaintFact> {
        // Pass-through:
        return listOf(fact)
    }

    override fun obtainSequentFlowFunction(
        current: JcInst,
        next: JcInst,
    ) = FlowFunction<TaintFact> { fact ->
        if (fact is Zero) {
            return@FlowFunction listOf(Zero)
        }
        check(fact is Tainted)

        if (current is JcAssignInst) {
            transmitTaintBackwardAssign(fact, from = current.lhv, to = current.rhv)
        } else {
            transmitTaintBackwardNormal(fact, current)
        }
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst, // FIXME: unused?
    ) = FlowFunction<TaintFact> { fact ->
        // TODO: pass-through on invokedynamic-based String concatenation

        if (fact == Zero) {
            return@FlowFunction listOf(Zero)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = callExpr.method.method

        // // FIXME: adhoc for constructors:
        // if (callee.isConstructor) {
        //     return@FlowFunction listOf(fact)
        // }

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
            // Possibly tainted rhv:
            if (fact.variable.startsWith(callStatement.rhv.toPathOrNull())) {
                return@FlowFunction emptyList() // Overridden by lhv
            }
        }

        // The "most default" behaviour is encapsulated here:
        transmitTaintBackwardNormal(fact, callStatement)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        calleeStart: JcInst,
    ) = FlowFunction<TaintFact> { fact ->
        val callee = calleeStart.location.method

        if (fact == Zero) {
            return@FlowFunction obtainPossibleStartFacts(callee)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")

        buildSet {
            // Transmit facts on arguments (from 'actual' to 'formal'):
            val actualParams = callExpr.args
            val formalParams = project.getArgumentsOf(callee)
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

            // Transmit facts on return value (from 'returnValue' to 'lhv'):
            if (calleeStart is JcReturnInst && callStatement is JcAssignInst) {
                // Note: returnValue can be null here in some weird cases, e.g. in lambda.
                calleeStart.returnValue?.let { returnValue ->
                    addAll(transmitTaintReturn(fact, from = callStatement.lhv, to = returnValue))
                }
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst,
    ) = FlowFunction<TaintFact> { fact ->
        if (fact == Zero) {
            return@FlowFunction listOf(Zero)
        }
        check(fact is Tainted)

        val callExpr = callStatement.callExpr
            ?: error("Call statement should have non-null callExpr")
        val callee = exitStatement.location.method

        buildSet {
            // Transmit facts on arguments (from 'formal' back to 'actual'), if they are passed by-ref:
            // TODO: "if passed by-ref" part is not implemented here yet
            val actualParams = callExpr.args
            val formalParams = project.getArgumentsOf(callee)
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
        }
    }
}
