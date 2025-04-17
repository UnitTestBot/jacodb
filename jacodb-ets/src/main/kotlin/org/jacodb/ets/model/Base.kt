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

interface Base : WithModifiers {
    val modifiers: EtsModifiers
    val decorators: List<EtsDecorator>

    // In TS, if "public" modifier is not specified,
    // an entity considered public if it is not private and not protected.
    override val isPublic: Boolean
        get() = super.isPublic || (!isPrivate && !isProtected)

    override fun hasModifier(modifier: EtsModifier): Boolean = modifiers.hasModifier(modifier)

    fun hasDecorator(decorator: EtsDecorator): Boolean = decorators.contains(decorator)
}
