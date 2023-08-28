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

package org.jacodb.analysis.engine

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import java.util.concurrent.ConcurrentHashMap

class IfdsRunnerDependencyController<UnitType> {
    sealed interface Event<UnitType>

    data class NewRunner<UnitType>(val runner: IfdsUnitRunner<UnitType>) : Event<UnitType>
    data class NewDependency<UnitType>(val consumer: UnitType, val producer: UnitType) : Event<UnitType>
    data class QueueStatusUpdate<UnitType>(val unit: UnitType, val isEmpty: Boolean) : Event<UnitType>

    val eventChannel: SendChannel<Event<UnitType>>

    private val eventReceiveChannel: ReceiveChannel<Event<UnitType>>

    init {
        val channel = Channel<Event<UnitType>>(capacity = Int.MAX_VALUE)
        eventChannel = channel
        eventReceiveChannel = channel
    }

    private val runners: MutableMap<UnitType, IfdsUnitRunner<UnitType>> = ConcurrentHashMap()
    private val edges: MutableMap<UnitType, MutableSet<UnitType>> = ConcurrentHashMap()
    private val queueIsEmpty: MutableMap<UnitType, Boolean> = ConcurrentHashMap()

    private fun dfs(v: UnitType, visited: MutableSet<UnitType>, dl: Int = 100): Boolean {
        if (queueIsEmpty[v] == false) {
            return true
        }
        if (dl == 0 || v in visited) {
            return false
        }
        visited.add(v)
        for (u in edges[v].orEmpty()) {
            if (dfs(u, visited, dl - 1)) {
                return true
            }
        }
        return false
    }

    suspend fun dispatch() = coroutineScope {
        for (event in eventReceiveChannel) {
            if (!isActive) {
                break
            }
            when (event) {
                is NewDependency -> {
                    edges.getOrPut(event.consumer) { ConcurrentHashMap.newKeySet() }.add(event.producer)
                }
                is NewRunner -> {
                    runners[event.runner.unit] = event.runner
                    queueIsEmpty[event.runner.unit] = false
                }
                is QueueStatusUpdate -> {
                    if (event.unit !in runners) {
                        continue
                    }
                    queueIsEmpty[event.unit] = event.isEmpty
                    if (event.isEmpty) {
//                          runners[event.unit]!!.job.cancel()
                        for ((unit, runner) in runners.toMap()) {
                            if (!dfs(unit, mutableSetOf())) {
                                runner.job.cancel()
                                runners.remove(unit)
                            }
                        }
                    }
                }
            }
        }
    }
}