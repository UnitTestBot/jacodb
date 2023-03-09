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

import NPEExamples
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.impl.AnalysisTest.AllOverridesDevirtualizer.Companion.bannedPackagePrefixes
import org.jacodb.analysis.impl.SimplifiedJcApplicationGraph.Companion.bannedPackagePrefixes
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisPlatform
import org.jacodb.api.cfg.DefaultJcExprVisitor
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcCallExpr
import org.jacodb.api.cfg.JcCastExpr
import org.jacodb.api.cfg.JcDynamicCallExpr
import org.jacodb.api.cfg.JcEqExpr
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcFieldRef
import org.jacodb.api.cfg.JcIfInst
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstanceCallExpr
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcNeqExpr
import org.jacodb.api.cfg.JcNullConstant
import org.jacodb.api.cfg.JcReturnInst
import org.jacodb.api.cfg.JcSpecialCallExpr
import org.jacodb.api.cfg.JcStaticCallExpr
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.cfg.fieldRef
import org.jacodb.api.ext.constructors
import org.jacodb.api.ext.findClass
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

/**
 * This class is used to represent a dataflow fact in problems where facts could be correlated with variables/values
 * (such as NPE, uninitialized variable, etc.)
 */
data class VariableNode private constructor(val value: JcValue?) {
    companion object {

        val ZERO = VariableNode(null)

        // TODO: do we really need these?
        fun fromLocal(value: JcLocal) = VariableNode(value)

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

/**
 * This is an implementation of [FlowFunctionsSpace] for NullPointerException problem based on JaCoDB CFG.
 * Here "fact D holds" denotes that "value D can be null"
 */
class NPEFlowFunctions(
    private val classpath: JcClasspath,
    private val graph: ApplicationGraph<JcMethod, JcInst>,
    private val platform: JcAnalysisPlatform
): FlowFunctionsSpace<JcMethod, JcInst, VariableNode> {

    private val factLeadingToNullVisitor = object : DefaultJcExprVisitor<VariableNode?> {
        override val defaultExprHandler: (JcExpr) -> VariableNode?
            get() = { null }

        private fun visitJcLocal(value: JcLocal) = VariableNode.fromLocal(value)

        private fun visitJcCallExpr(expr: JcCallExpr): VariableNode? {
            if (expr.method.method.isNullable == true) {
                return VariableNode.ZERO
            }
            return null
        }

        override fun visitJcArgument(value: JcArgument) = visitJcLocal(value)

        override fun visitJcLocalVar(value: JcLocalVar) = visitJcLocal(value)

        override fun visitJcNullConstant(value: JcNullConstant) = VariableNode.ZERO

        override fun visitJcCastExpr(expr: JcCastExpr) = expr.operand.accept(this)

        override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr) = visitJcCallExpr(expr)

        override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr) = visitJcCallExpr(expr)

        override fun visitJcStaticCallExpr(expr: JcStaticCallExpr) = visitJcCallExpr(expr)

        override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr) = visitJcCallExpr(expr)

        // TODO: override others
    }

    // Returns a fact, such that if it holds, `this` expr is null (or null if there is no such fact)
    private val JcExpr.factLeadingToNull: VariableNode?
        get() = accept(factLeadingToNullVisitor)

    // TODO: think about name shadowing
    // Returns all local variables and arguments referenced by this method
    private val JcMethod.domain: Set<VariableNode>
        get() {
            return platform.flowGraph(this).locals
                .map { VariableNode.fromLocal(it) }
                .toSet()
                .plus(VariableNode.ZERO)
        }

    private val JcInst.domain: Set<VariableNode>
        get() = location.method.domain

    // Returns a value that is being dereferenced in this call
    private val JcInst.dereferencedValue: JcLocal?
        get() {
            (callExpr as? JcInstanceCallExpr)?.let {
                return it.instance as? JcLocal
            }

           return fieldRef?.instance as? JcLocal
        }

    override fun obtainStartFacts(startStatement: JcInst): Collection<VariableNode> {
        return startStatement.domain.filter { it.value == null || it.value.type.nullable != false }
    }

    override fun obtainSequentFlowFunction(current: JcInst, next: JcInst): FlowFunctionInstance<VariableNode> {
        val nonId = mutableMapOf<VariableNode, List<VariableNode>>()

        current.dereferencedValue?.let {
            nonId[VariableNode.fromLocal(it)] = emptyList()
        }

        if (current is JcAssignInst) {
            nonId[VariableNode.fromValue(current.lhv)] = listOf()
            current.rhv.factLeadingToNull?.let {
                if (it.value == null || it.value != current.dereferencedValue) {
                    nonId[it] = setOf(it, VariableNode.fromValue(current.lhv)).toList()
                }
            }
        }

        // This handles cases like if (x != null) expr1 else expr2, where edges to expr1 and to expr2 should be different
        // (because x == null will be held at expr2 but won't be held at expr1)
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

        // All nullable callee's locals are initialized as null
        nonId[VariableNode.ZERO] = callee.domain
            .filterIsInstance<JcLocalVar>()
            .filter { it.type.nullable != false }
            .map { VariableNode.fromLocal(it) }
            .plus(VariableNode.ZERO)
            .toMutableList()

        // Propagate values passed to callee as parameters
        params.zip(args).forEach { (param, arg) ->
            arg.factLeadingToNull?.let {
                nonId.getValue(it).add(VariableNode.fromLocal(param))
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
            // Nullability of lhs of assignment will be handled by exit-to-return flow function
            nonId[VariableNode.fromValue(callStatement.lhv)] = emptyList()
        }
        callStatement.dereferencedValue?.let {
            nonId[VariableNode.fromLocal(it)] = emptyList()
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

        // TODO: pass everything related to `this` back to caller

        if (callStatement is JcAssignInst && exitStatement is JcReturnInst) {
            // Propagate results back to caller in case of assignment
            exitStatement.returnValue?.factLeadingToNull?.let {
                nonId[it] = listOf(VariableNode.fromValue(callStatement.lhv))
            }
        }
        return IdLikeFlowFunction(exitStatement.domain, nonId)
    }
}

/**
 * Simplification of JcApplicationGraph that ignores method calls matching [bannedPackagePrefixes]
 */
class SimplifiedJcApplicationGraph(
    override val classpath: JcClasspath,
    usages: SyncUsagesExtension,
    cacheSize: Long = 10_000,
) : JcAnalysisPlatformImpl(classpath, listOf(JcCacheGraphFeature(cacheSize))), ApplicationGraph<JcMethod, JcInst> {
    private val impl = JcApplicationGraphImpl(classpath, usages, cacheSize)

    override fun predecessors(node: JcInst): Sequence<JcInst> = impl.predecessors(node)
    override fun successors(node: JcInst): Sequence<JcInst> = impl.successors(node)
    override fun callees(node: JcInst): Sequence<JcMethod> = impl.callees(node).filterNot { callee ->
        bannedPackagePrefixes.any { callee.enclosingClass.packageName.startsWith(it) }
    }
    override fun callers(method: JcMethod): Sequence<JcInst> = impl.callers(method)
    override fun entryPoint(method: JcMethod): Sequence<JcInst> = impl.entryPoint(method)
    override fun exitPoints(method: JcMethod): Sequence<JcInst> = impl.exitPoints(method)
    override fun methodOf(node: JcInst): JcMethod = impl.methodOf(node)

    companion object {
        private val bannedPackagePrefixes = listOf(
            "kotlin.",
            "java."
        )
    }

}

class AnalysisTest : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

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

        // TODO: think about better assertions here
        assertEquals(
            setOf("%3 = %2.length()"),
            actual.map { it.inst.toString() }.toSet()
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
            setOf("%4 = %2.length()", "%5 = %3.length()"),
            actual.map { it.inst.toString() }.toSet()
        )
    }

    @Test
    fun `no NPE after checked access`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "checkedAccess" }
        val actual = findNPEInstructions(method)

        assertEquals(emptyList<NPELocation>(), actual)
    }

    @Test
    fun `consecutive NPEs handled properly`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "consecutiveNPEs" }
        val actual = findNPEInstructions(method)

        assertEquals(
            setOf("%2 = arg$0.length()", "%4 = arg$0.length()"),
            actual.map { it.inst.toString() }.toSet()
        )
    }

    @Test
    fun `npe on virtual call when possible`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "possibleNPEOnVirtualCall" }
        val actual = findNPEInstructions(method)

        // TODO: one false-positive here due to not-parsed @NotNull annotation
        assertEquals(2, actual.size)
    }

    @Test
    fun `no npe on virtual call when impossible`() {
        val method = cp.findClass<NPEExamples>().declaredMethods.single { it.name == "noNPEOnVirtualCall" }
        val actual = findNPEInstructions(method)

        // TODO: false-positive here due to not-parsed @NotNull annotation
        assertEquals(1, actual.size)
    }

    data class NPELocation(val inst: JcInst, val value: JcValue, val possibleStackTrace: List<JcInst>)

    /**
     * The method finds all places where NPE may occur
     */
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

            // TODO: check for JcLengthExpr and JcArrayAccess

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

    /**
     * Simple devirtualizer that substitutes method with all ov its overrides, but no more then [limit].
     * Also, it doesn't devirtualize methods matching [bannedPackagePrefixes]
     */
    class AllOverridesDevirtualizer(
        private val initialGraph: ApplicationGraph<JcMethod, JcInst>,
        private val classpath: JcClasspath,
        private val limit: Int = 3
    ) : Devirtualizer<JcMethod, JcInst> {
        private val hierarchyExtension = runBlocking {
            classpath.hierarchyExt()
        }

        override fun findPossibleCallees(sink: JcInst): Collection<JcMethod> {
            val methods = initialGraph.callees(sink).toList()
            if (sink.callExpr !is JcVirtualCallExpr)
                return methods
            return methods
                .flatMap { method ->
                    if (bannedPackagePrefixes.any { method.enclosingClass.packageName.startsWith(it) })
                        listOf(method)
                    else {
                        hierarchyExtension
                            .findOverrides(method) // TODO: maybe filter inaccessible methods here?
                            .take(limit - 1)
                            .toList() + listOf(method)
                    }
                }
        }

        companion object {
            private val bannedPackagePrefixes = listOf(
                "sun.",
                "jdk.internal",
                "java.",
                "kotlin."
            )
        }
    }

    fun <D> doRun(
        startMethod: JcMethod,
        flowSpace: FlowFunctionsSpace<JcMethod, JcInst, D>,
        graph: ApplicationGraph<JcMethod, JcInst>,
        devirtualizer: Devirtualizer<JcMethod, JcInst>?
    ): IFDSResult<JcMethod, JcInst, D> = runBlocking {
        val ifdsInstance = IFDSInstance(graph, flowSpace, devirtualizer)
        ifdsInstance.addStart(startMethod)
        ifdsInstance.run()
        return@runBlocking ifdsInstance.collectResults()
    }
}