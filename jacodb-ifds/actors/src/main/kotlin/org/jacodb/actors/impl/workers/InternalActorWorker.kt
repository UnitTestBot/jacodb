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

package org.jacodb.actors.impl.workers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.impl.ActorRefImpl
import kotlin.coroutines.CoroutineContext

internal class InternalActorWorker<Message>(
    override val self: ActorRef<Message>,
    override val channel: Channel<Message>,
    private val scope: CoroutineScope,
) : ActorWorker<Message> {
    override fun launchLoop(
        coroutineContext: CoroutineContext,
        actor: Actor<Message>,
    ) {
        scope.launch(coroutineContext) {
            loop(actor)
        }
    }

    override suspend fun <TargetMessage> send(ref: ActorRefImpl<TargetMessage>, message: TargetMessage) {
        ref.send(message)
    }

    private suspend fun loop(
        actor: Actor<Message>,
    ) {
        for (message in channel) {
            actor.receive(message)
        }
    }
}
