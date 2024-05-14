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

package org.jacodb.ifds.result

import org.jacodb.ifds.domain.Edge
import org.jacodb.ifds.domain.Reason
import org.jacodb.ifds.domain.Vertex

fun <Stmt, Fact, F : Finding<Stmt, Fact>> mergeIfdsResults(
    ifdsResults: Collection<IfdsComputationData<Stmt, Fact, F>>,
): IfdsComputationData<Stmt, Fact, F> {
    val edgesByEnd = hashMapOf<Vertex<Stmt, Fact>, HashSet<Edge<Stmt, Fact>>>()
    val factsByStmt = hashMapOf<Stmt, HashSet<Fact>>()
    val reasonsByEdge = hashMapOf<Edge<Stmt, Fact>, HashSet<Reason<Stmt, Fact>>>()
    val findings = hashSetOf<F>()
    for (data in ifdsResults) {
        for ((end, edges) in data.edgesByEnd) {
            edgesByEnd.getOrPut(end, ::hashSetOf)
                .addAll(edges)
        }
        for ((stmt, facts) in data.factsByStmt) {
            factsByStmt.getOrPut(stmt, ::hashSetOf)
                .addAll(facts)
        }
        for ((edge, reasons) in data.reasonsByEdge) {
            reasonsByEdge.getOrPut(edge, ::hashSetOf)
                .addAll(reasons)
        }
        findings.addAll(data.findings)
    }
    val mergedData = IfdsComputationData(
        edgesByEnd,
        factsByStmt,
        reasonsByEdge,
        findings
    )
    return mergedData
}
