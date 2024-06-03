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

package org.jacodb.panda.dynamic.ark.base

interface Position {
    val firstLine: Int
    val lastLine: Int
    val firstColumn: Int
    val lastColumn: Int

    companion object {
        /**
         * A position that represents an unknown location.
         */
        val NO_POSITION = object : Position {
            override val firstLine = -1
            override val lastLine = -1
            override val firstColumn = -1
            override val lastColumn = -1

            override fun toString(): String = "NoPosition"
        }
    }

    data class LinePosition(
        val lineNumber: Int,
    ) : Position {
        override val firstLine: Int
            get() = lineNumber
        override val lastLine: Int
            get() = lineNumber
        override val firstColumn: Int
            get() = 0
        override val lastColumn: Int
            get() = -1

        override fun toString(): String {
            return "[$lineNumber]"
        }
    }

    data class FullPosition(
        override val firstLine: Int,
        override val lastLine: Int,
        override val firstColumn: Int,
        override val lastColumn: Int,
    ) : Position {
        init {
            require(firstLine >= 0)
            require(lastLine >= firstLine)
        }

        override fun toString(): String = buildString {
            append('[')
            append(firstLine)
            if (firstColumn >= 0) {
                append(':')
                append(firstColumn)
            }
            append('-')
            append(lastLine)
            if (lastColumn >= 0) {
                append(':')
                append(lastColumn)
            }
            append(']')
        }
    }
}
