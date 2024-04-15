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

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.impl.systemOf
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class StoppingTest {
    context(ActorContext<Int>)
    class Repeater(
        private val parent: ActorRef<Int>?,
        depth: Int,
    ) : Actor<Int> {
        private val child: ActorRef<Int>? =
            if (depth > 0) {
                spawn("child") { Repeater(this@ActorContext.self, depth - 1) }
            } else {
                null
            }

        override suspend fun receive(message: Int) {
            delay(1)
            child?.send(message + 1)
            parent?.send(message + 1)
        }
    }

    @Test
    fun testStops() = runBlocking {
        val system = systemOf("test") { Repeater(null, 2) }

        system.send(0)
        val job = launch {
            system.awaitCompletion()
        }
        delay(50.milliseconds)

        assertTrue(job.isActive)

        system.stop()

        val result = withTimeoutOrNull(50.milliseconds) {
            job.join()
        }
        assertNotNull(result)
    }
}