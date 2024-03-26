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

import org.jacodb.actors.api.ActorSystem
import org.jacodb.actors.api.Factory
import org.jacodb.actors.api.options.SpawnOptions
import org.jacodb.actors.impl.actors.WatcherActor
import org.jacodb.actors.impl.actors.WatcherMessage
import org.jacodb.actors.impl.workers.internalActorWorkerFactory
import org.jacodb.actors.impl.workers.userActorWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel

internal class ActorSystemImpl<Message>(
    override val name: String,
    options: SpawnOptions,
    factory: Factory<Message>,
) : ActorSystem<Message> {
    private val path = root() / name

    private val userSpawner = ActorSpawnerImpl(path, this, userActorWorkerFactory)
    private val internalSpawner = ActorSpawnerImpl(path, this, internalActorWorkerFactory)

    internal val scope = CoroutineScope(SupervisorJob())

    internal val watcher = internalSpawner.spawn("watcher", factory = ::WatcherActor)

    private val user = userSpawner.spawn("usr", options, factory)

    override suspend fun send(message: Message) {
        watcher.send(WatcherMessage.OutOfSystemSend)
        user.send(message)
    }

    override suspend fun <R> ack(messageBuilder: (Channel<R>) -> Message): R {
        watcher.send(WatcherMessage.OutOfSystemSend)
        val channel = Channel<R>(Channel.RENDEZVOUS)
        val ack = messageBuilder(channel)
        user.send(ack)
        val received = channel.receive()
        return received
    }

    private val channel = Channel<Unit>(capacity = Channel.RENDEZVOUS)

    override suspend fun awaitTermination() {
        watcher.send(WatcherMessage.AwaitTermination(channel))
        channel.receive()
        watcher.send(WatcherMessage.Idle)
    }
}

fun <Message> systemOf(
    name: String,
    options: SpawnOptions = SpawnOptions.default(),
    factory: Factory<Message>,
): ActorSystem<Message> = ActorSystemImpl(
    name,
    options,
    factory
)