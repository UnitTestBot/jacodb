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

package org.utbot.jacodb.impl.cfg.analysis

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import kotlinx.collections.immutable.toPersistentList
import org.utbot.jacodb.api.JcMethod
import org.utbot.jacodb.api.analysis.JcGraphTransformer
import org.utbot.jacodb.api.analysis.JcInstIdentity
import org.utbot.jacodb.api.analysis.JcInterProceduralTask
import org.utbot.jacodb.api.cfg.JcGraph
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.ext.cfg.callExpr
import org.utbot.jacodb.impl.features.SyncUsagesExtension

abstract class AbstractJcInterProceduralTask(val usages: SyncUsagesExtension) : JcInterProceduralTask {

    protected open fun <KEY, VALUE> newCache(factory: (KEY) -> VALUE): LoadingCache<KEY, VALUE> {
        return CacheBuilder.newBuilder()
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .initialCapacity(10_000)
            .softValues()
            .build(object : CacheLoader<KEY, VALUE>() {
                override fun load(body: KEY): VALUE {
                    return factory(body)
                }
            })
    }

    protected val graphsStore by lazy(LazyThreadSafetyMode.NONE) {
        newCache<JcMethod, JcGraph> { flowOf(it) }
    }

    open val JcMethod.actualFlowGraph: JcGraph
        get() {
            return graphsStore.getUnchecked(this)
        }

    override fun groupedCallersOf(method: JcMethod): Map<JcMethod, Set<JcInst>> {
        val callersMethod = usages.findUsages(method)
        return callersMethod.associateWith {
            it.actualFlowGraph.instructions.mapIndexedNotNull { index, inst ->
                val callExpr = inst.callExpr
                if (callExpr != null && callExpr.method.method == method) {
                    inst
                } else {
                    null
                }
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
        return listOf(method.actualFlowGraph.toRef { it.entry })
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

    private inline fun JcGraph.toRef(inst: (JcGraph) -> JcInst): JcInstIdentity {
        return JcInstIdentity(this, indexOf(inst(this)))
    }
}

abstract class TransformingInterProcedureTask(
    usages: SyncUsagesExtension,
    _transformers: List<JcGraphTransformer>
) : AbstractJcInterProceduralTask(usages) {

    override val transformers = _transformers.toPersistentList()

}