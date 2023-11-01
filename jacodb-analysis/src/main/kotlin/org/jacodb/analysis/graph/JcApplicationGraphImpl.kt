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
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.impl.features.SyncUsagesExtension

/**
 * Possible we will need JcRawInst instead of JcInst
 */
open class JcApplicationGraphImpl(
    override val classpath: JcClasspath,
    private val usages: SyncUsagesExtension,
) : JcApplicationGraph {
    private val methods = mutableSetOf<JcMethod>()

    override fun predecessors(node: JcInst): Sequence<JcInst> {
        val graph = node.location.method.flowGraph()
        val predecessors = graph.predecessors(node)
        val throwers = graph.throwers(node)
        return predecessors.asSequence() + throwers.asSequence()
    }

    override fun successors(node: JcInst): Sequence<JcInst> {
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        val catchers = graph.catchers(node)
        return successors.asSequence() + catchers.asSequence()
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
            it.flowGraph().instructions.filter { inst ->
                inst.callExpr?.method?.method == method
            }.asSequence()
        }
    }

    override fun entryPoints(method: JcMethod): Sequence<JcInst> {
        methods.add(method)
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: JcMethod): Sequence<JcInst> {
        methods.add(method)
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: JcInst): JcMethod {
        return node.location.method.also { methods.add(it) }
    }
}
