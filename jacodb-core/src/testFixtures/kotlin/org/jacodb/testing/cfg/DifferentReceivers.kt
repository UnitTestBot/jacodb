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

import org.junit.jupiter.api.Assertions.assertEquals

class DifferentReceivers {

    var log: String = ""

    class MyClass(val value: String)

    inline fun <T> runLogged(entry: String, action: () -> T): T {
        log += entry
        return action()
    }

    operator fun MyClass.provideDelegate(host: Any?, p: Any): String =
        runLogged("tdf(${this.value});") { this.value }

    operator fun String.getValue(receiver: Any?, p: Any): String =
        runLogged("get($this);") { this }

    val testO by runLogged("O;") { MyClass("O") }
    val testK by runLogged("K;") { "K" }
    val testOK = runLogged("OK;") { testO + testK }

    fun box(): String {
        assertEquals("O;tdf(O);K;OK;get(O);get(K);", log)
        return testOK
    }

}