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

package org.jacodb.impl.util

import java.util.*

/**
 * Concatenates several lists, each one is nullable.
 * If the result is empty always returns EmptyList singleton.
 * Can be used for a single list to return singleton if it is empty.
 */
fun <T> concatLists(vararg lists: List<T>?): List<T> {
    var resultSize = 0
    lists.forEach {
        resultSize += it?.size ?: 0
    }
    if (resultSize == 0) return emptyList()
    if (lists.size == 1) return lists[0].orEmpty()
    return ArrayList<T>(resultSize).apply {
        lists.forEach {
            it?.let { list ->
                addAll(list)
            }
        }
    }
}


fun <T> List<T>.adjustEmptyList(): List<T> = ifEmpty { Collections.emptyList() }