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

import org.jacodb.analysis.engine.AnalysisDependentEvent
import org.jacodb.analysis.engine.AnalyzerFactory
import org.jacodb.analysis.engine.DomainFact
import org.jacodb.analysis.engine.IfdsResult
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.sarif.VulnerabilityDescription
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.analysis.JcApplicationGraph
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstLocation

fun JcAliasAnalyzerFactory(
    generates: (JcInst) -> List<DomainFact>,
    sanitizes: (JcExpr, TaintNode) -> Boolean,
    sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int = 5
) = AnalyzerFactory { graph ->
    JcAliasAnalyzer(graph as JcApplicationGraph, generates, sanitizes, sinks, maxPathLength)
}

private class JcAliasAnalyzer(
    graph: JcApplicationGraph,
    override val generates: (JcInst) -> List<DomainFact>,
    override val sanitizes: (JcExpr, TaintNode) -> Boolean,
    override val sinks: (JcInst) -> List<TaintAnalysisNode>,
    maxPathLength: Int,
) : TaintAnalyzer<JcMethod, JcInstLocation, JcInst>(graph, maxPathLength) {
    override fun generateDescriptionForSink(sink: IfdsVertex<JcMethod, JcInstLocation, JcInst>): VulnerabilityDescription =
        TODO()

    override fun handleIfdsResult(ifdsResult: IfdsResult<JcMethod, JcInstLocation, JcInst>): List<AnalysisDependentEvent> =
        TODO()
}