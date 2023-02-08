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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.utbot.jacodb.api.JcClasspath
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.analysis.ApplicationGraph
import org.utbot.jacodb.api.analysis.JcAnalysisPlatform
import org.utbot.jacodb.api.cfg.JcArgument
import org.utbot.jacodb.api.cfg.JcAssignInst
import org.utbot.jacodb.api.cfg.JcCallExpr
import org.utbot.jacodb.api.cfg.JcConstant
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.cfg.JcInstanceCallExpr
import org.utbot.jacodb.api.cfg.JcLocal
import org.utbot.jacodb.api.cfg.JcLocalVar
import org.utbot.jacodb.api.cfg.JcNewArrayExpr
import org.utbot.jacodb.api.cfg.JcNewExpr
import org.utbot.jacodb.api.cfg.JcNullConstant
import org.utbot.jacodb.api.cfg.JcReturnInst
import org.utbot.jacodb.api.cfg.JcValue
import org.utbot.jacodb.api.ext.cfg.callExpr
import org.utbot.jacodb.api.ext.findClass
import org.utbot.jacodb.api.ext.packageName
import org.utbot.jacodb.impl.analysis.JcAnalysisPlatformImpl
import org.utbot.jacodb.impl.analysis.features.JcCacheGraphFeature
import org.utbot.jacodb.impl.analysis.locals
import org.utbot.jacodb.impl.features.InMemoryHierarchy
import org.utbot.jacodb.impl.features.SyncUsagesExtension
import org.utbot.jacodb.impl.features.Usages
import org.utbot.jacodb.impl.features.usagesExt
import java.util.*

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
    fun obtainStartFacts(startStatement: Statement): Collection<D>
    fun obtainSequentFlowFunction(current: Statement, next: Statement): FlowFunctionInstance<D>
    fun obtainCallToStartFlowFunction(callStatement: Statement, callee: Method): FlowFunctionInstance<D>
    fun obtainCallToReturnFlowFunction(callStatement: Statement, returnSite: Statement): FlowFunctionInstance<D>
    fun obtainExitToReturnSiteFlowFunction(callStatement: Statement, returnSite: Statement, exitStatement: Statement): FlowFunctionInstance<D>
}

/**
 * This class is used to represent a dataflow fact in problems where facts could be correlated with variables/values
 * (such as NPE, uninitialized variable, etc.)
 */
data class VariableNode private constructor(val value: JcValue?) {
    companion object {

        val ZERO = VariableNode(null)

        fun fromLocal(value: JcLocal) = VariableNode(value)

        // todo: do we really want this?
        fun fromValue(value: JcValue) = VariableNode(value)
    }
}

/**
 * Flow function which is equal to id for all elements from [domain] except those in [nonId], for which the result is stored in the map
 */
class IdLikeFlowFunction<D>(
    private val domain: Set<D>,
    private val nonId: Map<D, Collection<D>>
): FlowFunctionInstance<D> {
    override fun compute(fact: D): Collection<D> {
        nonId[fact]?.let {
            return it
        }
        return if (domain.contains(fact)) listOf(fact) else emptyList()
    }

    override fun computeBackward(fact: D): Collection<D> {
        val res = if (domain.contains(fact) && !nonId.containsKey(fact)) {
            listOf(fact)
        } else {
            emptyList()
        }
        return res + nonId.entries.filter { (_, value) -> value.contains(fact) }.map { it.key }
    }
}

class NPEFlowFunctions(
    private val classpath: JcClasspath,
    private val platform: JcAnalysisPlatform
): FlowFunctionsSpace<JcMethod, JcInst, VariableNode> {

    private val JcMethod.domain: Set<VariableNode>
        get() {
            return platform.flowGraph(this).locals
                .map { VariableNode.fromLocal(it) }
                .toSet()
                .plus(VariableNode.ZERO)
        }

    private val JcInst.domain: Set<VariableNode>
        get() = location.method.domain

    override fun obtainStartFacts(startStatement: JcInst): Collection<VariableNode> {
        return startStatement.domain
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<VariableNode> {
        val nonId = mutableMapOf<VariableNode, List<VariableNode>>()
        if (current is JcAssignInst) {
            nonId[VariableNode.fromValue(current.lhv)] = listOf()
            when (val rhv = current.rhv) {
                is JcLocal -> nonId[VariableNode.fromLocal(rhv)] = setOf(VariableNode.fromLocal(rhv), VariableNode.fromValue(current.lhv)).toList()
                is JcNullConstant -> nonId[VariableNode.ZERO] = listOf(VariableNode.ZERO, VariableNode.fromValue(current.lhv))
                is JcNewExpr, is JcNewArrayExpr, is JcConstant, is JcCallExpr -> Unit
                else -> TODO()
            }
        }
        return IdLikeFlowFunction(current.domain, nonId)
    }

    override fun obtainCallToStartFlowFunction(
        callStatement: JcInst,
        callee: JcMethod
    ): FlowFunctionInstance<VariableNode> {
        val nonId = mutableMapOf<VariableNode, MutableList<VariableNode>>()
        val callExpr = callStatement.callExpr ?: error("Call statement should have non-null callExpr")
        val args = callExpr.args
        val params = callee.parameters.map {
            JcArgument.of(it.index, it.name, classpath.findTypeOrNull(it.type.typeName)!!)
        }


        // We don't propagate locals to the callee
        platform.flowGraph(callStatement.location.method).locals.forEach {
            nonId[VariableNode.fromLocal(it)] = mutableListOf()
        }

        nonId[VariableNode.ZERO] = platform.flowGraph(callee).locals
            .filterIsInstance<JcLocalVar>()
            .filter { it.type.nullable != false }
            .map { VariableNode.fromLocal(it) }
            .plus(VariableNode.ZERO)
            .toMutableList()

        // Propagate values passed to callee as parameters
        params.zip(args).forEach { (param, arg) ->
            when (arg) {
                is JcLocal -> nonId.getValue(VariableNode.fromLocal(arg)).add(VariableNode.fromLocal(param))
                is JcNullConstant -> nonId.getValue(VariableNode.ZERO).add(VariableNode.fromLocal(param))
                else -> TODO()
            }
        }

        // todo: pass everything related to `this` if this is JcInstanceCallExpr

        return IdLikeFlowFunction(callStatement.domain, nonId)
    }

    override fun obtainCallToReturnFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst
    ): FlowFunctionInstance<VariableNode> {
        val nonId = mutableMapOf<VariableNode, List<VariableNode>>()
        if (callStatement is JcAssignInst) {
            nonId[VariableNode.fromValue(callStatement.lhv)] = emptyList()
        }
        return IdLikeFlowFunction(callStatement.domain, nonId)
    }

    override fun obtainExitToReturnSiteFlowFunction(
        callStatement: JcInst,
        returnSite: JcInst,
        exitStatement: JcInst
    ): FlowFunctionInstance<VariableNode> {
        val nonId = mutableMapOf<VariableNode, List<VariableNode>>()

        // We shouldn't propagate locals back to caller
        platform.flowGraph(callStatement.location.method).locals.forEach {
            nonId[VariableNode.fromLocal(it)] = emptyList()
        }

        // todo: pass everything related to `this` back to caller

        if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
            // Propagate results back to caller in case of assignment
            when (val value = exitStatement.returnValue) {
                is JcNullConstant -> nonId[VariableNode.ZERO] = listOf(VariableNode.ZERO, VariableNode.fromValue(callStatement.lhv))
                is JcLocal -> nonId[VariableNode.fromLocal(value)] = listOf(VariableNode.fromValue(callStatement.lhv))
                else -> TODO()
            }
        }
        return IdLikeFlowFunction(callStatement.domain, nonId)
    }
}

//class Example {
//
//    var flag = false
//    var y: String? = null
//    var counter = 0
//
//    fun f(x: Int): Int {
//        try {
//            y = "abc"
//            return secretFun(x)
//        } catch (e: RuntimeException) {
//            flag = true
//        } finally {
//            println(y)
//            counter += 1
//        }
//        return 0
//    }
//
//    private fun secretFun(x: Int): Int {
//        return sqrt(x.toDouble()).toInt()
//    }
//}

class VeryDumbExample {
    fun f(): Int {
        var x: String? = null
        var y: String? = "abc"
        x = g(y)
        return x!!.length
    }

    fun g(y: String?): String? {
        return null
        //return id(y)
    }

    fun id(x: String?): String? {
        return x
    }
}

class SimplifiedJcApplicationGraph(
    override val classpath: JcClasspath,
    usages: SyncUsagesExtension,
    cacheSize: Long = 10_000,
) : JcAnalysisPlatformImpl(classpath, listOf(JcCacheGraphFeature(cacheSize))), ApplicationGraph<JcMethod, JcInst> {
    private val impl = JcApplicationGraphImpl(classpath, usages, cacheSize)

    override fun predecessors(node: JcInst): Sequence<JcInst> = impl.predecessors(node)
    override fun successors(node: JcInst): Sequence<JcInst> = impl.successors(node)
    override fun callees(node: JcInst): Sequence<JcMethod> = impl.callees(node).filterNot {
        it.enclosingClass.packageName.startsWith("java") || it.enclosingClass.packageName.startsWith("kotlin")
    }
    override fun callers(method: JcMethod): Sequence<JcInst> = impl.callers(method)
    override fun entryPoint(method: JcMethod): Sequence<JcInst> = impl.entryPoint(method)
    override fun exitPoints(method: JcMethod): Sequence<JcInst> = impl.exitPoints(method)
    override fun methodOf(node: JcInst): JcMethod = impl.methodOf(node)

}

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    @Test
    fun `analyse something`() {
        val graph = runBlocking {
            SimplifiedJcApplicationGraph(cp, cp.usagesExt())
        }
//      graph.callees(graph.successors(graph.successors(graph.successors(graph.entryPoint(graph.classpath.findClassOrNull("org.jacodb.analysis.impl.IFDSMainKt").methods.find {it.name.equals("main")}!!).toList().single()).single()).single()).last()!!)
//      cp.execute(object: JcClassProcessingTask{
//            override fun process(clazz: JcClassOrInterface) {
//
//            }
//        })
        val testingMethod = cp.findClass<VeryDumbExample>().declaredMethods.single { it.name == "f" }
        val results = doRun(testingMethod, NPEFlowFunctions(cp, graph), graph)
        print(results)
    }

    fun findNPEInstructions(method: JcMethod): List<JcInst> {
        val graph = runBlocking {
            SimplifiedJcApplicationGraph(cp, cp.usagesExt())
        }
        val ifdsResults = doRun(method, NPEFlowFunctions(cp, graph), graph)
        val possibleNPEInstructions = mutableListOf<JcInst>()
        ifdsResults.resultFacts.forEach { (instruction, facts) ->
            val callExpr = instruction.callExpr
            if (callExpr is JcInstanceCallExpr && VariableNode.fromValue(callExpr.instance) in facts) {
                possibleNPEInstructions.add(instruction)
            }
        }
        return possibleNPEInstructions
    }

    @Test
    fun `analyze simple NPE`() {
        val method = cp.findClass<VeryDumbExample>().declaredMethods.single { it.name == "f" }
        val actual = findNPEInstructions(method)

        // todo: think about better assertions here
        assertEquals(
            listOf("%3 = %2.length()"),
            actual.map { it.toString() }
        )
    }

    data class IfdsResult<D>(val pathEdges: List<Edge<D>>, val summaryEdge: List<Edge<D>>, val resultFacts: Map<JcInst, Set<D>>)

    fun <D> doRun(startMethod: JcMethod, flowSpace: FlowFunctionsSpace<JcMethod, JcInst, D>, graph: ApplicationGraph<JcMethod, JcInst>): IfdsResult<D> = runBlocking {
        val entryPoints = graph.entryPoint(startMethod)
        val pathEdges = mutableListOf<Edge<D>>()
        val workList: Queue<Edge<D>> = LinkedList()
        for(entryPoint in entryPoints) {
            for (fact in flowSpace.obtainStartFacts(entryPoint)) {
                val startV = Vertex(entryPoint, fact)
                val startE = Edge(startV, startV)
                pathEdges.add(startE)
                workList.add(startE)
            }
        }
        val summaryEdges = mutableListOf<Edge<D>>()

        fun propagate(e: Edge<D>) {
            if (e !in pathEdges) {
                pathEdges.add(e)
                workList.add(e)
            }
        }

        while(!workList.isEmpty()) {
            val (u, v) = workList.poll()
            val (sp, d1) = u
            val (n, d2) = v

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

                                        // Can't use iterator-based loops because of possible ConcurrentModificationException
                                        var ind = 0
                                        while (ind < pathEdges.size) {
                                            val pathEdge = pathEdges[ind]
                                            if (pathEdge.v == newSummaryEdge.u) {
                                                val newPathEdge = Edge(pathEdge.u, newSummaryEdge.v)
                                                propagate(newPathEdge)
                                            }
                                            ind += 1
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

        val resultFacts = mutableMapOf<JcInst, MutableSet<D>>()

        // lines 6-8
        for (pathEdge in pathEdges) {
            //val method = pathEdge.u.statement.location.method
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }
        return@runBlocking IfdsResult(pathEdges, summaryEdges, resultFacts)
    }
}