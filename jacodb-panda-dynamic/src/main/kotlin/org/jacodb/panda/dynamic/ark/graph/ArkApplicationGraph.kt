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
import org.jacodb.panda.dynamic.ark.base.ArkCallStmt
import org.jacodb.panda.dynamic.ark.base.ArkStmt
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.jacodb.panda.dynamic.ark.model.ArkMethod
import org.jacodb.panda.dynamic.ark.utils.callExpr

class ArkApplicationGraph(
    override val project: ArkFile,
) : ApplicationGraph<ArkMethod, ArkStmt> {

    override fun predecessors(node: ArkStmt): Sequence<ArkStmt> {
        val graph = node.method.flowGraph()
        val predecessors = graph.predecessors(node)
        return predecessors.asSequence()
    }

    override fun successors(node: ArkStmt): Sequence<ArkStmt> {
        val graph = node.method.flowGraph()
        val successors = graph.successors(node)
        return successors.asSequence()
    }

    override fun callees(node: ArkStmt): Sequence<ArkMethod> {
        val expr = node.callExpr ?: return emptySequence()
        val callee = expr.method
        return project.classes.asSequence()
            .flatMap { it.methods }
            .filter { it.name == callee.name }
    }

    override fun callers(method: ArkMethod): Sequence<ArkStmt> {
        return project.classes.asSequence()
            .flatMap { it.methods }
            .flatMap { it.cfg.instructions }
            .filterIsInstance<ArkCallStmt>()
            // TODO: consider comparing only by name
            .filter { it.expr.method == method.signature }
    }

    override fun entryPoints(method: ArkMethod): Sequence<ArkStmt> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: ArkMethod): Sequence<ArkStmt> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: ArkStmt): ArkMethod {
        return node.location.method
    }
}
