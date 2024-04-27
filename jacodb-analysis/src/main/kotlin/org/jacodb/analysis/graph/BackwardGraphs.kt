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

@file:JvmName("BackwardApplicationGraphs")

package org.jacodb.analysis.graph

import org.jacodb.analysis.util.Traits
import org.jacodb.api.common.CommonProject
import org.jacodb.api.common.analysis.ApplicationGraph
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.analysis.JcApplicationGraph
import org.jacodb.api.jvm.cfg.JcInst

private class BackwardApplicationGraphImpl<Method, Statement>(
    val forward: ApplicationGraph<Method, Statement>,
) : ApplicationGraph<Method, Statement> {

    override val project: CommonProject
        get() = forward.project

    override fun predecessors(node: Statement) = forward.successors(node)
    override fun successors(node: Statement) = forward.predecessors(node)

    override fun callees(node: Statement) = forward.callees(node)
    override fun callers(method: Method) = forward.callers(method)

    override fun entryPoints(method: Method) = forward.exitPoints(method)
    override fun exitPoints(method: Method) = forward.entryPoints(method)

    override fun methodOf(node: Statement) = forward.methodOf(node)
}

@Suppress("UNCHECKED_CAST")
val <Method, Statement> ApplicationGraph<Method, Statement>.reversed: ApplicationGraph<Method, Statement>
    get() = when (this) {
        is JcApplicationGraph -> this.reversed as ApplicationGraph<Method, Statement>
        is BackwardApplicationGraphImpl -> this.forward
        else -> BackwardApplicationGraphImpl(this)
    }

private class BackwardJcApplicationGraphImpl(
    val forward: JcApplicationGraph,
) : JcApplicationGraph,
    ApplicationGraph<JcMethod, JcInst> by BackwardApplicationGraphImpl(forward) {

    override val project: JcClasspath
        get() = forward.project
}

val JcApplicationGraph.reversed: JcApplicationGraph
    get() = if (this is BackwardJcApplicationGraphImpl) {
        this.forward
    } else {
        BackwardJcApplicationGraphImpl(this)
    }
