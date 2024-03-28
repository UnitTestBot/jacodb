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

import org.jacodb.actors.api.Actor
import org.jacodb.actors.api.ActorContext
import org.jacodb.actors.api.ActorRef
import org.jacodb.actors.api.options.ChannelFactory.Companion.buffered
import org.jacodb.actors.api.options.SpawnOptions
import org.jacodb.actors.impl.routing.firstReadyRouter
import org.jacodb.actors.impl.systemOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import mu.KotlinLogging.logger
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.system.measureTimeMillis

const val CNT = 1_000_000
const val SLEEP = 0L
const val PRODUCERS = 10

class Msg(val id: Int, val x: Int)

sealed interface ConsumerMessage {
    data class Ack(val rendezvous: Channel<IntArray>) : ConsumerMessage
    data class Msg(val id: Int, val x: Int) : ConsumerMessage
}

private var sums = AtomicIntegerArray(PRODUCERS)

class Consumer(
    total: Int,
) : Actor<ConsumerMessage> {

    override suspend fun receive(message: ConsumerMessage) {
        when (message) {
            is ConsumerMessage.Msg -> sums.addAndGet(message.id, message.x)
            is ConsumerMessage.Ack -> {}
        }
    }
}

context(ActorContext<Unit>)
class Producer(
    private val consumer: ActorRef<ConsumerMessage>,
    private val iterations: Int,
    private val sleep: Long,
    private val id: Int,
) : Actor<Unit> {

    override suspend fun receive(message: Unit) {
        repeat(iterations) {
            consumer.send(ConsumerMessage.Msg(id, 1))
            delay(sleep)
        }
    }
}


sealed interface RootMessage {
    object Start : RootMessage
    data class GetArray(val rendezvous: Channel<IntArray>) : RootMessage
}

context(ActorContext<RootMessage>)
class Root : Actor<RootMessage> {
    //    private val consumers = spawn("consumer") { Consumer(PRODUCERS) }
    private val consumers = spawn("consumer", factory = firstReadyRouter(PRODUCERS) {
        Consumer(PRODUCERS)
    }
    )

    private val producers = List(PRODUCERS) {
        spawn(
            "producer#$it",
            SpawnOptions.default.channelFactory(buffered(32768))
        ) {
            Producer(consumers, iterations = CNT, sleep = SLEEP, it)
        }
    }

    override suspend fun receive(message: RootMessage) {
        when (message) {
            RootMessage.Start -> {
                for (producer in producers) {
                    producer.send(Unit)
                }
            }

            is RootMessage.GetArray -> {
                consumers.send(ConsumerMessage.Ack(message.rendezvous))
            }
        }
    }
}

private val logger = logger("Main")

suspend fun main() {
    val system = systemOf("example", SpawnOptions.default, ::Root)
    val ms = measureTimeMillis {
        system.send(RootMessage.Start)
        system.awaitTermination()
        logger.info { List(PRODUCERS) { sums.get(it) } }
    }
    logger.info { "Finished in $ms" }
}
