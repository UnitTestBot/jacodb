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

import org.jacodb.api.common.CommonRefType
import org.jacodb.api.common.CommonType
import org.jacodb.api.common.CommonTypeName

sealed interface PandaType : CommonType {
    override val nullable: Boolean
        get() = TODO("Not yet implemented")
}

object PandaAnyType : PandaType {
    override val typeName: String = "any"
}

object PandaBoolType : PandaType {
    override val typeName: String = "bool"
}

object PandaRefType : PandaType, CommonRefType {
    override val typeName: String = "ref"
}

object PandaVoidType : PandaType {
    override val typeName: String = "void"
}

object PandaNumberType : PandaType {
    override val typeName: String = "number"
}

object PandaUndefinedType : PandaType {
    override val typeName: String = "undefined_t"
}

// ------------------------------------------------------

data class PandaTypeName(
    override val typeName: String,
) : CommonTypeName
