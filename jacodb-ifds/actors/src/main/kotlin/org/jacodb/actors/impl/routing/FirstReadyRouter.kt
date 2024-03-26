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

package org.jacodb.actors.impl.routing

import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.Factory
import org.jacodb.actors.api.options.SpawnOptions
import org.jacodb.actors.impl.UserMessage


context(ActorContext<Message>)
internal class FirstReadyRouter<Message>(
    size: Int,
    routeeSpawnOptions: SpawnOptions,
    routeeFactory: Factory<Message>,
) : Actor<Message> {
    override val flag: Boolean
        get() = false

    private val channel = routeeSpawnOptions.channelFactory.create()

    init {
        val options = routeeSpawnOptions.channel(channel)
        repeat(size) {
            spawn("$it", options, routeeFactory)
        }
    }

    override suspend fun receive(message: Message) {
        channel.send(UserMessage(message))
    }
}
