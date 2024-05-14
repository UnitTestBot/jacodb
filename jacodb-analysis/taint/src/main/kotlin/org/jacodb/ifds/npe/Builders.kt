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

package org.jacodb.ifds.npe

import org.jacodb.actors.api.ActorSystem
import org.jacodb.actors.impl.system
import org.jacodb.analysis.graph.defaultBannedPackagePrefixes
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.ifds.ChunkStrategy
import org.jacodb.ifds.common.JcChunkResolver
import org.jacodb.ifds.actors.ProjectManager
import org.jacodb.ifds.common.ClassChunkStrategy
import org.jacodb.ifds.common.JcAsyncIfdsFacade
import org.jacodb.ifds.common.JcIfdsContext
import org.jacodb.ifds.common.JcIfdsFacade
import org.jacodb.ifds.messages.CommonMessage
import org.jacodb.ifds.taint.TaintDomainFact

fun npeIfdsContext(
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcIfdsContext<TaintDomainFact> =
    JcIfdsContext(
        cp,
        bannedPackagePrefixes,
        JcChunkResolver(graph, chunkStrategy)
    ) { runnerId ->
        val analyzer = when (runnerId) {
            is SingletonRunnerId -> NpeAnalyzer(runnerId, graph)
            else -> error("Unexpected runnerId: $runnerId")
        }

        analyzer
    }

fun npeIfdsSystem(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): ActorSystem<CommonMessage> {
    val context = npeIfdsContext(
        cp,
        graph,
        bannedPackagePrefixes,
        chunkStrategy
    )
    return system(name) {
        ProjectManager(context)
    }
}

fun npeIfdsFacade(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcIfdsFacade<TaintDomainFact, NpeVulnerability> {
    val system = npeIfdsSystem(name, cp, graph, bannedPackagePrefixes, chunkStrategy)
    return JcIfdsFacade(system, SingletonRunnerId)
}

fun asyncNpeIfdsFacade(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcAsyncIfdsFacade<TaintDomainFact, NpeVulnerability> {
    val system = npeIfdsSystem(name, cp, graph, bannedPackagePrefixes, chunkStrategy)
    return JcAsyncIfdsFacade(system, SingletonRunnerId)
}
