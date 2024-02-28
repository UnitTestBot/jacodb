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

package org.jacodb.panda.dynamic.api

import org.jacodb.api.common.CommonClass
import org.jacodb.api.common.CommonClassField

class PandaClass(
    override val name: String,
    val superClassName: String,
    val methods: List<PandaMethod>,
) : CommonClass {
    override lateinit var project: PandaProject
        internal set

    override val simpleName: String
        get() = name
}

class PandaField(
    override val name: String,
    override val type: PandaTypeName,
    override val signature: String?,
    override val enclosingClass: PandaClass,
) : CommonClassField
