package org.jacodb.analysis.alias

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.jacodb.analysis.alias.apg.AccessGraph
import org.jacodb.analysis.alias.apg.accessGraphOf
import org.jacodb.analysis.alias.apg.appendTail
import org.jacodb.analysis.engine.SingletonUnitResolver
import org.jacodb.analysis.graph.newApplicationGraphForAnalysis
import org.jacodb.api.JcMethod
import org.jacodb.api.JcRefType
import org.jacodb.api.cfg.JcArrayAccess
import org.jacodb.api.cfg.JcAssignInst
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstList
import org.jacodb.api.cfg.JcStringConstant
import org.jacodb.api.cfg.JcValue
import org.jacodb.api.cfg.LocalResolver
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.findClass
import org.jacodb.api.ext.findFieldOrNull
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.testing.BaseTest
import org.jacodb.testing.WithDB
import org.jacodb.testing.analysis.alias.internal.TestUtil
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

private val logger = KotlinLogging.logger {}

abstract class BaseAliasTest(
    private val declaringClass: KClass<*>
) : BaseTest() {
    companion object : WithDB(Usages, InMemoryHierarchy)

    protected fun testMethod(method: KFunction<*>): List<DynamicTest> {
        val jcClass = cp.findClass(requireNotNull(declaringClass.qualifiedName))
        val jcMethod = jcClass.declaredMethods.first { it.name == method.name }
        logger.info { "Found ${jcMethod.name}:" }

        val instList = jcMethod.instList

        logger.info { instList.joinToString(prefix = "\n\t", separator = "\n\t") }

        val queries = findQueries(jcMethod)
        return queries.map { makeDynamicTest(jcMethod, instList, it) }
    }

    private fun findQueries(method: JcMethod): List<JcInst> {
        val instList = method.instList
        val targetInstructions = instList.filter { it.callExpr?.method?.name == TestUtil::check.name }

        return targetInstructions
    }

    private fun makeDynamicTest(method: JcMethod, instList: JcInstList<JcInst>, queryInst: JcInst) =
        DynamicTest.dynamicTest("Query: $queryInst") {
            checkAliasQuery(method, instList, queryInst)
        }


    private fun checkAliasQuery(
        method: JcMethod,
        instList: JcInstList<JcInst>,
        queryInst: JcInst
    ) {
        val arguments = requireNotNull(queryInst.callExpr?.args)
        require(arguments.size == 4)

        val (apgValue, mayAliasesValue, mustNotAliasesValue, allocationSitesValue) = arguments

        val apg = apgValue.toApg(instList)

        val (aliases, allocationSites) = launchAnalysis(method, queryInst, apg)

        for (mayAlias in findApgsArray(instList, mayAliasesValue)) {
            assertTrue(mayAlias in aliases) {
                "Can't find may alias $mayAlias in\n${aliases.joinToString("\n")}"
            }
        }

        for (mustNotAlias in findApgsArray(instList, mustNotAliasesValue)) {
            assertTrue(mustNotAlias !in aliases) {
                "Fount mast not alias $mustNotAlias in\n${aliases.joinToString("\n")}"
            }
        }

        // TODO: check allocation sites as well
    }


    private fun launchAnalysis(method: JcMethod, queryInst: JcInst, apg: AccessGraph): AliasResult {
        val graph = runBlocking {
            cp.newApplicationGraphForAnalysis()
        }

        val unitResolver = SingletonUnitResolver
        val manager = AliasManager(graph, unitResolver)
        val result = manager.query(method, queryInst, apg)
        return result

    }

    private fun findApgsArray(instList: JcInstList<JcInst>, array: JcValue): List<AccessGraph> =
        instList.mapNotNull { inst ->
            if (inst !is JcAssignInst) return@mapNotNull null
            val lhv = inst.lhv
            if (lhv !is JcArrayAccess) {
                return@mapNotNull null
            }
            if (lhv.array != array) {
                return@mapNotNull null
            }
            inst.rhv.toApg(instList)
        }

    private fun JcExpr.toApg(instList: JcInstList<JcInst>) = buildAccessGraph(
        instList,
        (this as JcStringConstant).value
    )


    private fun buildAccessGraph(instList: JcInstList<JcInst>, accessPath: String): AccessGraph {
        val localResolver = LocalResolver()
        instList.forEach { it.accept(localResolver) }
        val tokens = accessPath.split(".")
        val variableName = tokens[0]
        val local = localResolver.result.first { it.name == variableName }
        val accessGraph = tokens
            .drop(1)
            .fold(accessGraphOf(local)) { ag, fieldName ->
                val field = (local.type as JcRefType).jcClass.findFieldOrNull(fieldName)!!
                ag.appendTail(field)
            }
        return accessGraph
    }
}