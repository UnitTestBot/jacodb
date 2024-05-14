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

package org.jacodb.ifds.taint

import org.jacodb.analysis.taint.BackwardTaintAnalyzer
import org.jacodb.analysis.taint.ForwardTaintAnalyzer
import org.jacodb.analysis.taint.TaintDomainFact
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.ifds.ChunkResolver
import org.jacodb.ifds.ChunkStrategy
import org.jacodb.ifds.ClassChunkStrategy
import org.jacodb.ifds.DefaultChunkResolver
import org.jacodb.ifds.JcFlowFunctionsAdapter
import org.jacodb.ifds.JcIfdsContext
import org.jacodb.ifds.domain.Reason
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.messages.NewEdge

private fun complementRunner(type: RunnerId): RunnerId =
    when (type) {
        ForwardRunnerId -> BackwardRunnerId
        BackwardRunnerId -> ForwardRunnerId
        else -> error("unexpected runner: $type")
    }

fun taintIfdsContext(
    cp: JcClasspath,
    graph: JcApplicationGraph,
    bannedPackagePrefixes: List<String>,
    chunkStrategy: ChunkStrategy<JcInst> = ClassChunkStrategy,
    useBackwardRunner: Boolean = false,
): JcIfdsContext<TaintDomainFact> =
    JcIfdsContext(
        cp,
        graph,
        bannedPackagePrefixes,
        DefaultChunkResolver(chunkStrategy)
    ) { runnerId ->
        val analyzer = when (runnerId) {
            is ForwardRunnerId -> ForwardTaintAnalyzer(ForwardRunnerId, graph)
            is BackwardRunnerId -> BackwardTaintAnalyzer(BackwardRunnerId, ForwardRunnerId, graph)
            else -> error("Unexpected runnerId: $runnerId")
        }

        JcFlowFunctionsAdapter(
            runnerId,
            analyzer
        ) { event ->
            if (useBackwardRunner || !(event is NewEdge<*, *> && event.reason is Reason.FromOtherRunner)) {
                add(event)
            }
        }
    }
