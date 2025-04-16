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

enum class EtsModifier(val value: Int, val string: String) {
    PRIVATE(1 shl 0, "private"),
    PROTECTED(1 shl 1, "protected"),
    PUBLIC(1 shl 2, "public"),
    EXPORT(1 shl 3, "export"),
    STATIC(1 shl 4, "static"),
    ABSTRACT(1 shl 5, "abstract"),
    ASYNC(1 shl 6, "async"),
    CONST(1 shl 7, "const"),
    ACCESSOR(1 shl 8, "accessor"),
    DEFAULT(1 shl 9, "default"),
    IN(1 shl 10, "in"),
    READONLY(1 shl 11, "readonly"),
    OUT(1 shl 12, "out"),
    OVERRIDE(1 shl 13, "override"),
    DECLARE(1 shl 14, "declare");
}

interface WithModifiers {
    val isPrivate: Boolean get() = hasModifier(EtsModifier.PRIVATE)
    val isProtected: Boolean get() = hasModifier(EtsModifier.PROTECTED)
    val isPublic: Boolean get() = hasModifier(EtsModifier.PUBLIC)
    val isExport: Boolean get() = hasModifier(EtsModifier.EXPORT)
    val isStatic: Boolean get() = hasModifier(EtsModifier.STATIC)
    val isAbstract: Boolean get() = hasModifier(EtsModifier.ABSTRACT)
    val isAsync: Boolean get() = hasModifier(EtsModifier.ASYNC)
    val isConst: Boolean get() = hasModifier(EtsModifier.CONST)
    val isAccessor: Boolean get() = hasModifier(EtsModifier.ACCESSOR)
    val isDefault: Boolean get() = hasModifier(EtsModifier.DEFAULT)
    val isIn: Boolean get() = hasModifier(EtsModifier.IN)
    val isReadonly: Boolean get() = hasModifier(EtsModifier.READONLY)
    val isOut: Boolean get() = hasModifier(EtsModifier.OUT)
    val isOverride: Boolean get() = hasModifier(EtsModifier.OVERRIDE)
    val isDeclare: Boolean get() = hasModifier(EtsModifier.DECLARE)

    fun hasModifier(modifier: EtsModifier): Boolean
}

@JvmInline
value class EtsModifiers(val mask: Int) : WithModifiers {
    companion object {
        val EMPTY = EtsModifiers(0)
    }

    override fun hasModifier(modifier: EtsModifier): Boolean = (mask and modifier.value) != 0
}
