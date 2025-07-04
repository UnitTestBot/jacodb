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

import com.squareup.wire.GrpcClient
import com.squareup.wire.Service
import okhttp3.OkHttpClient
import okhttp3.Protocol

const val DEFAULT_PORT = 7777

/**
 * Creates a gRPC client with the specified port.
 *
 * ### Example:
 * ```kotlin
 * val client = grpcClient(7777)
 * ```
 *
 * ### Note:
 * You probably want to use [createGrpcClient] instead,
 * which is more convenient for creating specific clients.
 *
 * @param port The port on which the gRPC server is running.
 * @return A configured [GrpcClient] instance.
 */
fun grpcClient(port: Int = DEFAULT_PORT): GrpcClient {
    val okClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
        .build()
    return GrpcClient.Builder()
        .client(okClient)
        .baseUrl("http://0.0.0.0:$port")
        .build()
}

/**
 * Creates a gRPC client for the specified service type.
 *
 * ### Example:
 * ```kotlin
 * val client = createGrpcClient<MyServiceClient>(7777)
 * ```
 *
 * @param port The port on which the gRPC server is running.
 * @return A configured instance of the specified service type [T].
 */
inline fun <reified T : Service> createGrpcClient(port: Int = DEFAULT_PORT): T {
    return grpcClient(port).create()
}
