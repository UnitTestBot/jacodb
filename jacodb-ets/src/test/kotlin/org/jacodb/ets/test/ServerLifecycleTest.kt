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

package org.jacodb.ets.test

import org.jacodb.ets.grpc.Server
import org.jacodb.ets.grpc.startServer
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ServerLifecycleTest {
    @Test
    fun `stopping a ready server joins both output readers`() {
        val process = ProcessBuilder("/bin/sleep", "30").start()
        val server = startServer(process = process, port = 7777, awaitReady = {})
        try {
            server.stop()

            assertFalse(process.isAlive)
            assertFalse(server.outputThread.isAlive)
            assertFalse(server.errorThread.isAlive)
        } finally {
            process.destroyForcibly().waitFor()
        }
    }

    @Test
    fun `interrupted stop still reaps and joins before propagating`() {
        val process = ProcessBuilder("/bin/sleep", "30").start()
        val server = startServer(process = process, port = 7777, awaitReady = {})
        try {
            Thread.currentThread().interrupt()

            assertFailsWith<InterruptedException> { server.stop() }

            assertFalse(process.isAlive)
            assertFalse(server.outputThread.isAlive)
            assertFalse(server.errorThread.isAlive)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
            process.destroyForcibly().waitFor()
        }
    }

    @Test
    fun `readiness failure cleans up the started process`() {
        val process = ProcessBuilder("/bin/sleep", "30").start()
        val failure = IllegalStateException("not ready")
        try {
            val thrown = assertFailsWith<IllegalStateException> {
                startServer(process = process, port = 7777, awaitReady = { throw failure })
            }

            assertSame(failure, thrown)
            assertFalse(process.isAlive, "Failed startup must reap its process")
        } finally {
            process.destroyForcibly().waitFor()
        }
    }

    @Test
    fun `readiness interruption cleans up and preserves interruption`() {
        val process = ProcessBuilder("/bin/sleep", "30").start()
        val failure = InterruptedException("startup cancelled")
        try {
            val thrown = assertFailsWith<InterruptedException> {
                startServer(process = process, port = 7777, awaitReady = { throw failure })
            }

            assertSame(failure, thrown)
            assertFalse(process.isAlive, "Interrupted startup must reap its process")
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
            process.destroyForcibly().waitFor()
        }
    }

    @Test
    fun `stop forcefully reaps a process that ignores graceful termination`() {
        val process = ProcessBuilder("/bin/sh", "-c", "trap '' TERM; printf 'ready\\n'; while :; do :; done").start()
        process.inputStream.bufferedReader().readLine()
        val server = Server(process = process, outputThread = Thread(), errorThread = Thread())
        val failure = AtomicReference<Throwable?>()
        val stopper = thread {
            try {
                server.stop()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        try {
            stopper.join(2000)

            assertFalse(stopper.isAlive, "Stop must not wait forever after SIGTERM is ignored")
            assertFalse(process.isAlive)
            failure.get()?.let { throw it }
        } finally {
            process.destroyForcibly().waitFor()
            stopper.join(2000)
        }
    }
}
