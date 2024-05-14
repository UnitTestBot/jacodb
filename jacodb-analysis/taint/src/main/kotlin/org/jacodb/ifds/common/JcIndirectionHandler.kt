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

import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.ActorRef
import org.jacodb.ifds.taint.TaintDomainFact
import org.jacodb.api.JcClassType
import org.jacodb.api.JcMethod
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcVirtualCallExpr
import org.jacodb.api.ext.HierarchyExtension
import org.jacodb.api.ext.cfg.callExpr
import org.jacodb.api.ext.isSubClassOf
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.messages.RunnerMessage
import org.jacodb.ifds.messages.IndirectionMessage
import org.jacodb.ifds.messages.ResolvedCall
import org.jacodb.ifds.messages.UnresolvedCall

context(ActorContext<IndirectionMessage>)
class JcIndirectionHandler(
    private val hierarchy: HierarchyExtension,
    private val bannedPackagePrefixes: List<String>,
    private val parent: ActorRef<RunnerMessage>,
    private val runnerId: RunnerId,
) : Actor<IndirectionMessage> {
    private val cache = hashMapOf<JcMethod, List<JcMethod>>()

    override suspend fun receive(message: IndirectionMessage) {
        // TODO: refactor it
        @Suppress("UNCHECKED_CAST")
        message as? UnresolvedCall<JcInst, TaintDomainFact> ?: return

        val node = message.edge.to.statement

        val callees = (node.callExpr?.let { sequenceOf(it.method.method) } ?: emptySequence())
            .filterNot { callee ->
                bannedPackagePrefixes.any { callee.enclosingClass.name.startsWith(it) }
            }

        val callExpr = node.callExpr as? JcVirtualCallExpr
        if (callExpr == null) {
            for (override in callees) {
                parent.send(ResolvedCall(runnerId, message.edge, override))
            }
            return
        }
        val instanceClass = (callExpr.instance.type as? JcClassType)?.jcClass
        if (instanceClass == null) {
            parent.send(ResolvedCall(runnerId, message.edge, callExpr.method.method))
            return
        }

        val overrides = callees
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
            }

        for (override in overrides) {
            parent.send(ResolvedCall(runnerId, message.edge, override))
        }
    }
}
