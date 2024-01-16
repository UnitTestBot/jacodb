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

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.analysis.JcApplicationGraph
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.api.jvm.cfg.JcInstVisitor
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.cfg.callExpr
import org.jacodb.api.jvm.ext.isSubClassOf
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.jacodb.impl.features.hierarchyExt

/**
 * This is adopted specially for IFDS [JcApplicationGraph] that
 *  1. Ignores method calls matching [bannedPackagePrefixes] (i.e., treats them as simple instructions with no callees)
 *  2. In [callers] returns only callsites that were visited before
 *  3. Adds a special [JcNoopInst] instruction to the beginning of each method
 *    (because backward analysis may want for method to start with neutral instruction)
 */
internal class SimplifiedJcApplicationGraph(
    private val impl: JcApplicationGraphImpl,
    private val bannedPackagePrefixes: List<String>,
) : JcApplicationGraph by impl {
    private val hierarchyExtension = runBlocking {
        classpath.hierarchyExt()
    }

    private val visitedCallers: MutableMap<JcMethod, MutableSet<JcInst>> = mutableMapOf()

    private val cache: MutableMap<JcMethod, List<JcMethod>> = mutableMapOf()

    private fun getOverrides(method: JcMethod): List<JcMethod> {
        return if (cache.containsKey(method)) {
            cache[method]!!
        } else {
            val res = hierarchyExtension.findOverrides(method).toList()
            cache[method] = res
            res
        }
    }

    // For backward analysis we may want for method to start with "neutral" operation =>
    //  we add noop to the beginning of every method
    private fun getStartInst(method: JcMethod): JcNoopInst {
        val methodEntryLineNumber = method.flowGraph().entries.firstOrNull()?.lineNumber
        return JcNoopInst(JcInstLocationImpl(method, -1, methodEntryLineNumber?.let { it - 1 } ?: -1))
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

    private fun calleesUnmarked(node: JcInst): Sequence<JcMethod> {
        val callees = impl.callees(node).filterNot { callee ->
            bannedPackagePrefixes.any { callee.enclosingClass.name.startsWith(it) }
        }

        val callExpr = node.callExpr as? JcVirtualCallExpr ?: return callees
        val instanceClass = (callExpr.instance.type as? JcClassType)?.jcClass ?: return callees

        return callees
            .flatMap { callee ->
                val allOverrides = getOverrides(callee)
                    .filter {
                        it.enclosingClass isSubClassOf instanceClass ||
                                // TODO: use only down-most override here
                                instanceClass isSubClassOf it.enclosingClass
                    }

                // TODO: maybe filter inaccessible methods here?
                allOverrides + sequenceOf(callee)
            }
    }

    override fun callees(node: JcInst): Sequence<JcMethod> {
        return calleesUnmarked(node).also {
            it.forEach {
                visitedCallers.getOrPut(it) { mutableSetOf() }.add(node)
            }
        }
    }

    /**
     * This is IFDS-algorithm aware optimization.
     * In IFDS we don't need all method callers, we need only method callers which we visited earlier.
     */
    // TODO: Think if this optimization is really needed
    override fun callers(method: JcMethod): Sequence<JcInst> = visitedCallers.getOrDefault(method, mutableSetOf()).asSequence()

    override fun entryPoint(method: JcMethod): Sequence<JcInst> = sequenceOf(getStartInst(method))

    companion object {
    }
}


data class JcNoopInst(override val location: JcInstLocation): JcInst {
    override val operands: List<JcExpr>
        get() = emptyList()

    override fun <T> accept(visitor: JcInstVisitor<T>): T {
        return visitor.visitExternalJcInst(this)
    }

    override fun toString(): String = "noop"
}