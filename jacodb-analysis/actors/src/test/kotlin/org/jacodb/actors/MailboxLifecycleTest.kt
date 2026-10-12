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

package org.jacodb.actors

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.options.SpawnOptions
import org.jacodb.actors.impl.system
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class MailboxLifecycleTest {
    @Test
    fun `close cancels ask waiting for rendezvous or full mailbox delivery`() = runBlocking {
        for (capacity in listOf(Channel.RENDEZVOUS, 1)) {
            assertBlockedDeliveryCloses(capacity = capacity, useAsk = true)
        }
    }

    @Test
    fun `close cancels send waiting for rendezvous or full mailbox delivery`() = runBlocking {
        for (capacity in listOf(Channel.RENDEZVOUS, 1)) {
            assertBlockedDeliveryCloses(capacity = capacity, useAsk = false)
        }
    }

    private suspend fun assertBlockedDeliveryCloses(capacity: Int, useAsk: Boolean) = withTimeout(5.seconds) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val mailbox = Channel<Any?>(capacity = capacity)
        val system = system<CompletableDeferred<Unit>>(
            name = "close-blocked-delivery",
            spawnOptions = SpawnOptions.default.channel(mailbox),
        ) {
            object : Actor<CompletableDeferred<Unit>> {
                override suspend fun receive(message: CompletableDeferred<Unit>) {
                    entered.complete(Unit)
                    release.await()
                    message.complete(Unit)
                }
            }
        }
        system.send(CompletableDeferred())
        entered.await()
        if (capacity > 0) system.send(CompletableDeferred())
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            if (useAsk) system.ask<Unit> { it } else system.send(CompletableDeferred())
        }
        try {
            assertFalse(pending.isCompleted)

            system.close()
            withTimeout(1.seconds) { pending.join() }

            assertFailsWith<CancellationException> { pending.await() }
            val sendResult = mailbox.trySend(CompletableDeferred<Unit>())
            assertFalse(sendResult.isClosed, "The supplied channel must remain owned by its caller")
        } finally {
            system.close()
            pending.cancelAndJoin()
            mailbox.cancel()
        }
    }

    @Test
    fun `canceling a blocked ask does not count an unadmitted message`() = runBlocking {
        for (capacity in listOf(Channel.RENDEZVOUS, 1)) {
            assertCancelledAskCompletes(capacity = capacity, cancelAfterAdmission = false)
        }
    }

    @Test
    fun `canceling an admitted ask preserves its message accounting`() = runBlocking {
        for (capacity in listOf(Channel.RENDEZVOUS, 1)) {
            assertCancelledAskCompletes(capacity = capacity, cancelAfterAdmission = true)
        }
    }

    private suspend fun assertCancelledAskCompletes(capacity: Int, cancelAfterAdmission: Boolean) = withTimeout(5.seconds) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val processed = ConcurrentLinkedQueue<CompletableDeferred<Unit>>()
        val mailbox = Channel<Any?>(capacity = capacity)
        val system = system<CompletableDeferred<Unit>>(
            name = "cancel-ask-delivery",
            spawnOptions = SpawnOptions.default.channel(mailbox),
        ) {
            object : Actor<CompletableDeferred<Unit>> {
                override suspend fun receive(message: CompletableDeferred<Unit>) {
                    processed.add(message)
                    entered.complete(Unit)
                    release.await()
                    message.complete(Unit)
                }
            }
        }
        if (!cancelAfterAdmission) {
            system.send(CompletableDeferred())
            entered.await()
            if (capacity > 0) system.send(CompletableDeferred())
        }
        val reply = CompletableDeferred<CompletableDeferred<Unit>>()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            system.ask<Unit> { deferred ->
                reply.complete(deferred)
                deferred
            }
        }
        try {
            val submittedReply = reply.await()
            if (cancelAfterAdmission) entered.await()

            withTimeout(1.seconds) { pending.cancelAndJoin() }
            release.complete(Unit)
            withTimeout(1.seconds) {
                system.ask<Unit> { it }
                system.awaitCompletion()
            }

            assertEquals(expected = cancelAfterAdmission, actual = processed.contains(submittedReply))
        } finally {
            system.close()
            pending.cancelAndJoin()
            mailbox.cancel()
        }
    }

    @Test
    fun `multiple waiting senders survive cancellation without losing mailbox wakeups`() = runBlocking {
        withTimeout(5.seconds) {
            for (capacity in listOf(Channel.RENDEZVOUS, 1)) {
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val processed = ConcurrentLinkedQueue<Int>()
                val mailbox = Channel<Any?>(capacity = capacity)
                val system = system<Int>(
                    name = "multiple-waiting-senders",
                    spawnOptions = SpawnOptions.default.channel(mailbox),
                ) {
                    object : Actor<Int> {
                        override suspend fun receive(message: Int) {
                            if (message == 0) {
                                entered.complete(Unit)
                                release.await()
                            }
                            processed.add(message)
                        }
                    }
                }
                system.send(message = 0)
                entered.await()
                if (capacity > 0) system.send(message = -1)
                val senders = (1..8).associateWith { message ->
                    async(start = CoroutineStart.UNDISPATCHED) { system.send(message) }
                }
                try {
                    val allSendersAreWaiting = senders.values.all { !it.isCompleted }
                    assertTrue(allSendersAreWaiting)

                    for (message in listOf(2, 4, 6, 8)) {
                        withTimeout(1.seconds) { senders.getValue(message).cancelAndJoin() }
                    }
                    release.complete(Unit)
                    withTimeout(1.seconds) {
                        listOf(1, 3, 5, 7).map { senders.getValue(it) }.awaitAll()
                        system.awaitCompletion()
                    }

                    val expected = if (capacity > 0) listOf(-1, 0, 1, 3, 5, 7) else listOf(0, 1, 3, 5, 7)
                    val actual = processed.toList().sorted()
                    assertEquals(expected = expected, actual = actual)
                } finally {
                    system.close()
                    senders.values.forEach { it.cancelAndJoin() }
                    mailbox.cancel()
                }
            }
        }
    }
}
