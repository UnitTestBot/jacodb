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

class DefaultArgs {
    class A(
        val c1: Boolean,
        val c2: Boolean,
        val c3: Boolean,
        val c4: String
    ) {
        override fun equals(o: Any?): Boolean {
            if (o !is A) return false;
            return c1 == o.c1 &&
                    c2 == o.c2 &&
                    c3 == o.c3 &&
                    c4 == o.c4
        }
    }

    fun reformat(
        str : String,
        normalizeCase : Boolean = true,
        uppercaseFirstLetter : Boolean = true,
        divideByCamelHumps : Boolean = true,
        wordSeparator : String = " "
    ) =
        A(normalizeCase, uppercaseFirstLetter, divideByCamelHumps, wordSeparator)


    fun box() : String {
        val expected = A(true, true, true, " ")
        if(reformat("", true, true) != expected) return "fail"
        return "OK"
    }

}