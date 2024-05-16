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

import kotlinx.coroutines.channels.Channel
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorPath
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.impl.ActorSystemImpl
import kotlin.coroutines.CoroutineContext

internal typealias WorkerFactory<Message> =
        (ActorPath, Channel<Message>, ActorSystemImpl<*>) -> ActorWorker<Message>

internal abstract class ActorWorker<Message>(
    path: ActorPath,
) : ActorRef<Message>(path) {
    abstract fun launchLoop(
        actor: Actor<Message>,
    )

    abstract fun stop()
    abstract fun resume()

    abstract suspend fun <TargetMessage> send(
        destination: ActorRef<TargetMessage>,
        message: TargetMessage,
    )
}
