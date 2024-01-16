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

import org.jacodb.analysis.engine.AnalyzerFactory
import org.jacodb.analysis.engine.IfdsVertex
import org.jacodb.analysis.sarif.SarifMessage
import org.jacodb.analysis.sarif.VulnerabilityDescription
import org.jacodb.api.jvm.analysis.JcApplicationGraph

class SqlInjectionAnalyzer(
    graph: JcApplicationGraph,
    maxPathLength: Int
) : TaintAnalyzer(graph, maxPathLength) {
    override val generates = isSourceMethodToGenerates(sqlSourceMatchers.asMethodMatchers)
    override val sanitizes = isSanitizeMethodToSanitizes(sqlSanitizeMatchers.asMethodMatchers)
    override val sinks = isSinkMethodToSinks(sqlSinkMatchers.asMethodMatchers)

    companion object {
        private const val ruleId: String = "SQL-injection"
        private val vulnerabilityMessage = SarifMessage("SQL query with unchecked injection")

        val vulnerabilityDescription = VulnerabilityDescription(vulnerabilityMessage, ruleId)
    }

    override fun generateDescriptionForSink(sink: IfdsVertex): VulnerabilityDescription = vulnerabilityDescription
}

class SqlInjectionBackwardAnalyzer(
    graph: JcApplicationGraph,
    maxPathLength: Int
) : TaintBackwardAnalyzer(graph, maxPathLength) {
    override val generates = isSourceMethodToGenerates(sqlSourceMatchers.asMethodMatchers)
    override val sinks = isSinkMethodToSinks(sqlSinkMatchers.asMethodMatchers)
}

fun SqlInjectionAnalyzerFactory(maxPathLength: Int) = AnalyzerFactory { graph ->
    SqlInjectionAnalyzer(graph, maxPathLength)
}

fun SqlInjectionBackwardAnalyzerFactory(maxPathLength: Int) = AnalyzerFactory { graph ->
    SqlInjectionBackwardAnalyzer(graph, maxPathLength)
}

private val sqlSourceMatchers = listOf(
    "java\\.io.+",
    "java\\.lang\\.System\\#getenv",
    "java\\.sql\\.ResultSet#get.+"
)

private val sqlSanitizeMatchers = listOf(
    "java\\.sql\\.Statement#set.*",
    "java\\.sql\\.PreparedStatement#set.*"
)

private val sqlSinkMatchers = listOf(
    "java\\.sql\\.Statement#execute.*",
    "java\\.sql\\.PreparedStatement#execute.*",
)