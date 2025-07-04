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

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService

/**
 * Creates a gRPC server with the specified port and optional reflection service.
 *
 * ### Example:
 * ```kotlin
 * val server = grpcServer(7777) {
 *     addService(MyService())
 * }
 * ```
 *
 * @param port The port on which the server will listen.
 * @param addReflection Whether to add the [ProtoReflectionService] for introspection (see [gRPC Reflection](https://grpc.io/docs/guides/reflection/)).
 * @param setup A lambda to configure the server, where you can add services and other configurations.
 * @return A configured [Server] instance.
 */
fun grpcServer(
    port: Int,
    addReflection: Boolean = true,
    setup: ServerBuilder<*>.() -> Unit,
    // Note: `setup` is not `= {}` by default because you probably want to add at least one service.
): Server = ServerBuilder
    .forPort(port)
    .apply(setup)
    .apply {
        if (addReflection) addService(@Suppress("DEPRECATION") ProtoReflectionService.newInstance())
    }
    .build()
