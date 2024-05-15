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

package org.jacodb.analysis.ifds.messages

import kotlinx.coroutines.CompletableDeferred
import org.jacodb.analysis.ifds.domain.Chunk
import org.jacodb.analysis.ifds.domain.Edge
import org.jacodb.analysis.ifds.domain.Reason
import org.jacodb.analysis.ifds.domain.RunnerId
import org.jacodb.analysis.ifds.domain.Vertex
import org.jacodb.analysis.ifds.result.Finding
import org.jacodb.analysis.ifds.result.IfdsComputationData

sealed interface StorageMessage : RunnerMessage

data class NewEdge<Stmt, Fact>(
    override val runnerId: RunnerId,
    val edge: Edge<Stmt, Fact>,
    val reason: Reason<Stmt, Fact>,
) : StorageMessage

data class NewSummaryEdge<Stmt, Fact>(
    override val runnerId: RunnerId,
    val edge: Edge<Stmt, Fact>,
) : StorageMessage

data class SubscriptionOnStart<Stmt, Fact>(
    override val runnerId: RunnerId,
    val startVertex: Vertex<Stmt, Fact>,
    val subscriber: RunnerId,
    val subscribingEdge: Edge<Stmt, Fact>,
) : StorageMessage

data class SubscriptionOnEnd<Stmt, Fact>(
    override val runnerId: RunnerId,
    val endVertex: Vertex<Stmt, Fact>,
    val subscriber: RunnerId,
    val subscribingEdge: Edge<Stmt, Fact>,
) : StorageMessage

data class NewFinding<Stmt, Fact>(
    override val runnerId: RunnerId,
    val finding: Finding<Stmt, Fact>,
) : StorageMessage

data class CollectData<Stmt, Fact, Result : Finding<Stmt, Fact>>(
    val chunk: Chunk,
    override val runnerId: RunnerId,
    val data: CompletableDeferred<IfdsComputationData<Stmt, Fact, Result>>,
) : StorageMessage
