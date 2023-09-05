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

class InvokeMethodWithException {

    class A {
        fun lol(a: Int): Int {
            return 888/a
        }
    }

    fun box():String {
        val method = A::class.java.getMethod("lol", Int::class.java)
        var failed = false
        try {
            method.invoke(null, 0)
        }
        catch(e: Exception) {
            failed = true
        }

        return if (!failed) "fail" else "OK"
    }

}

fun main() {
    println(InvokeMethodWithException().box())
}