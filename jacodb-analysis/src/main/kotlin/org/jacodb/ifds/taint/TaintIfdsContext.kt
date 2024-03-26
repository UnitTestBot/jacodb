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

import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.Factory
import org.jacodb.ifds.domain.Analyzer
import org.jacodb.ifds.domain.Chunk
import org.jacodb.ifds.domain.IfdsContext
import org.jacodb.ifds.domain.RunnerType
import org.jacodb.ifds.messages.CommonMessage
import org.jacodb.ifds.messages.NewResult
import org.jacodb.analysis.taint.EdgeForOtherRunner
import org.jacodb.analysis.taint.NewSummaryEdge
import org.jacodb.analysis.taint.NewVulnerability
import org.jacodb.analysis.taint.TaintDomainFact
import org.jacodb.analysis.taint.TaintEvent
import org.jacodb.api.JcClasspath
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.impl.features.HierarchyExtensionImpl

class TaintIfdsContext(
    private val cp: JcClasspath,
    private val graph: JcApplicationGraph,
    private val jcAnalyzer: org.jacodb.analysis.ifds.Analyzer<TaintDomainFact, TaintEvent>,
    private val bannedPackagePrefixes: List<String>,
) : IfdsContext<JcInst, TaintDomainFact> {
    data object Chunk1 : Chunk
    data object Runner1 : RunnerType

    override fun chunkByMessage(message: CommonMessage): Chunk {
        return Chunk1
    }

    override fun runnerTypeByMessage(message: CommonMessage): RunnerType {
        return Runner1
    }

    override fun getAnalyzer(chunk: Chunk, type: RunnerType): Analyzer<JcInst, TaintDomainFact> {
        val wrapper = JcFlowFunctionsAdapter(Runner1, jcAnalyzer) { event ->
            when (event) {
                is EdgeForOtherRunner -> {

                }

                is NewSummaryEdge -> {

                }

                is NewVulnerability -> {
                    val result = NewResult(Runner1, event.vulnerability)
                    add(result)
                }
            }
        }

        return TaintAnalyzer(
            graph,
            wrapper,
            Runner1
        )
    }

    override fun indirectionHandlerFactory(parent: ActorRef<CommonMessage>) =
        Factory {
            IndirectionHandler(HierarchyExtensionImpl(cp), bannedPackagePrefixes, parent)
        }

}
