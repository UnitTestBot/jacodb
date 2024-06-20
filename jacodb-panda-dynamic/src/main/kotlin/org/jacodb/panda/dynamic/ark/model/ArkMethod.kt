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

import org.jacodb.api.common.CommonMethod
import org.jacodb.panda.dynamic.ark.base.ArkType
import org.jacodb.panda.dynamic.ark.graph.ArkCfg

// TODO: modifiers
// TODO: typeParameters
interface ArkMethod : CommonMethod {
    val enclosingClass: ArkClass
    val signature: ArkMethodSignature
    val cfg: ArkCfg

    override val name: String
        get() = signature.name

    override val parameters: List<ArkMethodParameter>
        get() = signature.parameters

    override val returnType: ArkType
        get() = signature.returnType

    override fun flowGraph(): ArkCfg {
        return cfg
    }
}

class ArkMethodImpl(
    override val signature: ArkMethodSignature,
) : ArkMethod {
    override lateinit var enclosingClass: ArkClass
    override lateinit var cfg: ArkCfg

    override fun toString(): String {
        return signature.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArkMethodImpl

        return signature == other.signature
    }

    override fun hashCode(): Int {
        return signature.hashCode()
    }
}
