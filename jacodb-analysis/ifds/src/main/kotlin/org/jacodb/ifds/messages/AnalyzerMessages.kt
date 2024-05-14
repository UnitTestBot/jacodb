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

interface AnalyzerMessage<Stmt, Fact> : RunnerMessage

data class StartAnalysis<Method>(
    override val runnerId: RunnerId,
    val method: Method,
) : AnalyzerMessage<Nothing, Nothing>

data class EdgeMessage<Stmt, Fact>(
    override val runnerId: RunnerId,
    val edge: Edge<Stmt, Fact>,
) : AnalyzerMessage<Stmt, Fact>

data class ResolvedCall<Stmt, Fact, Method>(
    override val runnerId: RunnerId,
    val edge: Edge<Stmt, Fact>,
    val method: Method,
) : AnalyzerMessage<Stmt, Fact>

data class NotificationOnStart<Stmt, Fact>(
    val subscriber: RunnerId,
    val author: RunnerId,
    val summaryEdge: Edge<Stmt, Fact>,
    val subscribingEdge: Edge<Stmt, Fact>,
) : AnalyzerMessage<Stmt, Fact> {
    override val runnerId: RunnerId
        get() = subscriber
}

data class NotificationOnEnd<Stmt, Fact>(
    val subscriber: RunnerId,
    val author: RunnerId,
    val summaryEdge: Edge<Stmt, Fact>,
    val subscribingEdge: Edge<Stmt, Fact>,
) : AnalyzerMessage<Stmt, Fact> {
    override val runnerId: RunnerId
        get() = subscriber
}
