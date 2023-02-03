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

package org.jacodb.analysis.impl

import kotlinx.coroutines.runBlocking
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Test
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.analysis.ApplicationGraph
import org.utbot.jacodb.api.analysis.JcAnalysisPlatform
import org.utbot.jacodb.api.cfg.JcArgument
import org.utbot.jacodb.api.cfg.JcAssignInst
import org.utbot.jacodb.api.cfg.JcCallInst
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.cfg.JcReturnInst
import org.utbot.jacodb.api.cfg.JcValue
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.impl.analysis.locals
import org.utbot.jacodb.impl.features.InMemoryHierarchy
import org.utbot.jacodb.impl.features.Usages
import java.util.*
import kotlin.math.sqrt

data class Vertex<D>(val statement: JcInst, val domainFact: D)

data class Edge<D>(val u: Vertex<D>, val v: Vertex<D>)

// ответственность за то, чтобы при создании ребер между датафлоу фактами
// при рассматривании разных случаев ребер в IFDS(при вызове метода, при возврате из метода и тд)
// лежит на создателе флоу функции.

// мы подразумеваем, что в флоу фанке нужно уметь считать домены для инструкций, которые выполнятся друг за другом напрямую
interface FlowFunctionInstance<D> {
    fun compute(fact: D): Collection<D>
    fun computeBackward(fact: D): Collection<D>
}

interface FlowFunctionsSpace<Method, Statement, D> {
    fun obtainSequentFlowFunction(current: Statement, next: Statement): FlowFunctionInstance<D>
    fun obtainCallToStartFlowFunction(callStatement: Statement, callee: Method): FlowFunctionInstance<D>
    fun obtainCallToReturnFlowFunction(callStatement: Statement, returnSite: Statement): FlowFunctionInstance<D>
    fun obtainExitToReturnSiteFlowFunction(callStatement: Statement, returnSite: Statement, exitStatement: Statement): FlowFunctionInstance<D>
}

data class VariableNode(val value: JcValue?) {
    companion object {
        fun fromValue(value: JcValue) = VariableNode(value)

        val ZERO = VariableNode(null)
    }
}

/**
 * Flow function which is equal to id for all elements except those in [nonId], for which the result is stored in the map
 */
class IdLikeFlowFunction<D>(
    private val nonId: Map<D, Collection<D>>
): FlowFunctionInstance<D> {
    override fun compute(fact: D): Collection<D> {
        return nonId.getOrDefault(fact, listOf(fact))
    }

    override fun computeBackward(fact: D): Collection<D> {
        val res = mutableListOf<D>()
        if (!nonId.containsKey(fact)) {
            res.add(fact)
        }
        return res + nonId.entries.filter { (_, value) -> value == fact }.map { it.key }
    }
}

class UninitializedVariableFlowFunctions(
    private val classpath: JcClasspath,
    private val platform: JcAnalysisPlatform
): FlowFunctionsSpace<JcMethod, JcInst, VariableNode> {

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<VariableNode> {
        return when (current) {
            is JcAssignInst -> {
                val nonId = mutableMapOf(VariableNode.fromValue(current.lhv) to listOf<VariableNode>())
                current.rhv.operands.map {
                    nonId.put(VariableNode.fromValue(it), listOf(VariableNode.fromValue(current.lhv)))
                }
                IdLikeFlowFunction(nonId)
            }
            else -> IdLikeFlowFunction(emptyMap())
        }
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance<VariableNode> {
        (callStatement as JcCallInst)
        val nonId = mutableMapOf<VariableNode, MutableList<VariableNode>>()
        val args = callStatement.callExpr.args
        val params = callee.parameters.map {
            JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
        }

        platform.flowGraph(callStatement.owner).locals.forEach {
            nonId[VariableNode(it)] = mutableListOf()
        }

        params.zip(args).forEach { (param, arg) ->
            arg.operands.forEach {
                nonId.getOrDefault(VariableNode(it), mutableListOf()).add(VariableNode(param))
            }
        }

        return IdLikeFlowFunction(nonId)
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance<VariableNode> {
        return object : FlowFunctionInstance<VariableNode> {
            override fun compute(fact: VariableNode): Collection<VariableNode> {
                val value = fact.value
                return if (fact == VariableNode.ZERO || value in platform.flowGraph(callStatement.owner).locals) // TODO: optimize
                    listOf(fact)
                else
                    listOf()
            }

            override fun computeBackward(fact: VariableNode): Collection<VariableNode> {
                // these are symmetric
                return compute(fact)
            }
        }
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance<VariableNode> {
        val nonId = mutableMapOf<VariableNode, MutableList<VariableNode>>()

        platform.flowGraph(callStatement.owner).locals.forEach {
            nonId[VariableNode(it)] = mutableListOf()
        }

        if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
            exitStatement.returnValue?.let {
                nonId.getOrDefault(VariableNode(it), mutableListOf()).add(VariableNode(callStatement.lhv))
            }
        }
        return IdLikeFlowFunction(nonId)
    }
}

class Example {

    var flag = false
    var counter = 0

    fun f(x: Int): Int {
        try {
            return secretFun(x)
        } catch (e: RuntimeException) {
            flag = true
        } finally {
            counter += 1
        }
        return 0
    }

    private fun secretFun(x: Int): Int {
        return sqrt(x.toDouble()).toInt()
    }
}

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    @Test
    fun `analyse something`() {
//        val graph = runBlocking {
//            JcApplicationGraphImpl(cp, cp.usagesExt())
//        }
//      graph.callees(graph.successors(graph.successors(graph.successors(graph.entryPoint(graph.classpath.findClassOrNull("org.jacodb.analysis.impl.IFDSMainKt").methods.find {it.name.equals("main")}!!).toList().single()).single()).single()).last()!!)
//      cp.execute(object: JcClassProcessingTask{
//            override fun process(clazz: JcClassOrInterface) {
//
//            }
//        })
//doRun<D>()
        val methodCFG = cp.findClass<Example>().declaredMethods.single { it.name == "f" }.flowGraph()
        println(methodCFG)
        println(methodCFG.toString())
    }


    data class IfdsResult<D>(val pathEdges: List<Edge<D>>, val summaryEdge: List<Edge<D>>, val resultFacts: Map<JcInst, Set<D>>)

    fun <D> doRun(startMethod: JcMethod, emptyDataFact: D, flowSpace: FlowFunctionsSpace<JcMethod, JcInst, D>, graph: ApplicationGraph<JcMethod, JcInst>): IfdsResult<D> = runBlocking {
        val entryPoints = graph.entryPoint(startMethod)
        val pathEdges = mutableListOf<Edge<D>>()
        val workList: Queue<Edge<D>> = LinkedList()
        for(entryPoint in entryPoints) {
            val startV = Vertex(entryPoint, emptyDataFact)
            val startE = Edge(startV, startV)
            pathEdges.add(startE)
            workList.add(startE)
        }
        val summaryEdges = mutableListOf<Edge<D>>()
        val occuredMethods = mutableSetOf<JcMethod>()

        fun propagate(e: Edge<D>) {
            if (e !in pathEdges) {
                pathEdges.add(e)
                workList.add(e)
            }
        }

        while(!workList.isEmpty()) {
            val (u, v) = workList.peek()
            val (sp, d1) = u
            val (n, d2) = v

            occuredMethods.add(graph.methodOf(sp))
            occuredMethods.add(graph.methodOf(n))

            val callees = graph.callees(n).toList()
            // 13
            if (callees.isNotEmpty()) {
                //14
                for (calledProc in callees) {
                    val flowFunction = flowSpace.obtainCallToStartFlowFunction(n, calledProc)
                    val nextFacts = flowFunction.compute(d2)
                    for (sCalledProc in graph.entryPoint(calledProc)) {
                        for (d3 in nextFacts) {
                            //15
                            val sCalledProcWithD3 = Vertex(sCalledProc, d3)
                            val nextEdge = Edge(sCalledProcWithD3, sCalledProcWithD3)
                            propagate(nextEdge)
                        }
                    }
                }
                //17-18
                val returnSitesOfN = graph.successors(n)
                for (returnSite in returnSitesOfN) {
                    val flowFunction = flowSpace.obtainCallToReturnFlowFunction(n, returnSite)
                    val nextFacts = flowFunction.compute(d2)
                    for (d3 in nextFacts) {
                        val returnSiteVertex = Vertex(returnSite, d3)
                        val nextEdge = Edge(u, returnSiteVertex)
                        propagate(nextEdge)
                    }
                }
                //17-18 for summary edges
                for (summaryEdge in summaryEdges) {
                    if (summaryEdge.u == v) {
                        propagate(summaryEdge)
                    }
                }
            } else {
                // 21-22
                val nMethod = graph.methodOf(n)
                val nMethodExitPoints = graph.exitPoints(nMethod).toList()
                if (n in nMethodExitPoints) {
                    @Suppress("UnnecessaryVariable") val ep = n
                    val callers = graph.callers(nMethod)
                    for (caller in callers) {
                        // todo think
                        val callToStartEdgeFlowFunction = flowSpace.obtainCallToStartFlowFunction(caller, nMethod)
                        val d4Set = callToStartEdgeFlowFunction.computeBackward(d1)
                        for (d4 in d4Set) {
                            val returnSitesOfCallers = graph.successors(caller)
                            for (returnSiteOfCaller in returnSitesOfCallers) {
                                val exitToReturnFlowFunction = flowSpace.obtainExitToReturnSiteFlowFunction(caller, returnSiteOfCaller, ep)
                                val d5Set = exitToReturnFlowFunction.compute(d2)
                                for (d5 in d5Set) {
                                    val newSummaryEdge = Edge(Vertex(caller, d4), Vertex(returnSiteOfCaller, d5))
                                    if (newSummaryEdge !in summaryEdges) {
                                        summaryEdges.add(newSummaryEdge)
                                        for (pathEdge in pathEdges) {
                                            if (pathEdge.v == newSummaryEdge.u) {
                                                val newPathEdge = Edge(pathEdge.u, newSummaryEdge.v)
                                                propagate(newPathEdge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val nextInstrs = graph.successors(n)
                    for (m in nextInstrs) {
                        val flowFunction = flowSpace.obtainSequentFlowFunction(n, m)
                        val d3Set = flowFunction.compute(d2)
                        for (d3 in d3Set) {
                            val newEdge = Edge(u, Vertex(m, d3))
                            propagate(newEdge)
                        }
                    }
                }
            }
        }

        // end of the algo - we should now for each instruction which domain facts holds at it
        // based on the facts we can create analyses

        // we are interested in facts for each instruction

        val resultFacts = mutableMapOf<JcInst, Set<D>>()

        for (method in occuredMethods) {
            for (entryPoint in graph.entryPoint(method)) {
                for (inst in method.instList) {
                    val availableFacts = mutableSetOf<D>()



                    resultFacts[inst] = availableFacts
                }
            }
        }
        return@runBlocking IfdsResult(pathEdges, summaryEdges, resultFacts)
    }
}