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

class Ranges {

    fun box(): String {
        val list1 = ArrayList<Int>()
        for (i in (3..9 step 2).reversed()) {
            list1.add(i)
            if (list1.size > 23) break
        }
        if (list1 != listOf<Int>(9, 7, 5, 3)) {
            return "Wrong elements for (3..9 step 2).reversed(): $list1"
        }

        val list2 = ArrayList<Int>()
        for (i in (3.toByte()..9.toByte() step 2).reversed()) {
            list2.add(i)
            if (list2.size > 23) break
        }
        if (list2 != listOf<Int>(9, 7, 5, 3)) {
            return "Wrong elements for (3.toByte()..9.toByte() step 2).reversed(): $list2"
        }

        val list3 = ArrayList<Int>()
        for (i in (3.toShort()..9.toShort() step 2).reversed()) {
            list3.add(i)
            if (list3.size > 23) break
        }
        if (list3 != listOf<Int>(9, 7, 5, 3)) {
            return "Wrong elements for (3.toShort()..9.toShort() step 2).reversed(): $list3"
        }

        val list4 = ArrayList<Long>()
        for (i in (3L..9L step 2L).reversed()) {
            list4.add(i)
            if (list4.size > 23) break
        }
        if (list4 != listOf<Long>(9, 7, 5, 3)) {
            return "Wrong elements for (3L..9L step 2L).reversed(): $list4"
        }

        val list5 = ArrayList<Char>()
        for (i in ('c'..'g' step 2).reversed()) {
            list5.add(i)
            if (list5.size > 23) break
        }
        if (list5 != listOf<Char>('g', 'e', 'c')) {
            return "Wrong elements for ('c'..'g' step 2).reversed(): $list5"
        }

        return "OK"
    }


}

class Ranges2 {

    fun isDigit(a: Int) : String {
        val aa = ArrayList<Int> ()
        aa.add(239)

        return when(a) {
            in aa -> "array list"
            in 0..9 -> "digit"
            !in 0..100 -> "not small"
            else -> "something"
        }
    }

    fun assertDigit(i: Int, expected: String): String {
        val result = isDigit(i)
        return if (result == expected) "" else "fail: isDigit($i) = \"$result\""
    }

    fun box(): String {
        val result = assertDigit(0, "digit")
        if (result == "") return "OK"
        return result
    }
}