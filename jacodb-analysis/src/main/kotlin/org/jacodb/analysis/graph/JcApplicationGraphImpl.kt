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

package org.jacodb.analysis.graph

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.impl.analysis.JcAnalysisPlatformImpl
import org.jacodb.impl.analysis.features.JcCacheGraphFeature
import org.jacodb.impl.features.SyncUsagesExtension

class JcApplicationGraphImpl(
    override val classpath: JcClasspath,
    private val usages: SyncUsagesExtension,
    cacheSize: Long = 10_000
) : JcAnalysisPlatformImpl(classpath, listOf(JcCacheGraphFeature(cacheSize))), JcApplicationGraph {

    private val methods = mutableSetOf<JcMethod>()
    override fun visitedMethods() = methods.asSequence()

    private val JcMethod.actualFlowGraph: JcGraph
        get() {
            return flowGraph(this)
        }

    override fun predecessors(node: JcInst): Sequence<JcInst> {
        return node.location.method.actualFlowGraph.predecessors(node).asSequence() + node.location.method.actualFlowGraph.throwers(node).asSequence()
    }

    override fun successors(node: JcInst): Sequence<JcInst> {
        return node.location.method.actualFlowGraph.successors(node).asSequence() + node.location.method.actualFlowGraph.catchers(node).asSequence()
    }

    override fun callees(node: JcInst): Sequence<JcMethod> {
        return node.callExpr?.method?.method?.let {
            methods.add(it)
            sequenceOf(it)
        } ?: emptySequence()
    }

    override fun callers(method: JcMethod): Sequence<JcInst> {
        methods.add(method)
        return usages.findUsages(method).flatMap {
            it.actualFlowGraph.instructions.filter { inst ->
                inst.callExpr?.method?.method == method
            }.asSequence()
        }
    }


    override fun entryPoint(method: JcMethod): Sequence<JcInst> {
        methods.add(method)
        return method.actualFlowGraph.entries.asSequence()
    }

    override fun exitPoints(method: JcMethod): Sequence<JcInst> {
        methods.add(method)
        return method.actualFlowGraph.exits.asSequence()
    }

    override fun methodOf(node: JcInst): JcMethod {
        return node.location.method.also { methods.add(it) }
    }
}