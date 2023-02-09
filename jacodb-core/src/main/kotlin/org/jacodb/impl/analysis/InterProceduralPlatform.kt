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

@file:JvmName("InterProceduralPlatforms")
package org.jacodb.impl.analysis

import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcAnalysisFeature
import org.jacodb.api.analysis.JcInstIdentity
import org.jacodb.api.analysis.JcInterProceduralPlatform
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.impl.analysis.features.JcCacheGraphFeature
import org.jacodb.impl.features.SyncUsagesExtension
import org.jacodb.impl.features.usagesExt
import java.util.concurrent.Future

open class InterProceduralPlatform(
    classpath: JcClasspath,
    val usages: SyncUsagesExtension,
    cacheSize: Long,
    feature: List<JcAnalysisFeature>
) : JcAnalysisPlatformImpl(
    classpath,
    feature.toPersistentList() + JcCacheGraphFeature(cacheSize)
), JcInterProceduralPlatform {

    protected open val JcMethod.actualFlowGraph: JcGraph
        get() {
            return flowGraph(this)
        }

    override fun groupedCallersOf(method: JcMethod): Map<JcMethod, Set<JcInst>> {
        val callersMethod = usages.findUsages(method)
        return callersMethod.associateWith {
            it.actualFlowGraph.instructions.filter { inst ->
                inst.callExpr?.method?.method == method
            }.toSet()
        }
    }

    override fun callersOf(method: JcMethod): Sequence<JcInstIdentity> {
        val callersMethod = usages.findUsages(method)
        return callersMethod.map {
            it.actualFlowGraph.instructions.mapIndexedNotNull { index, inst ->
                val callExpr = inst.callExpr
                if (callExpr != null && callExpr.method.method == method) {
                    JcInstIdentity(it, index)
                } else {
                    null
                }
            }
        }.flatten()
    }

    override fun callInstructionIdsOf(method: JcMethod): Sequence<JcInstIdentity> {
        return method.actualFlowGraph.instructions.mapIndexedNotNull { index, inst ->
            val callExpr = inst.callExpr
            if (callExpr != null && callExpr.method.method == method) {
                JcInstIdentity(method, index)
            } else {
                null
            }
        }.asSequence()
    }

    override fun callInstructionsOf(method: JcMethod): Sequence<JcInst> {
        return method.actualFlowGraph.instructions
            .filter { inst -> inst.callExpr != null }
            .asSequence()
    }


    override fun heads(method: JcMethod): List<JcInstIdentity> {
        return listOf(method.actualFlowGraph.toIdentity { it.entry })
    }

    override fun isCall(instId: JcInstIdentity): Boolean {
        val inst = toInstruction(instId)
        return inst.callExpr != null
    }

    override fun isExit(instId: JcInstIdentity): Boolean {
        val graph = instId.method.actualFlowGraph
        return graph.exits.contains(graph.instructions[instId.index])
    }

    override fun isHead(instId: JcInstIdentity): Boolean {
        val graph = instId.method.actualFlowGraph
        return graph.entry == graph.instructions[instId.index]
    }

    override fun toInstruction(instId: JcInstIdentity): JcInst {
        return instId.method.actualFlowGraph.instructions[instId.index]
    }

    private inline fun JcGraph.toIdentity(inst: (JcGraph) -> JcInst): JcInstIdentity {
        return JcInstIdentity(this, indexOf(inst(this)))
    }
}

suspend fun JcClasspath.interProcedure(
    features: List<JcAnalysisFeature>,
    cacheSize: Long = 10_000
): InterProceduralPlatform {
    val usages = usagesExt()
    return InterProceduralPlatform(this, usages, cacheSize, features)
}

fun JcClasspath.asyncInterProcedure(
    features: List<JcAnalysisFeature>,
    cacheSize: Long = 10_000
): Future<InterProceduralPlatform> = GlobalScope.future { interProcedure(features, cacheSize) }
