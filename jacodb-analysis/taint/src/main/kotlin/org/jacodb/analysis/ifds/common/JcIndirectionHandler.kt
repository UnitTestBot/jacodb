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

package org.jacodb.analysis.ifds.common

import org.jacodb.analysis.ifds.domain.IndirectionHandler
import org.jacodb.analysis.ifds.domain.RunnerId
import org.jacodb.analysis.ifds.messages.IndirectionMessage
import org.jacodb.analysis.ifds.messages.NoResolvedCall
import org.jacodb.analysis.ifds.messages.ResolvedCall
import org.jacodb.analysis.ifds.messages.RunnerMessage
import org.jacodb.analysis.ifds.messages.UnresolvedCall
import org.jacodb.analysis.ifds.taint.TaintDomainFact
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.isSubClassOf

class JcIndirectionHandler(
    private val hierarchy: HierarchyExtension,
    private val bannedPackagePrefixes: List<String>,
    private val runnerId: RunnerId,
) : IndirectionHandler {
    private val cache = hashMapOf<JcMethod, List<JcMethod>>()

    override fun handle(message: IndirectionMessage): Collection<RunnerMessage> {
        @Suppress("UNCHECKED_CAST")
        message as? UnresolvedCall<JcInst, TaintDomainFact> ?: error("Unexpected message: $message")

        val edge = message.edge
        val node = edge.to.statement

        val callExpr = node.callExpr ?: return emptyList()
        val callee = callExpr.method.method
        if (bannedPackagePrefixes.any { callee.enclosingClass.name.startsWith(it) }) {
            return listOf(NoResolvedCall(runnerId, edge))
        }

        if (callExpr !is JcVirtualCallExpr) {
            return listOf(ResolvedCall(runnerId, edge, callee))
        }

        val instanceClass = (callExpr.instance.type as? JcClassType)?.jcClass
            ?: return listOf(ResolvedCall(runnerId, edge, callee))


        val overrides = cache
            .computeIfAbsent(callee) { hierarchy.findOverrides(callee).toList() }
            .asSequence()
            .filter { it.enclosingClass isSubClassOf instanceClass }
        return (overrides + callee).mapTo(mutableListOf()) { ResolvedCall(runnerId, edge, it) }
    }
}
