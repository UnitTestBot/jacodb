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

package org.jacodb.actors.impl.actors.internal

import kotlinx.coroutines.CompletableDeferred
import org.jacodb.actors.api.ActorPath
import org.jacodb.actors.api.ActorStatus

internal data class Snapshot(
    val status: ActorStatus,
    val sent: Int,
    val received: Int,
)

internal sealed interface WatcherMessage {

    data object Idle : WatcherMessage

    data class Register(
        val path: ActorPath,
    ) : WatcherMessage

    data object OutOfSystemSend : WatcherMessage

    data class UpdateSnapshot(
        val path: ActorPath,
        val snapshot: Snapshot,
    ) : WatcherMessage

    data class AwaitTermination(
        val computationFinished: CompletableDeferred<Unit>,
    ) : WatcherMessage

    data object Print : WatcherMessage
}
