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

import org.jacodb.analysis.npe.NpeAnalyzer
import org.jacodb.analysis.taint.TaintDomainFact
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.ifds.ChunkResolver
import org.jacodb.ifds.ClassChunkStrategy
import org.jacodb.ifds.DefaultChunkResolver
import org.jacodb.ifds.JcFlowFunctionsAdapter
import org.jacodb.ifds.JcIfdsContext

fun npeIfdsContext(
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String>,
    chunkStrategy: ChunkResolver = DefaultChunkResolver(ClassChunkStrategy),
): JcIfdsContext<TaintDomainFact> =
    JcIfdsContext(
        cp,
        graph,
        bannedPackagePrefixes,
        chunkStrategy
    ) { runnerId ->
        val analyzer = when (runnerId) {
            is SingletonRunnerId -> NpeAnalyzer(runnerId, graph)
            else -> error("Unexpected runnerId: $runnerId")
        }

        JcFlowFunctionsAdapter(
            runnerId,
            analyzer
        ) { event ->
            add(event)
        }
    }
