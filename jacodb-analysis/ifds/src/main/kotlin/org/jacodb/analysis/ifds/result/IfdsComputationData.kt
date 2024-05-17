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

/**
 * Aggregates all facts and edges found by the tabulation algorithm.
 */
data class IfdsComputationData<Stmt, Fact, F : Finding<Stmt, Fact>>(
    val edgesByEnd: Map<Vertex<Stmt, Fact>, Collection<Edge<Stmt, Fact>>>,
    val factsByStmt: Map<Stmt, Collection<Fact>>,
    val reasonsByEdge: Map<Edge<Stmt, Fact>, Collection<Reason<Stmt, Fact>>>,
    val findings: Collection<F>,
)
