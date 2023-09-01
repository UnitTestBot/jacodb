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

class KotlinSequence {

    val xs = listOf("a", "b", "c", "d").asSequence()

    fun box(): String {
        val s = StringBuilder()

        for ((i, _) in xs.withIndex()) {
            s.append("$i;")
        }

        val ss = s.toString()
        return if (ss == "0;1;2;3;") "OK" else "fail: '$ss'"
    }

}