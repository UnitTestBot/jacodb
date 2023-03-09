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

import org.jacodb.api.JcClasspath
import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.analysis.JcAnalysisFeature
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.impl.analysis.JcAnalysisPlatformImpl
import org.jacodb.impl.analysis.features.JcCacheGraphFeature
import org.jacodb.impl.features.SyncUsagesExtension

/**
 * Possible we will need JcRawInst instead of JcInst
 */
open class JcApplicationGraphImpl(
    override val classpath: JcClasspath,
    protected val usages: SyncUsagesExtension,
    cacheSize: Long = 10_000,
    features: List<JcAnalysisFeature> = emptyList()
) : JcAnalysisPlatformImpl(classpath, features + listOf(JcCacheGraphFeature(cacheSize))), ApplicationGraph<JcMethod, JcInst> {

    protected open fun JcMethod.actualFlowGraph(): JcGraph {
        return flowGraph(this)
    }

    override fun predecessors(node: JcInst): Sequence<JcInst> {
        return node.location.method.actualFlowGraph().predecessors(node).asSequence()
    }

    override fun successors(node: JcInst): Sequence<JcInst> {
        return node.location.method.actualFlowGraph().successors(node).asSequence()
    }

    override fun callees(node: JcInst): Sequence<JcMethod> {
        return node.callExpr?.method?.method?.let {
            sequenceOf(it)
        } ?: emptySequence()
    }

    override fun callers(method: JcMethod): Sequence<JcInst> {
        return usages.findUsages(method).flatMap {
            it.actualFlowGraph().instructions.filter { inst ->
                inst.callExpr?.method?.method == method
            }.asSequence()
        }
    }


    override fun entryPoint(method: JcMethod): Sequence<JcInst> {
        return method.actualFlowGraph().entries.asSequence()
    }

    override fun exitPoints(method: JcMethod): Sequence<JcInst> {
        return method.actualFlowGraph().exits.asSequence()
    }

    override fun methodOf(node: JcInst): JcMethod {
        return node.location.method
    }
}