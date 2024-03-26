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
import org.jacodb.ifds.domain.RunnerType
import org.jacodb.ifds.domain.Vertex
import kotlinx.coroutines.channels.Channel

sealed interface StorageMessage : CommonMessage

data class NewEdge<Stmt, Fact>(
    val runnerType: RunnerType,
    val edge: Edge<Stmt, Fact>,
    val reason: Edge<Stmt, Fact>,
) : StorageMessage

data class NewSummaryEdge<Stmt, Fact>(
    val runnerType: RunnerType,
    val edge: Edge<Stmt, Fact>,
) : StorageMessage

data class SubscriptionOnStart<Stmt, Fact>(
    val runnerType: RunnerType,
    val startVertex: Vertex<Stmt, Fact>,
    val subscriber: RunnerType,
    val data: Edge<Stmt, Fact>,
) : StorageMessage

data class SubscriptionOnEnd<Stmt, Fact>(
    val runnerType: RunnerType,
    val endVertex: Vertex<Stmt, Fact>,
    val subscriber: RunnerType,
    val data: Edge<Stmt, Fact>,
) : StorageMessage

data class NewResult(
    val author: RunnerType,
    val result: Any?,
) : StorageMessage

data class ObtainResults(
    val channel: Channel<List<Any?>>
) : StorageMessage