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

package org.jacodb.ifds.common

import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.isSubClassOf
import org.jacodb.ifds.domain.IndirectionHandler
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.messages.IndirectionMessage
import org.jacodb.ifds.messages.ResolvedCall
import org.jacodb.ifds.messages.RunnerMessage
import org.jacodb.ifds.messages.UnresolvedCall
import org.jacodb.ifds.taint.TaintDomainFact

class JcIndirectionHandler(
    private val hierarchy: HierarchyExtension,
    private val bannedPackagePrefixes: List<String>,
    private val runnerId: RunnerId,
) : IndirectionHandler {
    private val cache = hashMapOf<JcMethod, List<JcMethod>>()


    override fun handle(message: IndirectionMessage): Collection<RunnerMessage> {
        @Suppress("UNCHECKED_CAST")
        message as? UnresolvedCall<JcInst, TaintDomainFact> ?: return emptyList()

        val node = message.edge.to.statement

        val callees = (node.callExpr?.let { listOf(it.method.method) } ?: emptyList())
            .filterNot { callee ->
                bannedPackagePrefixes.any { callee.enclosingClass.name.startsWith(it) }
            }

        val callExpr = node.callExpr as? JcVirtualCallExpr
            ?: return callees.map { ResolvedCall(runnerId, message.edge, it) }

        val instanceClass = (callExpr.instance.type as? JcClassType)?.jcClass
            ?: return listOf(ResolvedCall(runnerId, message.edge, callExpr.method.method))

        return callees
            .flatMap { callee ->
                val allOverrides = cache.computeIfAbsent(callee) {
                    hierarchy.findOverrides(callee).toList()
                }.filter {
                    it.enclosingClass isSubClassOf instanceClass ||
                        // TODO: use only down-most override here
                        instanceClass isSubClassOf it.enclosingClass
                }.asSequence()
                // TODO: maybe filter inaccessible methods here?
                allOverrides + sequenceOf(callee)
            }.mapTo(mutableListOf()) { ResolvedCall(runnerId, message.edge, it) }
    }
}
