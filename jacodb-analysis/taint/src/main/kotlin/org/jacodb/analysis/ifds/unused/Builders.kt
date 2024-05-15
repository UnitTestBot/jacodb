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

package org.jacodb.analysis.ifds.unused

import org.jacodb.actors.impl.system
import org.jacodb.analysis.graph.defaultBannedPackagePrefixes
import org.jacodb.analysis.ifds.actors.ProjectManager
import org.jacodb.analysis.ifds.common.ClassChunkStrategy
import org.jacodb.analysis.ifds.common.JcAsyncIfdsFacade
import org.jacodb.analysis.ifds.common.JcChunkResolver
import org.jacodb.analysis.ifds.common.JcIfdsContext
import org.jacodb.analysis.ifds.common.JcIfdsFacade
import org.jacodb.analysis.ifds.common.SingletonRunnerId
import org.jacodb.analysis.ifds.domain.Vertex
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst

fun unusedIfdsContext(
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: org.jacodb.analysis.ifds.ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcIfdsContext<UnusedVariableDomainFact> =
    JcIfdsContext(
        cp,
        bannedPackagePrefixes,
        JcChunkResolver(graph, chunkStrategy)
    ) { runnerId ->
        when (runnerId) {
            is SingletonRunnerId -> UnusedVariableAnalyzer(SingletonRunnerId, graph)
            else -> error("Unexpected runnerId: $runnerId")
        }
    }

fun unusedIfdsFacade(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: org.jacodb.analysis.ifds.ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcIfdsFacade<UnusedVariableDomainFact, UnusedVulnerability> {
    val context = unusedIfdsContext(cp, graph, bannedPackagePrefixes, chunkStrategy)
    val system = system(name) { ProjectManager(context) }
    return object : JcIfdsFacade<UnusedVariableDomainFact, UnusedVulnerability>(
        graph,
        context,
        system,
        SingletonRunnerId
    ) {
        override suspend fun collectFindings(): Collection<UnusedVulnerability> {
            val data = collectComputationData()

            val allFacts = data.factsByStmt

            val used = hashMapOf<JcInst, Boolean>()
            for ((inst, facts) in allFacts) {
                for (fact in facts) {
                    if (fact is UnusedVariable) {
                        used.putIfAbsent(fact.initStatement, false)
                        if (fact.variable.isUsedAt(inst)) {
                            used[fact.initStatement] = true
                        }
                    }

                }
            }
            val vulnerabilities = used.filterValues { !it }.keys.map {
                UnusedVulnerability(
                    message = "Assigned value is unused",
                    sink = Vertex(it, UnusedVariableZeroFact)
                )
            }
            return vulnerabilities
        }
    }
}

fun asyncUnusedIfdsFacade(
    name: String,
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String> = defaultBannedPackagePrefixes,
    chunkStrategy: org.jacodb.analysis.ifds.ChunkStrategy<JcInst> = ClassChunkStrategy,
): JcAsyncIfdsFacade<UnusedVariableDomainFact, UnusedVulnerability> {
    val facade = unusedIfdsFacade(name, cp, graph, bannedPackagePrefixes, chunkStrategy)
    return JcAsyncIfdsFacade(facade)
}
