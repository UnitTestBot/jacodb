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

package org.utbot.jacodb.impl.storage

enum class AnnotationValueKind {
    BOOLEAN,
    BYTE,
    CHAR,
    SHORT,
    INT,
    FLOAT,
    LONG,
    DOUBLE,
    STRING;

    companion object {
        fun serialize(value: Any): String {
            return when (value) {
                is String -> value
                is Short -> value.toString()
                is Char -> value.toString()
                is Long -> value.toString()
                is Int -> value.toString()
                is Float -> value.toString()
                is Double -> value.toString()
                is Byte -> value.toString()
                is Boolean -> value.toString()
                else -> throw IllegalStateException("Unknown type ${value.javaClass}")
            }
        }
    }

}


enum class LocationState {
    INITIAL,
    AWAITING_INDEXING,
    PROCESSED,
    OUTDATED
}