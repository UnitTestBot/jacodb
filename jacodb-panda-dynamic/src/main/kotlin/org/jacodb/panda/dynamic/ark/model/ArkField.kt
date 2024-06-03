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

package org.jacodb.panda.dynamic.ark.model

import org.jacodb.panda.dynamic.ark.base.Type

// for '!' field marker ("definitely assigned field"), see https://www.typescriptlang.org/docs/handbook/2/classes.html#--strictpropertyinitialization

data class ArkField(
    val signature: FieldSignature,
    val accessFlags: AccessFlags = AccessFlags(),
    val decorators: List<Decorator> = emptyList(),
    val isOptional: Boolean = false,  // '?'
    val isDefinitelyAssigned: Boolean = false, // '!'
) {
    val name: String
        get() = signature.sub.name
    val type: Type
        get() = signature.sub.type
    val enclosingClass: ClassSignature
        get() = signature.enclosingClass

    override fun toString(): String {
        return signature.toString()
    }
}

data class AccessFlags(
    var isStatic: Boolean = false,
    var isPublic: Boolean = false,
    var isPrivate: Boolean = false,
    var isProtected: Boolean = false,
    var isReadOnly: Boolean = false,
)

data class Decorator (
    val name: String,
    // TODO: args...
)
