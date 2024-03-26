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

package org.jacodb.ifds.domain

import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.Factory
import org.jacodb.ifds.messages.CommonMessage
import org.jacodb.ifds.messages.IndirectionMessage

interface IfdsContext<Stmt, Fact> {
    fun chunkByMessage(message: CommonMessage): Chunk
    fun runnerTypeByMessage(message: CommonMessage): RunnerType

    fun getAnalyzer(chunk: Chunk, type: RunnerType): Analyzer<Stmt, Fact>
    fun indirectionHandlerFactory(parent: ActorRef<CommonMessage>): Factory<IndirectionMessage>
}