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

import org.jacodb.analysis.ifds.ChunkResolver
import org.jacodb.analysis.ifds.IfdsContext
import org.jacodb.analysis.ifds.IfdsSystemOptions
import org.jacodb.analysis.ifds.domain.Chunk
import org.jacodb.analysis.ifds.domain.IndirectionHandler
import org.jacodb.analysis.ifds.domain.RunnerId
import org.jacodb.analysis.ifds.messages.RunnerMessage
import org.jacodb.api.JcClasspath
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.features.HierarchyExtensionImpl
import java.util.concurrent.ConcurrentHashMap

class JcIfdsContext<Fact>(
    private val cp: JcClasspath,
    override val options: IfdsSystemOptions,
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

    override fun getIndirectionHandler(runnerId: RunnerId): IndirectionHandler =
        JcIndirectionHandler(HierarchyExtensionImpl(cp), bannedPackagePrefixes, runnerId)
}
