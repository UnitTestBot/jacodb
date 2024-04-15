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

package org.jacodb.actors.impl

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import org.jacodb.actors.api.ActorSystem
import org.jacodb.actors.api.ActorFactory
import org.jacodb.actors.api.options.SpawnOptions
import org.jacodb.actors.impl.actors.WatcherActor
import org.jacodb.actors.impl.actors.WatcherMessage

internal class ActorSystemImpl<Message>(
    override val name: String,
    options: SpawnOptions,
    actorFactory: ActorFactory<Message>,
) : ActorSystem<Message> {
    private val path = root() / name

    private val spawner = ActorSpawnerImpl(path, this)

    internal val scope = CoroutineScope(SupervisorJob())

    internal val watcher = spawner.spawnInternalActor(WATCHER_ACTOR_NAME, SpawnOptions.default, ::WatcherActor)

    private val user = spawner.spawn(USER_ACTOR_NAME, options, actorFactory)

    override suspend fun send(message: Message) {
        watcher.receive(WatcherMessage.OutOfSystemSend)
        user.receive(message)
    }

    override suspend fun <R> ask(messageBuilder: (Channel<R>) -> Message): R {
        watcher.receive(WatcherMessage.OutOfSystemSend)
        val channel = Channel<R>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val ack = messageBuilder(channel)
        user.receive(ack)
        val received = channel.receive()
        return received
    }


    override suspend fun awaitCompletion() {
        val ready = CompletableDeferred<Unit>()
        watcher.receive(WatcherMessage.AwaitTermination(ready))
        ready.await()
        watcher.receive(WatcherMessage.Idle)
    }

    override suspend fun resume() {
        spawner.resumeChild(USER_ACTOR_NAME)
    }

    override fun stop() {
        spawner.stopChild(USER_ACTOR_NAME)
    }

    companion object {
        private const val USER_ACTOR_NAME = "usr"
        private const val WATCHER_ACTOR_NAME = "watcher"
    }
}

fun <Message> systemOf(
    name: String,
    options: SpawnOptions = SpawnOptions.default,
    actorFactory: ActorFactory<Message>,
): ActorSystem<Message> = ActorSystemImpl(
    name,
    options,
    actorFactory
)
