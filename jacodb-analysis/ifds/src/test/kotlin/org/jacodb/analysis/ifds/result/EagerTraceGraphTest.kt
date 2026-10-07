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

package org.jacodb.analysis.ifds.result

import org.jacodb.analysis.ifds.domain.Edge
import org.jacodb.analysis.ifds.domain.Reason
import org.jacodb.analysis.ifds.domain.Vertex
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EagerTraceGraphTest {
    @Test
    fun `returned summary trace contains the callee body and exit`() {
        val caller = Vertex(statement = "call", fact = "caller")
        val entry = Vertex(statement = "entry", fact = "input")
        val body = Vertex(statement = "body", fact = "generated")
        val exit = Vertex(statement = "exit", fact = "output")
        val returned = Vertex(statement = "return-site", fact = "returned")
        val callerEdge = Edge(from = caller, to = caller)
        val entryEdge = Edge(from = entry, to = entry)
        val bodyEdge = Edge(from = entry, to = body)
        val summaryEdge = Edge(from = entry, to = exit)
        val returnEdge = Edge(from = caller, to = returned)
        val reasons: Map<Edge<String, String>, Collection<Reason<String, String>>> = mapOf(
            callerEdge to listOf(Reason.Initial),
            entryEdge to listOf(Reason.CallToStart(edge = callerEdge)),
            bodyEdge to listOf(Reason.Sequent(edge = entryEdge)),
            summaryEdge to listOf(Reason.Sequent(edge = bodyEdge)),
            returnEdge to listOf(Reason.ExitToReturnSite(callerEdge = callerEdge, edge = summaryEdge)),
        )
        val data = IfdsComputationData<String, String, Finding<String, String>>(
            edgesByEnd = mapOf(returned to listOf(returnEdge)),
            factsByStmt = emptyMap(),
            reasonsByEdge = reasons,
            findings = emptyList(),
        )

        val traces = data.buildTraceGraph(sink = returned).getAllTraces().toList()

        assertEquals(listOf(listOf(caller, entry, body, exit, returned)), traces)
    }
}
