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
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.options.SpawnOptions

internal typealias KeyRouteeFactory<Message, Key> = ActorContext<Message>.(Key) -> Actor<Message>

context(ActorContext<Message>)
internal class MessageKeyRouter<Message, Key>(
    private val keyExtractor: (Message) -> Key,
    private val routeeNameFactory: (Key) -> String,
    private val routeeSpawnOptions: SpawnOptions,
    private val routeeFactory: KeyRouteeFactory<Message, Key>,
) : Actor<Message> {
    private val routees = hashMapOf<Key, ActorRef<Message>>()

    override suspend fun receive(message: Message) {
        val key = keyExtractor(message)
        val routee = routees.computeIfAbsent(key) {
            val name = routeeNameFactory(key)
            spawn(name, routeeSpawnOptions) { routeeFactory(key) }
        }
        routee.send(message)
    }
}
