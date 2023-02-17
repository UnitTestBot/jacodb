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
import org.jacodb.analysis.examples.Example
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisPlatform
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcArrayAccess
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcBinaryExpr
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcConstant
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLengthExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcNegExpr
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNewArrayExpr
import org.jacodb.api.cfg.JcNewExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcThis
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.cfg.fieldRef
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.isAccessibleFrom
import org.jacodb.api.ext.isNullable
import org.jacodb.api.ext.packageName
import org.jacodb.impl.analysis.JcAnalysisPlatformImpl
import org.jacodb.impl.analysis.features.JcCacheGraphFeature
import org.jacodb.impl.analysis.locals
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.SyncUsagesExtension
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.features.usagesExt
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform
): FlowFunctionsSpace<JcMethod, JcInst, VariableNode> {

    // todo: think about name shadowing
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
                is JcArrayAccess -> Unit // TODO: do smth real here
                is JcNewExpr, is JcNewArrayExpr, is JcConstant, is JcBinaryExpr, is JcLengthExpr, is JcNegExpr, is JcThis -> Unit
                is JcFieldRef -> Unit// TODO: do smth real here
                is JcCallExpr -> {
                    // todo: this is workaround for non-analyzable methods, do smth cooler here
                    if (rhv.method.method.isNullable != false && rhv.method.name != "iterator")
                        nonId[VariableNode.ZERO] = listOf(VariableNode.ZERO, VariableNode.fromValue(current.lhv))
                }
                is JcCastExpr -> {
                    when (val operand = rhv.operand) {
                        is JcLocal -> nonId[VariableNode.fromLocal(operand)] = setOf(VariableNode.fromLocal(operand), VariableNode.fromValue(current.lhv)).toList()
                        is JcNullConstant -> nonId[VariableNode.ZERO] = listOf(VariableNode.ZERO, VariableNode.fromValue(current.lhv))
                        else -> Unit
                    }
                }
                else -> TODO()
            }
        }
        if (current is JcIfInst) {
            val expr = current.condition
            var comparedValue: JcValue? = null

            if (expr.rhv is JcNullConstant && expr.lhv is JcLocal)
                comparedValue = expr.lhv
            else if (expr.lhv is JcNullConstant && expr.rhv is JcLocal)
                comparedValue = expr.rhv

            val currentBranch = graph.methodOf(current).flowGraph().ref(next)
            if (comparedValue != null) {
                when (expr) {
                    is JcEqExpr -> {
                        comparedValue.let {
                            if (currentBranch == current.trueBranch) {
                                nonId[VariableNode.ZERO] = listOf(VariableNode.ZERO, VariableNode.fromValue(comparedValue))
                            } else if (currentBranch == current.falseBranch) {
                                nonId[VariableNode.fromValue(comparedValue)] = emptyList()
                            }
                        }
                    }
                    is JcNeqExpr -> {
                        comparedValue.let {
                            if (currentBranch == current.falseBranch) {
                                nonId[VariableNode.ZERO] = listOf(VariableNode.ZERO, VariableNode.fromValue(comparedValue))
                            } else if (currentBranch == current.trueBranch) {
                                nonId[VariableNode.fromValue(comparedValue)] = emptyList()
                            }
                        }
                    }
                    else -> Unit
                }
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

        nonId[VariableNode.ZERO] = callee.domain
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
                is JcConstant, is JcThis -> Unit
                is JcFieldRef -> Unit // TODO: do something smarter here
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
        exitStatement.domain.forEach {
            nonId[it] = emptyList()
        }

        // todo: pass everything related to `this` back to caller

        if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
            // Propagate results back to caller in case of assignment
            when (val value = exitStatement.returnValue) {
                is JcNullConstant -> nonId[VariableNode.ZERO] = listOf(VariableNode.ZERO, VariableNode.fromValue(callStatement.lhv))
                is JcLocal -> nonId[VariableNode.fromLocal(value)] = listOf(VariableNode.fromValue(callStatement.lhv))
                is JcConstant, is JcThis -> Unit
                is JcFieldRef -> Unit // TODO: do something smarter here
                else -> TODO()
            }
        }
        return IdLikeFlowFunction(callStatement.domain, nonId)
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

    @Suppress("WARNINGS")
    class NPEExamples {

        interface SomeI {
            fun functionThatCanThrowNPEOnNull(x: String?)
            fun functionThatCanNotThrowNPEOnNull(x: String?)
        }

        class SomeImpl: SomeI {
            override fun functionThatCanThrowNPEOnNull(x: String?) = println(x!!.length)
            override fun functionThatCanNotThrowNPEOnNull(x: String?) = println(x)
        }

        private fun constNull(y: String?): String? = null

        private fun id(x: String?): String? = x

        private fun twoExits(x: String?): String? {
            if (x != null && x.startsWith("239"))
                return x
            return null
        }

        fun npeOnLength(): Int {
            var x: String? = "abc"
            var y: String? = "def"
            x = constNull(y)
            return x!!.length
        }

        fun noNPE(): Int {
            var x: String? = null
            var y: String? = "def"
            x = id(y)
            return x!!.length
        }

        fun npeAfterTwoExits(): Int {
            var x: String? = null
            var y: String? = "abc"
            x = twoExits(x)
            y = twoExits(y)
            return x!!.length + y!!.length
        }

        fun checkedAccess(x: String?): Int {
            if (x != null) {
                return x.length
            }
            return -1
        }

        fun possibleNPEOnVirtualCall(x: SomeI, y: String) {
            x.functionThatCanThrowNPEOnNull(y)
        }

        fun noNPEOnVirtualCall(x: SomeI, y: String) {
            x.functionThatCanNotThrowNPEOnNull(y)
        }
    }

    @Test
    fun `fields resolving should work through interfaces`() = runBlocking {
        val graph = JcApplicationGraphImpl(cp, cp.usagesExt())
        val callers = graph.callers(cp.findClass<StringTokenizer>().constructors[2])
        println(callers.toList().size)
    }
    
    @Test
    fun `analyse something`() {
        val graph = runBlocking {
            SimplifiedJcApplicationGraph(cp, cp.usagesExt())
        }
        val devirtualizer = AllOverridesDevirtualizer(graph, cp)
//      graph.callees(graph.successors(graph.successors(graph.successors(graph.entryPoint(graph.classpath.findClassOrNull("org.jacodb.analysis.impl.IFDSMainKt").methods.find {it.name.equals("main")}!!).toList().single()).single()).single()).last()!!)
//      cp.execute(object: JcClassProcessingTask{
//            override fun process(clazz: JcClassOrInterface) {
//
//            }
//        })
        val testingMethod = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "npeOnLength" }
        val results = doRun(testingMethod, NPEFlowFunctions(cp, graph, graph), graph, devirtualizer)
        print(results)
    }

    @Test
    fun `analyze simple NPE`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "npeOnLength" }
        val actual = findNPEInstructions(method)

        // todo: think about better assertions here
        assertEquals(
            listOf("%3 = %2.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `analyze no NPE`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "noNPE" }
        val actual = findNPEInstructions(method)

        assertEquals(emptyList<NPELocation>(), actual)
    }

    @Test
    fun `analyze NPE after fun with two exits`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "npeAfterTwoExits" }
        val actual = findNPEInstructions(method)

        assertEquals(
            listOf("%4 = %2.length()", "%5 = %3.length()"),
            actual.map { it.inst.toString() }
        )
    }

    @Test
    fun `no NPE after checked access`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "checkedAccess" }
        val actual = findNPEInstructions(method)

        assertEquals(emptyList<NPELocation>(), actual)
    }

    @Test
    fun `npe on virtual call when possible`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "possibleNPEOnVirtualCall" }
        val actual = findNPEInstructions(method)

        assertEquals(2, actual.size) // TODO: should be only one NPE, false positive due to kotlin not-null
    }

    @Test
    fun `no npe on virtual call when impossible`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "noNPEOnVirtualCall" }
        val actual = findNPEInstructions(method)

        assertEquals(1, actual.size)
    }

    @Test
    fun `speed test`() {
        val method = cp.findClass<Example>().declaredMethods.single { it.name == "main" }
        val actual = findNPEInstructions(method)

        assertEquals(10, actual.size)
    }

    data class NPELocation(val inst: JcInst, val value: JcValue, val possibleStackTrace: List<JcInst>)

    fun findNPEInstructions(method: JcMethod): List<NPELocation> {
        val graph = runBlocking {
            SimplifiedJcApplicationGraph(cp, cp.usagesExt())
        }
        val devirtualizer = AllOverridesDevirtualizer(graph, cp)
        val ifdsResults = doRun(method, NPEFlowFunctions(cp, graph, graph), graph, devirtualizer)
        val possibleNPEInstructions = mutableListOf<NPELocation>()
        ifdsResults.resultFacts.forEach { (instruction, facts) ->
            val callExpr = instruction.callExpr
            if (callExpr is JcInstanceCallExpr && VariableNode.fromValue(callExpr.instance) in facts) {
                val possibleStackTrace = ifdsResults.resolvePossibleStackTrace(
                    Vertex(instruction, VariableNode.fromValue(callExpr.instance)), method
                )
                possibleNPEInstructions.add(NPELocation(instruction, callExpr.instance, possibleStackTrace))
            }

            // todo: check for JcLengthExpr and JcArrayAccess

            val fieldRef = instruction.fieldRef
            if (fieldRef is JcFieldRef) {
                fieldRef.instance?.let {
                    if (VariableNode.fromValue(it) in facts) {
                        val possibleStackTrace = ifdsResults.resolvePossibleStackTrace(
                            Vertex(instruction, VariableNode.fromValue(it)), method
                        )
                        possibleNPEInstructions.add(NPELocation(instruction, it, possibleStackTrace))
                    }
                }
            }
        }
        return possibleNPEInstructions
    }

    interface Devirtualizer<Method, Statement> {
        /**
         * Accepts source of the code and sink, which is expected to be virtual call
         * Should return all methods that could be called by sink.
         */
        fun findPossibleCallees(sources: List<Statement>, sink: Statement): Collection<Method>
    }

    class AllOverridesDevirtualizer(private val initialGraph: ApplicationGraph<JcMethod, JcInst>, classpath: JcClasspath) : Devirtualizer<JcMethod, JcInst> {
        private val hierarchyExtension = runBlocking {
            classpath.hierarchyExt()
        }

        override fun findPossibleCallees(sources: List<JcInst>, sink: JcInst): Collection<JcMethod> {
            val methods = initialGraph.callees(sink).toList()
            return methods
                .filter { it.enclosingClass.isAccessibleFrom(sink.location.method.enclosingClass.packageName) }
                .flatMap {
                    hierarchyExtension.findOverrides(it).toList() + listOf(it)
                }
        }
    }

    data class IfdsResult<D>(
        val pathEdges: List<Edge<D>>,
        val summaryEdge: List<Edge<D>>,
        val resultFacts: Map<JcInst, Set<D>>,
        val callToStartEdges: List<Edge<D>>,
    ) {
        fun resolvePossibleStackTrace(vertex: Vertex<D>, startMethod: JcMethod): List<JcInst> {
            val result = mutableListOf(vertex.statement)
            var curVertex = vertex
            while (curVertex.statement.location.method != startMethod) {
                // TODO: Note that taking not first element may cause to infinite loop in this implementation
                val startVertex = pathEdges.first { it.v == curVertex }.u
                curVertex = callToStartEdges.first { it.v == startVertex }.u
                result.add(curVertex.statement)
            }
            return result.reversed()
        }
    }

    fun <D> doRun(
        startMethod: JcMethod,
        flowSpace: FlowFunctionsSpace<JcMethod, JcInst, D>,
        graph: ApplicationGraph<JcMethod, JcInst>,
        devirtualizer: Devirtualizer<JcMethod, JcInst>?
    ): IfdsResult<D> = runBlocking {
        val entryPoints = graph.entryPoint(startMethod)
        val pathEdges = mutableListOf<Edge<D>>()
        val workList: Queue<Edge<D>> = LinkedList()
        val callToStartEdges = mutableListOf<Edge<D>>()

        for(entryPoint in entryPoints) {
            for (fact in flowSpace.obtainStartFacts(entryPoint)) {
                val startV = Vertex(entryPoint, fact)
                val startE = Edge(startV, startV)
                pathEdges.add(startE)
                workList.add(startE)
            }
        }
        val summaryEdges = mutableListOf<Edge<D>>()

        fun propagate(e: Edge<D>): Boolean {
            if (e !in pathEdges) {
                pathEdges.add(e)
                workList.add(e)
                return true
            }
            return false
        }

        while(!workList.isEmpty()) {
            val (u, v) = workList.poll()
            val (sp, d1) = u
            val (n, d2) = v

            val callees = devirtualizer?.findPossibleCallees(graph.entryPoint(startMethod).toList(), n)?.toList() ?: graph.callees(n).toList()
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
                            if (propagate(nextEdge)) {
                                callToStartEdges.add(Edge(v, sCalledProcWithD3))
                            }
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
                        val newPathEdge = Edge(u, summaryEdge.v)
                        propagate(newPathEdge)
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

        // 6-8
        // todo: think about optimizations when we don't need all facts
        for (pathEdge in pathEdges) {
            //val method = pathEdge.u.statement.location.method
            resultFacts.getOrPut(pathEdge.v.statement) { mutableSetOf() }.add(pathEdge.v.domainFact)
        }
        return@runBlocking IfdsResult(pathEdges, summaryEdges, resultFacts, callToStartEdges)
    }
}