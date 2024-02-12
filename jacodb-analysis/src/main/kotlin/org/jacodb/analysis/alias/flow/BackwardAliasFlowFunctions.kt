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

package org.jacodb.analysis.alias.flow

import org.jacodb.analysis.alias.AliasOnCall
import org.jacodb.analysis.alias.AllocationSite
import org.jacodb.analysis.alias.FieldRead
import org.jacodb.analysis.alias.apg.AccessGraph
import org.jacodb.analysis.alias.apg.isEmpty
import org.jacodb.analysis.alias.apg.matches
import org.jacodb.analysis.alias.apg.prependHead
import org.jacodb.analysis.alias.apg.removeHead
import org.jacodb.analysis.alias.apg.substituteLocal
import org.jacodb.analysis.ifds2.FlowFunction
import org.jacodb.analysis.library.analyzers.getArgumentsOf
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcField
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr

class BackwardAliasFlowFunctions(
    private val classpath: JcClasspath,
//    val channel: Channel<AliasEvent>
): AliasFlowFunctions() {
    override fun obtainPossibleStartFacts(method: JcMethod): Collection<AccessGraph> {
        TODO("Not yet implemented")
    }

    override fun obtainSequentFlowFunction(
        current: JcInst,
        next: JcInst
    ) = FlowFunction<AccessGraph> { accessGraph ->
        if (current !is JcAssignInst) {
            return@FlowFunction setOf(accessGraph)
        }

        val lhs = current.lhv
        val rhs = current.rhv
        when {
            lhs is JcLocalVar && rhs is JcLocal ->
                accessGraph.backwardAssign(lhs, rhs)

            lhs is JcLocalVar && rhs is JcNewExpr ->
                when {
                    accessGraph.matches(lhs) && accessGraph.isEmpty() -> {
                        val poi = AllocationSite(current, lhs, rhs)
                        events.add(poi) // TODO: emit ALLOC
                        setOf()
                    }

                    accessGraph.matches(lhs) -> setOf()
                    else -> setOf(accessGraph)
                }

            lhs is JcFieldRef && lhs.instance != null && rhs is JcLocal -> {
                val local = lhs.instance as JcLocal
                val field = lhs.field.field
                accessGraph.backwardFieldWrite(local, field, rhs)
            }

            lhs is JcLocalVar && rhs is JcFieldRef && rhs.instance != null -> // TODO: static fields
                if (accessGraph.matches(lhs)) {
                    setOf(
                        accessGraph.substituteLocal(rhs.instance as JcLocal).prependHead(rhs.field.field)
                    ).onEach { ag ->
                        val poi = FieldRead(current, rhs.instance as JcLocal, rhs.field.field, ag)
                        events.add(poi) // TODO: emit READ
                    }
                } else {
                    setOf(accessGraph)
                }

            else -> setOf(accessGraph)
        }
    }


    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ) = FlowFunction<AccessGraph> { accessGraph ->
        val expr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val lhs = (callStatement as? JcAssignInst)?.lhv as? JcLocalVar
        val ths = (expr as? JcInstanceCallExpr)?.instance as? JcLocal
        val parameters = expr.args
        accessGraph.backwardCallToReturn(lhs, ths, parameters)
    }


    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        startStatement: JcInst
    ) = FlowFunction<AccessGraph> { accessGraph ->
        val callee = startStatement.location.method
        val expr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val lhs = (callStatement as? JcAssignInst)?.lhv as? JcLocalVar
        val ths = (expr as? JcInstanceCallExpr)?.instance as? JcLocal
        val retVal = (startStatement as? JcReturnInst)?.returnValue as? JcLocal
        val parameters = expr.args
        when {
            lhs != null && accessGraph.matches(lhs) && retVal != null -> {
                setOf(accessGraph.substituteLocal(retVal))
            } // TODO: emit call
            ths != null && accessGraph.matches(ths) && !accessGraph.isEmpty() -> {
                setOf(
                    accessGraph.substituteLocal(
                        JcThis(ths.type)
                    )
                )
            } // TODO: emit call
            !accessGraph.isEmpty() -> {
                parameters
                    .zip(classpath.getArgumentsOf(callee))
                    .mapNotNullTo(mutableSetOf()) { (parameter, formalParameter) ->
                        if (accessGraph.matches(parameter)) {
                            accessGraph.substituteLocal(formalParameter)
                        } else {
                            null
                        }
                    }
            } // TODO: emit call
            else -> emptySet()
        }.onEach { ag ->
            val poi = AliasOnCall(callStatement, startStatement, ag)
            events.add(poi) // TODO: call
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ) = FlowFunction<AccessGraph> { accessGraph ->
        val expr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val lhs = (callStatement as? JcAssignInst)?.lhv as? JcLocalVar
        val parameters = expr.args
        accessGraph.backwardReturn(lhs, parameters)
    }

    private fun AccessGraph.backwardAssign(
        lhs: JcLocalVar,
        rhs: JcLocal
    ): Set<AccessGraph> =
        if (matches(lhs)) {
            setOf(substituteLocal(rhs))
        } else {
            setOf(this)
        }

    private fun AccessGraph.backwardFieldWrite(
        lhs: JcLocal,
        field: JcField,
        rhs: JcLocal
    ): Set<AccessGraph> =
        if (matches(lhs, field)) {
            substituteLocal(rhs).removeHead()
        } else {
            setOf(this)
        }

    private fun AccessGraph.backwardFieldRead(
        lhs: JcLocal,
        rhs: JcLocal,
        field: JcField
    ): Set<AccessGraph> =
        if (matches(lhs)) {
            setOf(substituteLocal(rhs).prependHead(field)) // TODO: emit READ
        } else {
            setOf(this)
        }

    private fun AccessGraph.backwardAllocate(lhs: JcLocalVar): Set<AccessGraph> =
        when {
            matches(lhs) && isEmpty() -> setOf() // TODO: emit ALLOC
            matches(lhs) -> setOf()
            else -> setOf(this)
        }

    private fun AccessGraph.backwardCallFlow( // TODO: simplify parameters
        lhs: JcLocalVar?,
        ths: JcLocal?,
        retVal: JcLocal?,
        parameters: List<JcValue>,
        formalParameters: List<JcArgument>
    ): Set<AccessGraph> =
        when {
            lhs != null && matches(lhs) && retVal != null -> setOf(substituteLocal(retVal)) // TODO: emit call
            ths != null && matches(ths) && !isEmpty() -> setOf(substituteLocal(JcThis(ths.type))) // TODO: emit call
            !isEmpty() -> parameters
                .zip(formalParameters)
                .mapNotNullTo(mutableSetOf()) { (parameter, formalParameter) ->
                    if (matches(parameter)) {
                        substituteLocal(formalParameter)
                    } else {
                        null
                    }
                } // TODO: emit call
            else -> emptySet()
        }

    private fun AccessGraph.backwardReturn(
        ths: JcLocal?,
        parameters: List<JcValue>
    ): Set<AccessGraph> =
        when {
            ths != null && matches(JcThis(ths.type)) && !isEmpty() -> setOf(substituteLocal(ths))
            local is JcArgument && !isEmpty() -> setOf(substituteLocal(parameters[local.index] as JcLocal))
            else -> emptySet()
        }

    private fun AccessGraph.backwardCallToReturn(
        lhs: JcLocal?,
        ths: JcLocal?,
        parameters: List<JcValue>
    ): Set<AccessGraph> =
        when {
            lhs != null && matches(lhs) -> emptySet()
            (ths != null && matches(ths) || parameters.any(::matches)) && !isEmpty() -> emptySet()
            else -> setOf(this)
        }
}

