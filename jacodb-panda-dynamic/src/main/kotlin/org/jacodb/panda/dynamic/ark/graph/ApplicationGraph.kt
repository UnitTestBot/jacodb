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

package org.jacodb.panda.dynamic.ark.graph

import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.panda.dynamic.ark.base.CallStmt
import org.jacodb.panda.dynamic.ark.base.Stmt
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.jacodb.panda.dynamic.ark.model.ArkMethod

class ArkApplicationGraph(
    override val project: ArkFile
) : ApplicationGraph<ArkMethod, Stmt> {

    override fun predecessors(node: Stmt): Sequence<Stmt> {
        val graph = node.location.method.flowGraph()
        val predecessors = graph.predecessors(node)
        return predecessors.asSequence()
    }

    override fun successors(node: Stmt): Sequence<Stmt> {
        val graph = node.location.method.flowGraph()
        val successors = graph.successors(node)
        return successors.asSequence()
    }

    override fun callees(node: Stmt): Sequence<ArkMethod> {
        val expr = (node as CallStmt).expr
        val method = expr.method
        val result = project.classes.asSequence().flatMap { it.methods }.filter { it.name == method.name }
        return result
    }

    override fun callers(method: ArkMethod): Sequence<Stmt> {
        val result = project.classes.asSequence()
            .flatMap { it.methods }
            .flatMap { it.cfg.instructions }
            .filter { stmt -> stmt is CallStmt && stmt.expr.method == method.signature }
        return result
    }

    override fun entryPoints(method: ArkMethod): Sequence<Stmt> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: ArkMethod): Sequence<Stmt> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: Stmt): ArkMethod {
        return node.location.method
    }
}
