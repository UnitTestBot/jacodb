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

interface AnalyzerMessage<Stmt, Fact> : CommonMessage

data class EdgeMessage<Stmt, Fact>(
    val edge: Edge<Stmt, Fact>
) : AnalyzerMessage<Stmt, Fact>

data class ResolvedCall<Stmt, Fact, Method>(
    val edge: Edge<Stmt, Fact>,
    val method: Method
) : AnalyzerMessage<Stmt, Fact>

data class NotificationOnStart<Stmt, Fact>(
    val runnerType: RunnerType,
    val author: RunnerType,
    val edge: Edge<Stmt, Fact>,
    val data: Edge<Stmt, Fact>,
) : AnalyzerMessage<Stmt, Fact>

data class NotificationOnEnd<Stmt, Fact>(
    val runnerType: RunnerType,
    val author: RunnerType,
    val edge: Edge<Stmt, Fact>,
    val data: Edge<Stmt, Fact>
) : AnalyzerMessage<Stmt, Fact>
