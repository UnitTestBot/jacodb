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

import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging.logger
import org.jacodb.actors.api.ActorPath
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.ActorSpawner
import org.jacodb.actors.api.Factory
import org.jacodb.actors.api.options.SpawnOptions
import org.jacodb.actors.impl.workers.InternalActorWorker
import org.jacodb.actors.impl.workers.UserActorWorker
import org.jacodb.actors.impl.workers.WorkerFactory

internal class ActorSpawnerImpl(
    private val self: ActorPath,
    private val system: ActorSystemImpl<*>,
) : ActorSpawner {
    private val children = hashMapOf<String, ActorRefImpl<*>>()

    override fun <ChildMessage> spawn(
        name: String,
        options: SpawnOptions,
        factory: Factory<ChildMessage>,
    ): ActorRefImpl<ChildMessage> =
        spawnImpl(name, options, factory) { ref, channel, system ->
            UserActorWorker(ref, channel, system.scope, system.watcher)
        }

    internal fun <ChildMessage> spawnInternalActor(
        name: String,
        options: SpawnOptions,
        factory: Factory<ChildMessage>,
    ): ActorRefImpl<ChildMessage> =
        spawnImpl(name, options, factory) { ref, channel, system ->
            InternalActorWorker(ref, channel, system.scope)
        }

    private fun <ChildMessage> spawnImpl(
        name: String,
        options: SpawnOptions,
        factory: Factory<ChildMessage>,
        workerFactory: WorkerFactory<ChildMessage>,
    ): ActorRefImpl<ChildMessage> {
        @Suppress("UNCHECKED_CAST")
        val channel = options.channelFactory.create() as Channel<ChildMessage>
        val ref = createRef(name, channel)

        val spawner = ActorSpawnerImpl(ref.path, system)

        val worker = workerFactory(ref, channel, system)
        val context = ActorContextImpl(spawner, worker, logger(ref.toString()))
        context.launch(options.coroutineContext, factory)

        return ref
    }

    private fun <ChildMessage> createRef(
        name: String,
        channel: Channel<ChildMessage>,
    ): ActorRefImpl<ChildMessage> {
        if (children[name] != null) {
            error("$self already has $name child")
        }
        val path = self / name
        val ref = ActorRefImpl(path, channel)
        children[name] = ref
        return ref
    }

    override fun child(name: String): ActorRef<*>? =
        children[name]

    override fun children(): Collection<ActorRef<*>> =
        children.values
}
