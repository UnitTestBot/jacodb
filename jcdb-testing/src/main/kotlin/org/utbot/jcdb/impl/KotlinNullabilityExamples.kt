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

package org.utbot.jcdb.impl

class KotlinNullabilityExamples {
    class SomeContainer<E>(
        val listOfNotNull: List<E>,
        val listOfNullable: List<E?>,
        val notNullProperty: E,
        val nullableProperty: E?,
    )

    fun simpleGenerics(
        matrixOfNotNull: SomeContainer<SomeContainer<Int>>,
        matrixOfNullable: SomeContainer<SomeContainer<Int?>>,
        containerOfNotNullContainers: SomeContainer<SomeContainer<Int>?>
    ) = Unit

    fun SomeContainer<SomeContainer<Int?>?>.extensionFunction() = Unit

    fun genericsWithProjection(
        covariant: SomeContainer<out String?>,
        contravariant: SomeContainer<in String>,
        star: SomeContainer<*>
    ) = Unit

    fun javaArrays(nullable: IntArray?, notNull: Array<SomeContainer<Int>>) = Unit

    fun <T> typeVariableParameters(notNull: T, nullable: T?) = Unit

    fun <A: List<Int?>, B: List<Int>?> typeVariableDeclarations() = Unit

    fun instantiatedContainer(byNotNull: SomeContainer<String>, byNullable: SomeContainer<String?>) = Unit

    interface SomeContainerProducerI<P> {
        fun produceContainer(): SomeContainer<P>
    }

    lateinit var someContainerProducer: SomeContainerProducerI<Int?>
}