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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import mu.KLogger
import mu.KotlinLogging.logger
import org.jacodb.actors.api.ActorFactory
import org.jacodb.actors.api.ActorSystem
import org.jacodb.actors.api.options.SpawnOptions
import org.jacodb.actors.impl.actors.internal.WatcherActor
import org.jacodb.actors.impl.actors.internal.WatcherMessage

internal class ActorSystemImpl<Message>(
    override val name: String,
    systemOptions: ActorSystemOptions,
    spawnOptions: SpawnOptions,
    actorFactory: ActorFactory<Message>,
) : ActorSystem<Message>, AutoCloseable {
    private val path = root() / name

    override val logger: KLogger = logger(path.toString())

    private val spawner = ActorSpawnerImpl(path, this)

    internal val scope = CoroutineScope(Job())

    internal val watcher = spawner.spawnInternalActor(WATCHER_ACTOR_NAME, SpawnOptions.default) {
        WatcherActor(systemOptions.printStatisticsPeriod)
    }

    private val user = spawner.spawn(USER_ACTOR_NAME, spawnOptions, actorFactory)

    override suspend fun send(message: Message) {
        withSystemLifetime {
            deliver(message)
        }
    }

    override suspend fun <R> ask(messageBuilder: (CompletableDeferred<R>) -> Message): R =
        withSystemLifetime { requestJob ->
            val deferred = CompletableDeferred<R>(parent = requestJob)
            val ack = messageBuilder(deferred)
            deliver(ack)
            deferred.await()
        }

    private suspend fun deliver(message: Message) {
        watcher.receive(WatcherMessage.OutOfSystemSend)
        try {
            user.receive(message)
        } catch (failure: Throwable) {
            // UserActorWorker.receive never suspends after successful trySend, so a failure
            // here means no message was admitted, even under prompt cancellation.
            withContext(NonCancellable) {
                if (scope.isActive) {
                    watcher.receive(WatcherMessage.OutOfSystemSendCancelled)
                }
            }
            throw failure
        }
    }

    private suspend fun <R> withSystemLifetime(action: suspend (Job) -> R): R {
        scope.ensureActive()
        val requestJob = SupervisorJob(parent = scope.coroutineContext.job)
        try {
            // Delivery can suspend before a reply exists. The child follows system close,
            // while the caller awaits in its own context and cancels the child in finally.
            val requestScope = CoroutineScope(currentCoroutineContext() + requestJob)
            return requestScope.async(start = CoroutineStart.UNDISPATCHED) {
                action(requestJob)
            }.await()
        } finally {
            requestJob.cancel()
        }
    }

    override suspend fun awaitCompletion() {
        scope.ensureActive()
        val ready = CompletableDeferred<Unit>(parent = scope.coroutineContext.job)
        try {
            watcher.receive(WatcherMessage.AwaitTermination(ready))
            ready.await()
        } finally {
            ready.cancel()
        }
    }

    override suspend fun resume() {
        spawner.resumeChild(USER_ACTOR_NAME)
    }

    override fun stop() {
        spawner.stopChild(USER_ACTOR_NAME)
    }

    companion object {
        private const val USER_ACTOR_NAME = "user"
        private const val WATCHER_ACTOR_NAME = "watcher"
    }

    override fun close() {
        scope.cancel()
    }
}

fun <Message> system(
    name: String,
    systemOptions: ActorSystemOptions = ActorSystemOptions(),
    spawnOptions: SpawnOptions = SpawnOptions.default,
    actorFactory: ActorFactory<Message>,
): ActorSystem<Message> = ActorSystemImpl(
    name,
    systemOptions,
    spawnOptions,
    actorFactory
)
