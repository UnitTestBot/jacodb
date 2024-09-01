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

package org.jacodb.go.api

import org.jacodb.api.common.cfg.CommonInstLocation

interface GoInstLocation : CommonInstLocation {
    override val method: GoMethod
    val lineNumber: Int
}

class GoInstLocationImpl(
    override val method: GoMethod,
    override val lineNumber: Int,
) : GoInstLocation {
    override fun toString(): String {
        return "${method.name}:$lineNumber"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GoInstLocationImpl

        if (lineNumber != other.lineNumber) return false
        return method == other.method
    }

    override fun hashCode(): Int {
        var result = lineNumber
        result = 31 * result + method.hashCode()
        return result
    }
}