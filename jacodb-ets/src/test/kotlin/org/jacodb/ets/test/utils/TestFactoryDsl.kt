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

package org.jacodb.ets.test.utils

import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.function.Executable
import java.util.stream.Stream

private interface TestProvider {
    fun test(name: String, test: () -> Unit)
}

private interface ContainerProvider {
    fun container(name: String, init: TestContainerBuilder.() -> Unit)
}

class TestContainerBuilder(var name: String) : TestProvider, ContainerProvider {
    private val nodes: MutableList<DynamicNode> = mutableListOf()

    override fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    override fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += containerBuilder(name, init)
    }

    fun build(): DynamicContainer = DynamicContainer.dynamicContainer(name, nodes)
}

private fun containerBuilder(name: String, init: TestContainerBuilder.() -> Unit): DynamicContainer =
    TestContainerBuilder(name).apply(init).build()

class TestFactoryBuilder : TestProvider, ContainerProvider {
    private val nodes: MutableList<DynamicNode> = mutableListOf()

    override fun test(name: String, test: () -> Unit) {
        nodes += dynamicTest(name, test)
    }

    override fun container(name: String, init: TestContainerBuilder.() -> Unit) {
        nodes += containerBuilder(name, init)
    }

    fun build(): Stream<out DynamicNode> = nodes.stream()
}

fun testFactory(init: TestFactoryBuilder.() -> Unit): Stream<out DynamicNode> =
    TestFactoryBuilder().apply(init).build()

private fun dynamicTest(name: String, test: () -> Unit): DynamicTest =
    DynamicTest.dynamicTest(name, Executable(test))
