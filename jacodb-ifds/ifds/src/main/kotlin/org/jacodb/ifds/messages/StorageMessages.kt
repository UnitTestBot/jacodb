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

package org.jacodb.ifds.messages

import org.jacodb.ifds.domain.Edge
import org.jacodb.ifds.domain.RunnerId
import org.jacodb.ifds.domain.Vertex
import kotlinx.coroutines.channels.Channel
import org.jacodb.ifds.domain.Reason
import org.jacodb.ifds.result.IfdsComputationData
import org.jacodb.ifds.result.IfdsResult

sealed interface StorageMessage : CommonMessage

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
    val data: Edge<Stmt, Fact>,
) : StorageMessage

data class SubscriptionOnEnd<Stmt, Fact>(
    override val runnerId: RunnerId,
    val endVertex: Vertex<Stmt, Fact>,
    val subscriber: RunnerId,
    val data: Edge<Stmt, Fact>,
) : StorageMessage

data class NewResult<Stmt, Fact>(
    override val runnerId: RunnerId,
    val result: IfdsResult<Stmt, Fact>,
) : StorageMessage

data class ObtainData<Stmt, Fact, Result : IfdsResult<Stmt, Fact>>(
    override val runnerId: RunnerId,
    val channel: Channel<IfdsComputationData<Stmt, Fact, Result>>,
) : StorageMessage
