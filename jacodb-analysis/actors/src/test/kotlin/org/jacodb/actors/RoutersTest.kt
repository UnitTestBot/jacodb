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

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.impl.routing.messageKeyRouter
import org.jacodb.actors.impl.routing.randomRouter
import org.jacodb.actors.impl.routing.roundRobinRouter
import org.jacodb.actors.impl.system
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class RoutersTest {
    private val log = ConcurrentLinkedDeque<Pair<Int, Int>>()

    context(ActorContext<Int>)
    class Logger(
        private val selfId: Int,
        private val log: ConcurrentLinkedDeque<Pair<Int, Int>>,
    ) : Actor<Int> {
        override suspend fun receive(message: Int) {
            log.add(selfId to message)
        }
    }

    @Test
    fun `Test message key router`() = runBlocking {
        val system = system<Int>(
            "test",
            actorFactory = messageKeyRouter(keyExtractor = { a -> a % 4 }) {
                Logger(it, log)
            }
        )


        repeat(12) { msg ->
            delay(20.milliseconds)
            system.send(msg)
        }

        system.awaitCompletion()

        val expectedLog = List(12) { (it % 4) to it }
        assertEquals(expectedLog, log.toList())
    }

    @Test
    fun `Test round robin router`() = runBlocking {
        val size = 4

        var idx = 0

        val system = system<Int>(
            "test",
            actorFactory = roundRobinRouter(size = size) {
                Logger(idx++, log)
            }
        )


        repeat(12) { msg ->
            delay(20.milliseconds)
            system.send(msg)
        }

        system.awaitCompletion()

        val expectedLog = List(12) { (it % size) to it }
        assertEquals(expectedLog, log.toList())
    }

    @Test
    fun `Test random router`() = runBlocking {
        val size = 4
        val randomSequence = listOf(2, 2, 8, 1, 3, 3, 7, 6).map { it % size }

        val random = mockk<Random> {
            every { nextInt(any()) } returnsMany randomSequence
        }

        var idx = 0

        val system = system<Int>(
            "test",
            actorFactory = randomRouter(size = size, random = random) {
                Logger(idx++, log)
            }
        )


        repeat(randomSequence.size) { msg ->
            delay(20.milliseconds)
            system.send(msg)
        }

        system.awaitCompletion()

        val expectedLog = randomSequence.withIndex().map { (it.value % size) to it.index }
        assertEquals(expectedLog, log.toList())
    }
}