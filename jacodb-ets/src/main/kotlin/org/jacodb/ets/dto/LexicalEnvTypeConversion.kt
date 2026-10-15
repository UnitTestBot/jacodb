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

package org.jacodb.ets.dto

import kotlinx.serialization.SerializationException
import org.jacodb.ets.model.EtsLexicalEnvType
import java.util.Collections
import java.util.IdentityHashMap

// Conversion helpers recursively call each other without carrying a file parameter.
// Keep this context only for the dynamic extent of one file conversion, including
// nested conversions, and restore it even when malformed input fails.
private val currentLexicalEnvTypes = ThreadLocal<LexicalEnvTypeConversion>()

internal fun <T> withLexicalEnvTypes(types: List<LexicalEnvTypeDto>, action: () -> T): T {
    val previous = currentLexicalEnvTypes.get()
    val conversion = LexicalEnvTypeConversion(types = types)
    currentLexicalEnvTypes.set(conversion)

    return try {
        conversion.validate()
        action()
    } finally {
        if (previous == null) {
            currentLexicalEnvTypes.remove()
        } else {
            currentLexicalEnvTypes.set(previous)
        }
    }
}

internal fun resolveLexicalEnvType(id: Int): EtsLexicalEnvType {
    val conversion = currentLexicalEnvTypes.get()
    if (conversion == null) {
        throw SerializationException("LexicalEnvTypeRef $id requires an enclosing file table")
    }
    return conversion.resolve(id = id)
}

internal fun convertLexicalEnvType(type: LexicalEnvTypeDto): EtsLexicalEnvType {
    val conversion = currentLexicalEnvTypes.get()
    if (conversion != null) return conversion.convert(type = type)

    return withLexicalEnvTypes(types = emptyList()) { convertLexicalEnvType(type = type) }
}

private class LexicalEnvTypeConversion(private val types: List<LexicalEnvTypeDto>) {
    private val converted = IdentityHashMap<LexicalEnvTypeDto, EtsLexicalEnvType>()
    private val resolving = Collections.newSetFromMap(IdentityHashMap<LexicalEnvTypeDto, Boolean>())

    fun validate() {
        // Validate even unused definitions: malformed references must never become
        // UnknownType or be hidden just because a table entry is not used by a class.
        types.indices.forEach { id -> resolve(id = id) }
    }

    fun resolve(id: Int): EtsLexicalEnvType {
        if (id !in types.indices) {
            throw SerializationException("LexicalEnvTypeRef $id is outside table of size ${types.size}")
        }
        return convert(type = types[id])
    }

    fun convert(type: LexicalEnvTypeDto): EtsLexicalEnvType {
        val cached = converted[type]
        if (cached != null) return cached

        val hasCycle = !resolving.add(type)
        if (hasCycle) {
            throw SerializationException("Cyclic LexicalEnvTypeRef or inline lexical environment")
        }

        return try {
            val result = EtsLexicalEnvType(
                nestedMethod = type.method.toEtsMethodSignature(),
                closures = type.closures.map { it.toEtsLocal() },
            )
            converted[type] = result
            result
        } finally {
            resolving.remove(type)
        }
    }
}
