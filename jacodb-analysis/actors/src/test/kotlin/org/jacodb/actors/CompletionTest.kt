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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jacodb.actors.api.Actor
import org.jacodb.actors.impl.system
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CompletionTest {
    @Test
    fun `a failed ask reply does not cancel subsequent requests`() = runBlocking {
        val failure = IllegalStateException("request failed")
        var failNext = true
        val system = system<CompletableDeferred<Unit>>(name = "failed-reply") {
            object : Actor<CompletableDeferred<Unit>> {
                override suspend fun receive(message: CompletableDeferred<Unit>) {
                    if (failNext) {
                        failNext = false
                        message.completeExceptionally(failure)
                    } else {
                        message.complete(Unit)
                    }
                }
            }
        }
        try {
            val thrown = assertFailsWith<IllegalStateException> { system.ask<Unit> { it } }
            assertEquals(failure.message, thrown.message)

            val completed = withTimeoutOrNull(1.seconds) { system.ask<Unit> { it }; true }

            assertNotNull(completed)
        } finally {
            system.close()
        }
        Unit
    }

    @Test
    fun `notifies all concurrent completion waiters`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val system = system<Unit>(name = "concurrent-await") {
            object : Actor<Unit> {
                override suspend fun receive(message: Unit) {
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        val waiters = mutableListOf<kotlinx.coroutines.Job>()
        try {
            system.send(Unit)
            entered.await()
            repeat(2) {
                waiters += launch(start = CoroutineStart.UNDISPATCHED) { system.awaitCompletion() }
            }

            release.complete(Unit)
            val completed = withTimeoutOrNull(1.seconds) { waiters.joinAll(); true }

            assertNotNull(completed)
        } finally {
            system.close()
            waiters.forEach { it.cancelAndJoin() }
        }
        Unit
    }

    @Test
    fun `closing the system cancels outstanding ask requests`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Boolean>()
        val system = system<CompletableDeferred<Unit>>(name = "close-ask") {
            object : Actor<CompletableDeferred<Unit>> {
                override suspend fun receive(message: CompletableDeferred<Unit>) {
                    entered.complete(Unit)
                    release.await()
                    message.complete(Unit)
                }
            }
        }
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                system.ask<Unit> { it }
                cancelled.complete(false)
            } catch (_: CancellationException) {
                cancelled.complete(true)
            }
        }
        try {
            entered.await()

            system.close()
            val result = withTimeoutOrNull(1.seconds) { cancelled.await() }

            assertEquals(true, result)
        } finally {
            system.close()
            waiter.cancelAndJoin()
        }
    }

    @Test
    fun `closing the system cancels outstanding completion waiters`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Boolean>()
        val system = system<Unit>(name = "close-await") {
            object : Actor<Unit> {
                override suspend fun receive(message: Unit) {
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        system.send(Unit)
        entered.await()
        val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                system.awaitCompletion()
                cancelled.complete(false)
            } catch (_: CancellationException) {
                cancelled.complete(true)
            }
        }
        try {
            system.close()
            val result = withTimeoutOrNull(1.seconds) { cancelled.await() }

            assertEquals(true, result)
        } finally {
            system.close()
            waiter.cancelAndJoin()
        }
    }

    @Test
    fun `an ask builder failure does not count an unsent message`() = runBlocking {
        val failure = IllegalStateException("builder failed")
        val system = system<Unit>(name = "ask-builder") {
            object : Actor<Unit> { override suspend fun receive(message: Unit) {} }
        }
        try {
            try {
                system.ask<Unit> { throw failure }
                error("ask should fail")
            } catch (error: IllegalStateException) {
                assertTrue(error === failure)
            }

            val completed = withTimeoutOrNull(1.seconds) { system.awaitCompletion(); true }

            assertNotNull(completed)
        } finally {
            system.close()
        }
        Unit
    }
}
