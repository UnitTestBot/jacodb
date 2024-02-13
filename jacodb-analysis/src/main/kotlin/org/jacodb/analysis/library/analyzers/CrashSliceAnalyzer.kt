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

import org.jacodb.analysis.engine.*
import org.jacodb.analysis.graph.JcNoopInst
import org.jacodb.analysis.paths.*
import org.jacodb.analysis.paths.toPath
import org.jacodb.analysis.paths.toPathOrNull
import org.jacodb.analysis.sarif.VulnerabilityDescription
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.*
import org.jacodb.api.ext.cfg.callExpr


/**
 * Implementation of program slicing algorithm based on the backward taint analysis and IFDS algorithm.
 * This analysis is a modification of [SliceTaintAnalyzer] that computes the program slice w.r.t. the
 * crash report.
 *
 * @property sinks is a list of [JcInst] that was obtained from stack trace of certain crash report.
 */
class CrashSliceAnalyzer(graph: JcApplicationGraph, val sinks: List<JcInst>, maxPathLength: Int) : AbstractAnalyzer(graph) {
    override val flowFunctions: FlowFunctionsSpace = CrashSliceBackwardFunctions(graph, sinks, maxPathLength)

    override val isMainAnalyzer: Boolean
        get() = true

    companion object {
        const val ruleId: String = "slicing"
        const val modifiedId: String = "modified"
        //const val returnId: String = "return"
    }

    /**
     * This method handles new [IfdsEdge] and creates new [VulnerabilityLocation] that will form the resulting
     * program slice.
     */
    override fun handleNewEdge(edge: IfdsEdge): List<AnalysisDependentEvent> = buildList {
        val (inst, fact0) = edge.v

        if (inst == sinks.last()) {
            val desc = VulnerabilityDescription(
                "Important fact $fact0 at sink instruction $inst at location ${inst.location}", modifiedId)
            add(NewSummaryFact((VulnerabilityLocation(desc, edge.v))))
            verticesWithTraceGraphNeeded.add(edge.v)
        } else {
            val variableIsUsedAtExternalMethod = fact0 is SliceDataFlowNode
                    && inst.callExpr != null
                    && inst.values.mapNotNull { it.toPathOrNull() }.any { it.startsWith(fact0.variable) }
                    && graph.callees(inst).toList().isEmpty()

            val variableIsAssignedToSmth = (fact0 is SliceDataFlowNode
                    && inst is JcAssignInst) && (inst.lhv.toPathOrNull()?.startsWith(fact0.variable) == true
                    || fact0.variable.startsWith(inst.lhv.toPathOrNull()))

            val variableInReturn = fact0 is SliceDataFlowNode
                    && inst is JcReturnInst
                    && inst.values.mapNotNull { it.toPathOrNull() }.any { it.startsWith(fact0.variable) }

            val ifInstIsImportant = fact0 is SliceControlFlowNode
                    && inst is JcIfInst
                    && fact0.inst == null

//            if (fact0 is SliceControlFlowNode && inst is JcNoopInst && fact0.isFromReturn != null) {
//                val desc = VulnerabilityDescription(
//                    "Important return $fact0 at instruction ${fact0.isFromReturn} at location ${fact0.isFromReturn.location}", returnId)
//                add(NewSummaryFact((VulnerabilityLocation(desc, edge.v))))
//                verticesWithTraceGraphNeeded.add(edge.v)
//            } else
            if (variableIsUsedAtExternalMethod || variableIsAssignedToSmth || ifInstIsImportant || variableInReturn) {
                if (fact0 is SliceDataFlowNode && fact0.isFromReturn != null) {
                    val desc = VulnerabilityDescription(
                        "Important fact $fact0 at instruction $inst at location ${inst.location}"
                        , "$modifiedId from ${fact0.isFromReturn.location}")
                    add(NewSummaryFact((VulnerabilityLocation(desc, edge.v))))
                    verticesWithTraceGraphNeeded.add(edge.v)
                } else if (fact0 is SliceControlFlowNode && fact0.isFromReturn != null) {
                    if (graph.methodOf(fact0.isFromReturn) == graph.methodOf(inst)) {
                        val desc = VulnerabilityDescription(
                            "Important control-flow fact $fact0 at instruction $inst at location ${inst.location}"
                            , modifiedId + " from " + fact0.isFromReturn.location)
                        add(NewSummaryFact((VulnerabilityLocation(desc, edge.v))))
                        verticesWithTraceGraphNeeded.add(edge.v)
                    }
                } else {
                    val desc = VulnerabilityDescription(
                        "Important fact $fact0 at instruction $inst at location ${inst.location}", modifiedId)
                    add(NewSummaryFact((VulnerabilityLocation(desc, edge.v))))
                    verticesWithTraceGraphNeeded.add(edge.v)
                }
            }
        }

        addAll(super.handleNewEdge(edge))
    }

    override fun handleNewCrossUnitCall(fact: CrossUnitCallFact): List<AnalysisDependentEvent> = buildList {
        add(EdgeForOtherRunnerQuery(IfdsEdge(fact.calleeVertex, fact.calleeVertex)))
        addAll(super.handleNewCrossUnitCall(fact))
    }
}

fun CrashSliceAnalyzerFactory(sinks: List<JcInst>, maxPathLength: Int) = AnalyzerFactory { graph ->
    CrashSliceAnalyzer(graph, sinks, maxPathLength)
}

private class CrashSliceBackwardFunctions(
    graph: JcApplicationGraph,
    val sinks: List<JcInst>,
    maxPathLength: Int,
) : AbstractTaintBackwardFunctions(graph, maxPathLength) {

    private val iDomRelation: MutableMap<JcInst, JcInst> = mutableMapOf()
    //private val traceArgs: Set<JcValue> = findAllTraceArgs()

    override fun transmitBackDataFlow(from: JcValue, to: JcExpr, atInst: JcInst, fact: DomainFact, dropFact: Boolean): List<DomainFact> {
        if (atInst == sinks.last()) {
            val sinkFacts = atInst.values.mapNotNull { it.toPathOrNull()}.map { SliceDataFlowNode(it) }
            return if (fact == ZEROFact) {
                listOf(ZEROFact).plus(sinkFacts).plus(SliceControlFlowNode())
            } else if (fact is SliceControlFlowNode && fact.isFromReturn != null) {
                emptyList()
            } else {
                listOf(fact)
            }
        }

        if (fact == ZEROFact) {
            return listOf(ZEROFact)
        }

        if (atInst is JcNoopInst && isNotInTrace(atInst, to)) {
            return emptyList()
        }

        if (fact is SliceControlFlowNode) {
            if (fact.isFromReturn != null && atInst is JcNoopInst) {
                return emptyList()
            }
        }

        if (fact !is SliceDataFlowNode) {
            return listOf(fact)
        }

        val factPath = fact.variable
        val fromPath = from.toPathOrNull() ?: return listOf(fact)
        val toPath =  to.toPathOrNull()

        if (toPath != null) {
            return if (factPath.startsWith(fromPath)) {
                listOf(SliceDataFlowNode(toPath, null, fact.isFromReturn))
                    .plus(SliceControlFlowNode(null, fact.isFromReturn))
            } else if (fromPath.startsWith(factPath)) {
                listOf(fact).plus(SliceDataFlowNode(toPath, null, fact.isFromReturn))
                    .plus(SliceControlFlowNode(null, fact.isFromReturn))
            } else {
                listOf(fact)
            }
        }

        val toOperands = to.operands.mapNotNull { it.toPathOrNull() }

        if (toOperands.isNotEmpty()) {
            return if (factPath.startsWith(fromPath)) {
                toOperands.map { SliceDataFlowNode(it, null, fact.isFromReturn) }.toList()
                    .plus(SliceControlFlowNode(null, fact.isFromReturn))
            } else if (fromPath.startsWith(factPath)) {
                listOf(fact).plus(toOperands.map { SliceDataFlowNode(it, null, fact.isFromReturn) })
                    .plus(SliceControlFlowNode(null, fact.isFromReturn))
            } else {
                listOf(fact)
            }
        }

        return if (factPath.startsWith(fromPath)) {
            listOf(SliceControlFlowNode(null, fact.isFromReturn))
        } else if (fromPath.startsWith(factPath)) {
            listOf(fact).plus(SliceControlFlowNode(null, fact.isFromReturn))
        } else {
            listOf(fact)
        }
    }

    override fun transmitDataFlowAtNormalInst(inst: JcInst, nextInst: JcInst, fact: DomainFact): List<DomainFact> {
        if (inst == sinks.last()) {
            val sinkDataFlowFacts = inst.values.mapNotNull { it.toPathOrNull()}.map { SliceDataFlowNode(it) }
            return if (fact == ZEROFact) {
                listOf(ZEROFact).plus(sinkDataFlowFacts).plus(SliceControlFlowNode())
            } else if (fact is SliceControlFlowNode && fact.isFromReturn != null) {
                emptyList()
            } else {
                listOf(fact)
            }
        }

        if (fact is SliceControlFlowNode) {
            if (fact.isFromReturn != null && inst is JcNoopInst) {
                return emptyList()
            }

            var conditionFacts = mutableListOf<SliceDataFlowNode>()
            if (inst is JcIfInst) {
                conditionFacts.addAll(inst.condition.operands.mapNotNull { it.toPathOrNull() }.map { SliceDataFlowNode(it, null, fact.isFromReturn) })
            }

            if (inst is JcSwitchInst) {
                conditionFacts.addAll(inst.operands.mapNotNull { it.toPathOrNull() }.map { SliceDataFlowNode(it, null, fact.isFromReturn) })
            }

            if (fact.inst == null) {
                var successors = graph.successors(inst).toList()

                if (successors.size > 1) {
                    var nextInst = iDomRelation.getOrPut(inst) { findIDom(inst) }
                    successors = graph.successors(nextInst).toList()
                    while (successors.size > 1) {
                        if (nextInst !is JcIfInst) {
                            return listOf(SliceControlFlowNode(nextInst, fact.isFromReturn)).plus(conditionFacts)
                        }
                        nextInst = iDomRelation.getOrPut(nextInst) { findIDom(nextInst) }
                        successors = graph.successors(nextInst).toList()
                    }
                    return if (successors.isEmpty()) {
                        conditionFacts.toList()
                    } else {
                        listOf(SliceControlFlowNode(successors[0], fact.isFromReturn)).plus(conditionFacts)
                    }
                }

                return listOf(fact).plus(conditionFacts)
            } else {
                return if (fact.inst == inst) {
                    listOf(SliceControlFlowNode(null, fact.isFromReturn)).plus(conditionFacts)
                } else {
                    listOf(fact)
                }
            }
        }

        if (fact == ZEROFact) {
            if (isEarlyExit(inst)) { // handle early "return" and "throw"
                return listOf(SliceControlFlowNode(null, inst))
            }
            return listOf(ZEROFact)
        }

        if (fact !is SliceDataFlowNode) {
            return listOf(fact)
        }

        val callExpr = inst.callExpr as? JcInstanceCallExpr ?: return listOf(fact)
        if (fact.variable.startsWith(callExpr.instance.toPath())) {
            return inst.values.mapNotNull { it.toPathOrNull() }.map { SliceDataFlowNode(it, null, fact.isFromReturn) }
        }

        return listOf(fact)
    }

    fun isNotInTrace(atInst: JcInst, to: JcExpr): Boolean {
        for (i in 1 until sinks.size) {
            if (graph.methodOf(atInst) == graph.methodOf(sinks[i])) {
                return sinks[i - 1].callExpr?.args?.contains(to) == false
            }
        }
        return false
        /*if (traceArgs.contains(to)) {
            //println(graph.methodOf(atInst))
        }
        return (sinks.last().callExpr?.args?.contains(to) == true) || !traceArgs.contains(to)*/
    }

    fun findAllTraceArgs(): Set<JcValue> {
        var argsSet = mutableSetOf<JcValue>()
        for (sink in sinks) {
            val instList = graph.methodOf(sink).instList
            var i = instList.indexOf(sink)
            while (i >= 0) {
                val args = instList[i].callExpr?.args
                if (args != null) {
                    argsSet.addAll(args)
                }
                i--
            }
        }
        return argsSet.toSet()
    }

    // TODO: rewrite this for a more general case
    fun isEarlyExit(inst: JcInst) : Boolean {
        val instList = graph.methodOf(inst).instList
        return (inst is JcReturnInst || inst is JcThrowInst)
                && sinks.any { instList.indexOf(inst) < instList.indexOf(it) }
        //return (inst is JcReturnInst || inst is JcThrowInst)
        //        && sinks.any { graph.methodOf(inst).instList.contains(it) } && canAvoidSink(inst)
    }

    /*fun canAvoidSink(inst: JcInst, visited: Set<JcInst> = setOf(inst)) : Boolean {
        val successors = graph.successors(inst).toList().filter { !visited.contains(it) && !sinks.contains(it)}
        if (successors.any { it is JcNoopInst }) {
            return true
        }
        return successors.any { canAvoidSink(it, visited.plus(it)) }
    }*/

    /**
     * This function finds idom (immediate dominator) for instruction in a control-flow graph. The function is
     * used for determining the activation points of control-flow dependency facts.
     */
    fun findIDom(inst: JcInst, visited: Set<JcInst> = emptySet()) : JcInst {
        val successors = graph.successors(inst).toList().filter { !visited.contains(it) }

        if (successors.isEmpty()) {
            return inst
        }

        if (successors.size == 1) {
            return successors[0]
        }

        if (inst is JcIfInst) { // handler of "for" block
            val gotoSuccessors = successors.filterIsInstance<JcGotoInst>()
            if (gotoSuccessors.size > 1) {
                val instList = graph.methodOf(inst).instList
                val instIndex = instList.indexOf(inst)
                for (gotoInst in gotoSuccessors) {
                    if (instIndex == instList.indexOf(gotoInst) + 1 ) {
                        return gotoInst
                    }
                }
            }
        }

        return findCommonAncestor(successors, successors.map { mutableSetOf(it) }.toMutableList())
    }


    /**
     * This is a helper function for [findIDom] that finds common ancestor for list of instructions
     * in a control-flow graph.
     */
    fun findCommonAncestor(front: List<JcInst>, visited: MutableList<MutableSet<JcInst>>) : JcInst {
        val nextFront = front.mapIndexed { index, jcInst -> iDomRelation.getOrPut(jcInst) { findIDom(jcInst, visited[index]) } }

        visited.forEachIndexed { index, jcInsts -> jcInsts.add(nextFront[index]) }

        val commonInsts = visited.reduce { acc, jcInsts -> acc.intersect(jcInsts).toMutableSet() }.toList()

        if (commonInsts.isEmpty()) {
            return findCommonAncestor(nextFront, visited)
        }

        return commonInsts[0]
    }
}
