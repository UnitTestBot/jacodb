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

import org.jacodb.api.analysis.ApplicationGraph

class BackwardApplicationGraph<Method, Statement>(
    val forward: ApplicationGraph<Method, Statement>
) : ApplicationGraph<Method, Statement> {
    override fun predecessors(node: Statement) = forward.successors(node)

    override fun successors(node: Statement) = forward.predecessors(node)

    override fun callees(node: Statement) = forward.callees(node)

    override fun callers(method: Method) = forward.callers(method)

    override fun entryPoint(method: Method) = forward.exitPoints(method)

    override fun exitPoints(method: Method) = forward.entryPoint(method)

    override fun methodOf(node: Statement) = forward.methodOf(node)

    override fun visitedMethods() = forward.visitedMethods()
}

val <Method, Statement> ApplicationGraph<Method, Statement>.reversed
    get() = if (this is BackwardApplicationGraph) {
        this.forward
    } else BackwardApplicationGraph(this)