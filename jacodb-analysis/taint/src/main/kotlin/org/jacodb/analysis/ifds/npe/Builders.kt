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

package org.jacodb.analysis.ifds.npe

import org.jacodb.analysis.ifds.ChunkStrategy
import org.jacodb.analysis.ifds.IfdsSystemOptions
import org.jacodb.analysis.ifds.common.ClassChunkStrategy
import org.jacodb.analysis.ifds.common.JcAsyncIfdsFacade
import org.jacodb.analysis.ifds.common.JcChunkResolver
import org.jacodb.analysis.ifds.common.JcIfdsContext
import org.jacodb.analysis.ifds.common.JcIfdsFacade
import org.jacodb.analysis.ifds.common.SingletonRunnerId
import org.jacodb.analysis.ifds.common.defaultBannedPackagePrefixes
import org.jacodb.analysis.ifds.taint.TaintDomainFact
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst

fun npeIfdsContext(
    cp: JcClasspath,
    graph: JcApplicationGraph,
    ifdsSystemOptions: IfdsSystemOptions = IfdsSystemOptions(),
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcIfdsContext<TaintDomainFact> =
    JcIfdsContext(
        cp,
        ifdsSystemOptions,
        bannedPackagePrefixes,
        chunkStrategy,
        JcChunkResolver(chunkStrategy)
    ) { runnerId ->
        val analyzer = when (runnerId) {
            is SingletonRunnerId -> NpeAnalyzer(runnerId, graph)
            else -> error("Unexpected runnerId: $runnerId")
        }

        analyzer
    }

fun npeIfdsFacade(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    ifdsSystemOptions: IfdsSystemOptions = IfdsSystemOptions(),
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcIfdsFacade<TaintDomainFact, NpeVulnerability> {
    val context = npeIfdsContext(cp, graph, ifdsSystemOptions, bannedPackagePrefixes, chunkStrategy)
    return JcIfdsFacade(name, graph, context, SingletonRunnerId)
}

fun asyncNpeIfdsFacade(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    ifdsSystemOptions: IfdsSystemOptions = IfdsSystemOptions(),
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcAsyncIfdsFacade<TaintDomainFact, NpeVulnerability> {
    val facade = npeIfdsFacade(name, cp, graph, ifdsSystemOptions, bannedPackagePrefixes, chunkStrategy)
    return JcAsyncIfdsFacade(facade)
}
