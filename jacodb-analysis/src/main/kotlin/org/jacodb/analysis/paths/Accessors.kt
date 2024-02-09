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

package org.jacodb.analysis.paths

import org.jacodb.api.JcField
import org.jacodb.api.cfg.JcValue

sealed interface Accessor {
    fun toSuffix(): String
}

data class FieldAccessor(
    val field: JcField,
) : Accessor {
    override fun toSuffix(): String {
        return ".${field.name}"
    }

    override fun toString(): String {
        return field.name
    }
}

// data class ElementAccessor(
//     val index: JcValue?, // null if "any"
// ) : Accessor {
//     override fun toSuffix(): String {
//         return if (index == null) "[*]" else "[$index]"
//     }
//
//     override fun toString(): String {
//         return "[$index]"
//     }
// }

object ElementAccessor : Accessor {
    override fun toSuffix(): String {
        return "[*]"
    }

    override fun toString(): String {
        return "*"
    }
}

fun ElementAccessor(index: JcValue?) = ElementAccessor
