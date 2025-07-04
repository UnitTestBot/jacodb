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

package org.jacodb.ets.service

import greeter.GreeterBlockingServer
import greeter.GreeterWireGrpc
import greeter.HelloReply
import greeter.HelloRequest
import io.grpc.stub.StreamObserver

private val logger = mu.KotlinLogging.logger {}

class GreeterImpl : GreeterBlockingServer {
    override fun SayHello(request: HelloRequest): HelloReply {
        return HelloReply(message = "Hello, ${request.name}!")
    }
}

class GreeterService : GreeterWireGrpc.GreeterImplBase() {
    private val impl = GreeterImpl()

    override fun SayHello(request: HelloRequest, response: StreamObserver<HelloReply>) {
        logger.info { "Received $request" }
        val reply = impl.SayHello(request)
        logger.info { "Sending $reply" }
        response.onNext(reply)
        response.onCompleted()
    }
}

fun main() {
    val port = 7777
    val server = grpcServer(port) {
        addService(GreeterService())
    }
    server.start()
    println("Server listening on port ${server.port}")
    server.awaitTermination()
}
