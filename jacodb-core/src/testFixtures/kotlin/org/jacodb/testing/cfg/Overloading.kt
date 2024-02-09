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

package org.jacodb.testing.cfg

import kotlin.reflect.KClass
import kotlin.reflect.KFunction0

class Overloading {

    inline fun <reified T> test(kFunction: KFunction0<Unit>, test: T.() -> Unit) {
        val annotation = kFunction.annotations.single() as T
        annotation.test()
    }

    fun check(b: Boolean, message: String) {
        if (!b) throw RuntimeException(message)
    }

    annotation class Foo(val a: FloatArray = [], val b: Array<String> = [], val c: Array<KClass<*>> = [])

    @Foo(a = [1f, 2f, 1 / 0f])
    fun test1() {
    }

    @Foo(b = ["Hello", ", ", "Kot" + "lin"])
    fun test2() {
    }

    @Foo(c = [Int::class, Array<Short>::class, Foo::class])
    fun test3() {
    }

    fun box(): String {
        test<Foo>(::test1) {
            check(a.contentEquals(floatArrayOf(1f, 2f, Float.POSITIVE_INFINITY)), "Fail 1: ${a.joinToString()}")
        }

        test<Foo>(::test2) {
            check(b.contentEquals(arrayOf("Hello", ", ", "Kotlin")), "Fail 2: ${b.joinToString()}")
        }

        test<Foo>(::test3) {
            check(c.contentEquals(arrayOf(Int::class, Array<Short>::class, Foo::class)), "Fail 3: ${c.joinToString()}")
        }

        return "OK"
    }

}
