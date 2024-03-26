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

package org.jacodb.ifds.actors

import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.ActorRef
import org.jacodb.ifds.domain.Analyzer
import org.jacodb.ifds.messages.AnalyzerMessage
import org.jacodb.ifds.messages.CommonMessage

context(ActorContext<AnalyzerMessage<Stmt, Fact>>)
class Runner<Stmt, Fact>(
    private val analyzer: Analyzer<Stmt, Fact>,
    private val parent: ActorRef<CommonMessage>,
) : Actor<AnalyzerMessage<Stmt, Fact>> {

    override suspend fun receive(message: AnalyzerMessage<Stmt, Fact>) {
        val newMessages = analyzer.step(message)
        for (newMessage in newMessages) {
            parent.send(newMessage)
        }
    }
}