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

import greeter.GreeterClient
import greeter.HelloRequest
import mu.KotlinLogging
import org.jacodb.ets.service.GreeterService
import org.jacodb.ets.service.createGrpcClient
import org.jacodb.ets.service.grpcServer
import kotlin.test.Test

private val logger = KotlinLogging.logger {}

class WireTest {
    companion object {
        private const val PORT = 7777
    }

    @Test
    fun `test Greeter`() {
        val server = grpcServer(PORT) {
            addService(GreeterService())
        }
        server.start()
        logger.info { "Server listening on port ${server.port}" }

        val client = createGrpcClient<GreeterClient>(PORT)

        val request = HelloRequest(name = "Kotlin")
        logger.info { "Sending $request" }
        val response = client.SayHello().executeBlocking(request)
        logger.info { "Received $response" }

        logger.info { "Shutting down server..." }
        server.shutdown()
        server.awaitTermination()
        logger.info { "Server shut down" }
    }
}
