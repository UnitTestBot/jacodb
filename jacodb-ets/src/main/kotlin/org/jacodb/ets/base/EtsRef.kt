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

package org.jacodb.ets.base

import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.ets.model.EtsFieldSignature

interface EtsRef : EtsValue

data class EtsThis(
    override val type: EtsClassType,
) : EtsRef, CommonThis {
    override fun toString(): String = "this"

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsParameterRef(
    val index: Int,
    override val type: EtsType,
) : EtsRef, CommonArgument {
    override fun toString(): String {
        return "arg$index"
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsArrayAccess(
    val array: EtsValue,
    val index: EtsValue,
    override val type: EtsType,
) : EtsRef, EtsLValue {
    override fun toString(): String {
        return "$array[$index]"
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsFieldRef : EtsRef, EtsLValue {
    val field: EtsFieldSignature

    override val type: EtsType
        get() = this.field.type
}

data class EtsInstanceFieldRef(
    val instance: EtsLocal,
    override val field: EtsFieldSignature,
) : EtsFieldRef {
    override fun toString(): String {
        return "$instance.${field.name}"
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsStaticFieldRef(
    override val field: EtsFieldSignature,
) : EtsFieldRef {
    override fun toString(): String {
        return "${field.enclosingClass.name}.${field.name}"
    }

    override fun <R> accept(visitor: EtsValue.Visitor<R>): R {
        return visitor.visit(this)
    }
}
