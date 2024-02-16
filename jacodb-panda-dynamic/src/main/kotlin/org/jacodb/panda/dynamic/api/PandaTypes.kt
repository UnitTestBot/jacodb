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

import org.jacodb.api.core.CoreType

sealed interface PandaType : CoreType

class PandaAnyType : PandaType {

    override val typeName: String = "any"
}

class PandaBoolType : PandaType {

    override val typeName: String = "bool"
}

class PandaRefType : PandaType {

    override val typeName: String = "ref"
}

class PandaVoidType : PandaType {

    override val typeName: String = "void"
}

class PandaNumberType : PandaType {

    override val typeName: String = "number"
}

class PandaUndefinedType : PandaType {
    override val typeName: String = "undefined_t"
}
