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

import org.jacodb.api.JcMethod
import org.jacodb.api.analysis.ApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcInstLocation
import org.jacodb.api.cfg.JcNoopInst

/**
 * Simplified and optimized  JcApplicationGraph that ignores method calls matching [bannedPackagePrefixes]
 */
class SimplifiedJcApplicationGraph(
    private val impl: JcApplicationGraphImpl,
    private val bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
) : ApplicationGraph<JcMethod, JcInst> by impl {

    private val visitedCallers: MutableMap<JcMethod, MutableSet<JcInst>> = mutableMapOf()

    private fun getStartInst(method: JcMethod): JcNoopInst {
        return JcNoopInst(JcInstLocation(method, -1, -1))
    }

    override fun predecessors(node: JcInst): Sequence<JcInst> {
        val method = methodOf(node)
        return if (node == getStartInst(method)) {
            emptySequence()
        } else {
            if (node in impl.entryPoint(method)) {
                sequenceOf(getStartInst(method))
            } else {
                impl.predecessors(node)
            }
        }
    }
    override fun successors(node: JcInst): Sequence<JcInst> {
        val method = methodOf(node)
        return if (node == getStartInst(method)) {
            impl.entryPoint(method)
        } else {
            impl.successors(node)
        }
    }
    override fun callees(node: JcInst): Sequence<JcMethod> = impl.callees(node).filterNot { callee ->
        bannedPackagePrefixes.any { callee.enclosingClass.name.startsWith(it) }
    }.map {
        val curSet = visitedCallers.getOrPut(it) { mutableSetOf() }
        curSet.add(node)
        it
    }

    /**
     * This is IFDS-algorithm aware optimization.
     * In IFDS we don't need all method callers, we need only method callers which we visited earlier.
     */
    override fun callers(method: JcMethod): Sequence<JcInst> = visitedCallers.getOrDefault(method, mutableSetOf()).asSequence()

    /**
     * This is IFDS-algorithm aware optimization.
     * In IFDS we don't need all entry-points of method, we need only entry-points of method which we visited earlier.
     */
    override fun entryPoint(method: JcMethod): Sequence<JcInst> = sequenceOf(getStartInst(method))

    companion object {
        val defaultBannedPackagePrefixes: List<String> = listOf(
            "kotlin.",
            "java.",
            "kotlin.jvm.internal.",
            "jdk.internal.",
            "sun.",
            "java.security.",
            "java.util.regex."
        )
    }
}
