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

package org.jacodb.analysis.ifds

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

interface Producer<T> {
    fun produce(event: T)
    fun subscribe(consumer: Consumer<T>)
}

fun interface Consumer<in T> {
    fun consume(event: T)
}

class SyncProducer<T> : Producer<T> {
    private val consumers: MutableList<Consumer<T>> = mutableListOf()
    private val events: MutableList<T> = mutableListOf()

    @Synchronized
    override fun produce(event: T) {
        for (consumer in consumers) {
            consumer.consume(event)
        }
        events.add(event)
    }

    @Synchronized
    override fun subscribe(consumer: Consumer<T>) {
        for (event in events) {
            consumer.consume(event)
        }
        consumers.add(consumer)
    }
}

sealed interface ConsList<out T> : Iterable<T>

object Nil : ConsList<Nothing> {
    override fun iterator(): Iterator<Nothing> = object : Iterator<Nothing> {
        override fun hasNext(): Boolean = false
        override fun next(): Nothing {
            throw NoSuchElementException()
        }
    }

    override fun toString(): String = javaClass.simpleName
}

data class Cons<out T>(
    val value: T,
    val tail: ConsList<T>,
) : ConsList<T> {
    override fun iterator(): Iterator<T> = Iter(this)

    private class Iter<T>(private var list: ConsList<T>) : Iterator<T> {
        override fun hasNext(): Boolean = list !is Nil

        override fun next(): T = when (val list = list) {
            is Nil -> throw NoSuchElementException()
            is Cons -> {
                val value = list.value
                this.list = list.tail
                value
            }
        }
    }
}

class NonBlockingQueue<T> {
    data class Node<T>(
        val value: T,
        @Volatile var next: Node<T>? = null,
    )

    var head: Node<T>? = null
        private set
    val tail: AtomicReference<Node<T>> = AtomicReference(head)
    val size: AtomicInteger = AtomicInteger(0)

    fun add(element: T) {
        val node = Node(element)
        var currentTail: Node<T>?
        while (true) {
            currentTail = tail.get()
            if (tail.compareAndSet(currentTail, node)) break
        }
        if (currentTail != null) {
            currentTail.next = node
        } else {
            head = node
        }
        size.incrementAndGet()
    }
}

class ConcurrentProducer<T> : Producer<T> {
    private var consumers: AtomicReference<ConsList<Consumer<T>>> = AtomicReference(Nil)
    private val events: NonBlockingQueue<T> = NonBlockingQueue()

    override fun produce(event: T) {
        var currentConsumers: ConsList<Consumer<T>>
        while (true) {
            currentConsumers = consumers.get() ?: continue
            if (consumers.compareAndSet(currentConsumers, null)) break
        }

        events.add(event)

        try {
            for (consumer in currentConsumers) {
                consumer.consume(event)
            }
        } finally {
            check(consumers.compareAndSet(null, currentConsumers))
        }
    }

    override fun subscribe(consumer: Consumer<T>) {
        var last: NonBlockingQueue.Node<T>? = null
        while (true) {
            val start = if (last != null) last.next else events.head
            var current = start
            while (current != null) {
                last = current
                consumer.consume(current.value)
                current = current.next
            }

            val currentConsumers = consumers.get() ?: continue
            if (!consumers.compareAndSet(currentConsumers, null)) continue
            if (events.tail.get() === last) {
                val newConsumers = Cons(consumer, currentConsumers)
                check(consumers.compareAndSet(null, newConsumers))
                break
            } else {
                check(consumers.compareAndSet(null, currentConsumers))
            }
        }
    }
}
