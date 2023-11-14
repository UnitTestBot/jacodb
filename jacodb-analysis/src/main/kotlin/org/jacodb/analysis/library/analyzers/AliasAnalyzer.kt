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

package org.jacodb.analysis.library.analyzers

import org.jacodb.analysis.config.TaintConfig
import org.jacodb.analysis.engine.AnalysisDependentEvent
import org.jacodb.analysis.engine.AnalyzerFactory
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.IfdsResult
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.sarif.VulnerabilityDescription
import org.jacodb.api.analysis.JcApplicationGraph
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcInst

fun AliasAnalyzerFactory(
    generates: (JcInst) -> List<DomainFact>,
    sanitizes: (JcExpr, TaintNode) -> Boolean,
    sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5,
) = AnalyzerFactory { graph ->
    AliasAnalyzer(graph, generates, sanitizes, sinks, maxPathLength)
}

private class AliasAnalyzer(
    graph: JcApplicationGraph,
    override val generates: (JcInst) -> List<DomainFact>,
    override val sanitizes: (JcExpr, TaintNode) -> Boolean,
    override val sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : TaintAnalyzer(graph, maxPathLength) {
    override fun generateDescriptionForSink(sink: IfdsVertex): VulnerabilityDescription = TODO()

    override fun handleIfdsResult(ifdsResult: IfdsResult): List<AnalysisDependentEvent> = TODO()
}
