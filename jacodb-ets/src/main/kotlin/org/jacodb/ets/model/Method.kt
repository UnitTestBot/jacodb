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

@file:Suppress("PropertyName")

package org.jacodb.ets.model

import org.jacodb.api.common.CommonMethod

interface EtsMethod : Base, CommonMethod {
    val signature: EtsMethodSignature
    val typeParameters: List<EtsType>
    val body: EtsMethodBody

    val enclosingClass: EtsClass?

    override val name: String
        get() = signature.name

    override val parameters: List<EtsMethodParameter>
        get() = signature.parameters

    override val returnType: EtsType
        get() = signature.returnType

    val cfg: EtsBlockCfg
        get() = body.cfg

    val locals: List<EtsLocal>
        get() = body.locals

    val traps: List<EtsTrap>
        get() = body.traps

    override fun flowGraph(): EtsBytecodeGraph<EtsStmt> {
        return cfg
    }
}

class EtsMethodBody(
    var cfg: EtsBlockCfg = EtsBlockCfg.EMPTY,
    var locals: List<EtsLocal> = emptyList(),
    var traps: List<EtsTrap> = emptyList(),
)

class EtsMethodImpl(
    override val signature: EtsMethodSignature,
    override val typeParameters: List<EtsType> = emptyList(),
    override val modifiers: EtsModifiers = EtsModifiers.EMPTY,
    override val decorators: List<EtsDecorator> = emptyList(),
) : EtsMethod {
    override val body: EtsMethodBody = EtsMethodBody()

    @Deprecated("Use body.cfg instead", ReplaceWith("body.cfg"))
    var _cfg: EtsBlockCfg
        get() = body.cfg
        set(value) {
            body.cfg = value
        }

    override var enclosingClass: EtsClass? = null

    override fun toString(): String {
        return signature.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EtsMethodImpl

        return signature == other.signature
    }

    override fun hashCode(): Int {
        return signature.hashCode()
    }
}
