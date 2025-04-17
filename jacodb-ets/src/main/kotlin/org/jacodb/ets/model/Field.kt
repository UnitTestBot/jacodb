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

package org.jacodb.ets.model

interface EtsField : Base {
    val signature: EtsFieldSignature

    val declaringClass: EtsClass?

    val name: String
        get() = signature.name

    val type: EtsType
        get() = signature.type
}

class EtsFieldImpl(
    override val signature: EtsFieldSignature,
    override val modifiers: EtsModifiers = EtsModifiers.Companion.EMPTY,
    val isOptional: Boolean = false,  // '?'
    val isDefinitelyAssigned: Boolean = false, // '!'
) : EtsField {
    override var declaringClass: EtsClass? = null

    override val decorators: List<EtsDecorator>
        get() = error("Fields do not have decorators")

    override fun toString(): String {
        return signature.toString()
    }
}
