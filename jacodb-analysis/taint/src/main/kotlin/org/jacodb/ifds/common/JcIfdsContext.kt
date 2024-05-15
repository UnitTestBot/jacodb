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

import org.jacodb.actors.api.ActorFactory
import org.jacodb.actors.api.ActorRef
import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcInst
import org.jacodb.ifds.ChunkResolver
import org.jacodb.ifds.IfdsContext
import org.jacodb.ifds.domain.Chunk
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.messages.RunnerMessage
import org.jacodb.impl.features.HierarchyExtensionImpl
import java.util.concurrent.ConcurrentHashMap

class JcIfdsContext<Fact>(
    private val cp: JcClasspath,
    private val bannedPackagePrefixes: List<String>,
    private val chunkStrategy: ChunkResolver,
    private val analyzerFactory: (RunnerId) -> JcBaseAnalyzer<Fact>,
) : IfdsContext<JcInst> {
    override fun chunkByMessage(message: RunnerMessage): Chunk =
        chunkStrategy.chunkByMessage(message)

    override fun runnerIdByMessage(message: RunnerMessage): RunnerId =
        message.runnerId

    private val analyzers = ConcurrentHashMap<RunnerId, JcBaseAnalyzer<Fact>>()

    override fun getAnalyzer(runnerId: RunnerId): JcBaseAnalyzer<Fact> =
        analyzers.computeIfAbsent(runnerId, analyzerFactory)

    override fun indirectionHandlerFactory(parent: ActorRef<RunnerMessage>, runnerId: RunnerId) =
        ActorFactory {
            JcIndirectionHandler(HierarchyExtensionImpl(cp), bannedPackagePrefixes, parent, runnerId)
        }
}
