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
import org.jacodb.actors.api.ActorFactory
import org.jacodb.actors.api.options.SpawnOptions
import kotlin.random.Random

context(ActorContext<Message>)
internal class RandomRouter<Message>(
    size: Int,
    private val random: Random,
    routeeSpawnOptions: SpawnOptions,
    routeeFactory: ActorFactory<Message>,
) : Actor<Message> {
    private val routees = List(size) {
        spawn("$it", routeeSpawnOptions, routeeFactory)
    }

    override suspend fun receive(message: Message) {
        routees.random(random).send(message)
    }
}
