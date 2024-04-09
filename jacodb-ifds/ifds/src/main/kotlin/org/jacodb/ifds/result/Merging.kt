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

fun <Stmt, Fact, Result : IfdsResult<Stmt, Fact>> mergeIfdsResults(
    ifdsDatas: Collection<IfdsComputationData<Stmt, Fact, Result>>,
): IfdsComputationData<Stmt, Fact, Result> {
    val edgesByEnd = hashMapOf<Vertex<Stmt, Fact>, HashSet<Edge<Stmt, Fact>>>()
    val factsByStmt = hashMapOf<Stmt, HashSet<Fact>>()
    val reasonsByEdge = hashMapOf<Edge<Stmt, Fact>, HashSet<Reason<Stmt, Fact>>>()
    val results = hashSetOf<Result>()
    for (data in ifdsDatas) {
        for ((end, edges) in data.edgesByEnd) {
            edgesByEnd.getOrPut(end, ::hashSetOf)
                .addAll(edges)
        }
        for ((stmt, facts) in data.facts) {
            factsByStmt.getOrPut(stmt, ::hashSetOf)
                .addAll(facts)
        }
        for ((edge, reasons) in data.reasons) {
            reasonsByEdge.getOrPut(edge, ::hashSetOf)
                .addAll(reasons)
        }
        results.addAll(data.results)
    }
    val mergedData = IfdsComputationData(
        edgesByEnd,
        factsByStmt,
        reasonsByEdge,
        results
    )
    return mergedData
}