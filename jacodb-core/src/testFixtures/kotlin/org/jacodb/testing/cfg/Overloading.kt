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

class Overloading {

    class ArrayWrapper<T>() {
        val contents = ArrayList<T>()

        fun add(item: T) {
            contents.add(item)
        }

        operator fun plus(rhs: ArrayWrapper<T>): ArrayWrapper<T> {
            val result = ArrayWrapper<T>()
            result.contents.addAll(contents)
            result.contents.addAll(rhs.contents)
            return result
        }

        operator fun get(index: Int): T {
            return contents.get(index)!!
        }
    }

    fun box(): String {
        var v1 = ArrayWrapper<String>()
        val v2 = ArrayWrapper<String>()
        v1.add("foo")
        val v3 = v1
        v2.add("bar")
        v1 += v2
        return if (v1.contents.size == 2 && v3.contents.size == 1) "OK" else "fail"
    }

}