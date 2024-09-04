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

package org.jacodb.ets.graph

import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.ets.base.EtsStmt
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsScene
import org.jacodb.ets.utils.callExpr

interface EtsApplicationGraph : ApplicationGraph<EtsMethod, EtsStmt> {
    val cp: EtsScene
}

class EtsApplicationGraphImpl(
    override val cp: EtsScene,
) : EtsApplicationGraph {

    override fun predecessors(node: EtsStmt): Sequence<EtsStmt> {
        val graph = node.method.flowGraph()
        val predecessors = graph.predecessors(node)
        return predecessors.asSequence()
    }

    override fun successors(node: EtsStmt): Sequence<EtsStmt> {
        val graph = node.method.flowGraph()
        val successors = graph.successors(node)
        return successors.asSequence()
    }

    override fun callees(node: EtsStmt): Sequence<EtsMethod> {
        val expr = node.callExpr ?: return emptySequence()
        val callee = expr.method

        // TODO: Fix later
        if (callee.enclosingClass.name.isBlank()) {
            val clazz = cp.classes.single { it.signature == node.method.enclosingClass }
            return clazz.methods.asSequence().filter { it.name == callee.name }
        } else {
            check(callee.name == "@instance_init" || callee.name == "constructor")
        }

        val allMethods = cp.classes
            .asSequence()
            .flatMap { it.methods.asSequence() + it.ctor }

        val methodsWithSameName = allMethods.filter {
            it.name == callee.name
        }
        if (methodsWithSameName.count() == 1) {
            return sequenceOf(methodsWithSameName.first())
        }

        val methodsWithSameClassName = methodsWithSameName.filter {
            it.enclosingClass.name == callee.enclosingClass.name
        }
        if (methodsWithSameClassName.count() == 1) {
            return sequenceOf(methodsWithSameClassName.first())
        }

        // Else, return all methods with the same signature.
        return allMethods.filter {
            it.signature == callee
        }
    }

    override fun callers(method: EtsMethod): Sequence<EtsStmt> {
        // Note: currently, nobody uses `callers`, so if is safe to disable it for now.
        // Note: comparing methods by signature may be incorrect, and comparing only by name fails for constructors.
        TODO("disabled for now, need re-design")
        // return cp.classes.asSequence()
        //     .flatMap { it.methods }
        //     .flatMap { it.cfg.instructions }
        //     .filterIsInstance<EtsCallStmt>()
        //     .filter { it.expr.method == method.signature }
    }

    override fun entryPoints(method: EtsMethod): Sequence<EtsStmt> {
        return method.flowGraph().entries.asSequence()
    }

    override fun exitPoints(method: EtsMethod): Sequence<EtsStmt> {
        return method.flowGraph().exits.asSequence()
    }

    override fun methodOf(node: EtsStmt): EtsMethod {
        return node.location.method
    }
}
