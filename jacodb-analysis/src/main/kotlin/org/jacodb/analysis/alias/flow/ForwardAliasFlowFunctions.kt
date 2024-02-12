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

import org.jacodb.analysis.alias.AliasOnReturn
import org.jacodb.analysis.alias.FieldWrite
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

class ForwardAliasFlowFunctions(
    private val cp: JcClasspath,
) : AliasFlowFunctions() {

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
                accessGraph.forwardAssign(lhs, rhs)

            lhs is JcLocalVar && rhs is JcNewExpr ->
                accessGraph.forwardAllocate(lhs)

            lhs is JcFieldRef && lhs.instance != null && rhs is JcLocal -> {
                val local = lhs.instance as JcLocal
                val field = lhs.field.field
                accessGraph.forwardFieldWrite(local, field, rhs).onEach { ag ->
                    val poi = FieldWrite(current, local, field, ag)
                    events.add(poi)
                }
            }

            lhs is JcLocalVar && rhs is JcFieldRef && rhs.instance != null -> // TODO: static fields
                accessGraph.forwardFieldRead(lhs, rhs.instance as JcLocal, rhs.field.field)

            else -> setOf(accessGraph)
        }
    }

    override fun obtainCallToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ) =  FlowFunction<AccessGraph> { accessGraph ->
        val expr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val lhs = (callStatement as? JcAssignInst)?.lhv as? JcLocalVar
        val ths = (expr as? JcInstanceCallExpr)?.instance as? JcLocal
        val parameters = expr.args
        accessGraph.forwardCallToReturn(lhs, ths, parameters)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        startStatement: JcInst
    ) = FlowFunction<AccessGraph> { accessGraph ->
        val callee = startStatement.location.method
        val expr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val ths = (expr as? JcInstanceCallExpr)?.instance as? JcLocal
        val parameters = expr.args
        accessGraph.forwardCallFlow(ths, parameters, cp.getArgumentsOf(callee))
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ) = FlowFunction<AccessGraph> { accessGraph ->
        val expr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val lhs = (callStatement as? JcAssignInst)?.lhv as? JcLocalVar
        val ths = (expr as? JcInstanceCallExpr)?.instance as? JcLocal
        val retVal = (exitStatement as JcReturnInst).returnValue as? JcLocal
        val parameters = expr.args
        when {
            ths != null && accessGraph.matches(JcThis(ths.type)) -> {
                setOf(accessGraph.substituteLocal(ths))
            } // TODO: emit return
            accessGraph.local is JcArgument -> {
                setOf(accessGraph.substituteLocal(parameters[accessGraph.local.index] as JcLocal))
            } // TODO: emit return
            retVal != null && accessGraph.matches(retVal) && lhs != null -> {
                setOf(accessGraph.substituteLocal(lhs))
            } // TODO: emit return
            else -> emptySet()
        }.onEach { ag ->
            val poi = AliasOnReturn(
                exitStatement,
                returnSite,
                ag
            )
            events.add(poi)
        }
    }

    private fun AccessGraph.forwardAssign(
        lhs: JcLocalVar,
        rhs: JcLocal
    ): Set<AccessGraph> =
        when {
            matches(rhs) -> setOf(substituteLocal(lhs), this)
            matches(lhs) -> emptySet()
            else -> setOf(this)
        }

    private fun AccessGraph.forwardFieldWrite(
        lhs: JcLocal,
        field: JcField,
        rhs: JcLocal
    ): Set<AccessGraph> =
        when {
            matches(rhs) -> setOf(prependHead(field).substituteLocal(lhs), this)
            matches(lhs, field) -> emptySet()
            else -> setOf(this)
        }

    private fun AccessGraph.forwardFieldRead(
        lhs: JcLocal,
        rhs: JcLocal,
        field: JcField
    ): Set<AccessGraph> =
        when {
            matches(rhs, field) -> substituteLocal(lhs).removeHead() + this
            matches(lhs) -> emptySet()
            else -> setOf(this)
        }

    private fun AccessGraph.forwardAllocate(lhs: JcLocalVar): Set<AccessGraph> =
        when {
            matches(lhs) -> emptySet()
            else -> setOf(this)
        }

    private fun AccessGraph.forwardCallFlow(
        ths: JcLocal?,
        parameters: List<JcValue>,
        formalParameters: List<JcArgument>
    ): Set<AccessGraph> =
        when {
            ths != null && matches(ths) -> setOf(substituteLocal(JcThis(ths.type)))
            else -> parameters
                .zip(formalParameters)
                .mapNotNullTo(hashSetOf()) { (parameter, formalParameter) ->
                    if (matches(parameter)) {
                        substituteLocal(formalParameter)
                    } else {
                        null
                    }
                }
        }

    private fun AccessGraph.forwardReturn(
        lhs: JcLocal?,
        ths: JcLocal?,
        retVal: JcLocal?,
        parameters: List<JcValue>
    ): Set<AccessGraph> =
        when {
            ths != null && matches(JcThis(ths.type)) -> setOf(substituteLocal(ths)) // TODO: emit return
            local is JcArgument -> setOf(substituteLocal(parameters[local.index] as JcLocal)) // TODO: emit return
            retVal != null && matches(retVal) && lhs != null -> setOf(substituteLocal(lhs)) // TODO: emit return
            else -> emptySet()
        }

    private fun AccessGraph.forwardCallToReturn(
        lhs: JcLocalVar?,
        ths: JcLocal?,
        parameters: List<JcValue>
    ): Set<AccessGraph> =
        when {
            lhs != null && matches(lhs) -> emptySet()
            (ths != null && matches(ths) || parameters.any { matches(it) }) && !isEmpty() -> emptySet()
            else -> setOf(this)
        }
}