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

package org.jacodb.panda.dynamic.ets.base

import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.panda.dynamic.ets.model.EtsFieldSignature

interface EtsRef : EtsValue {
    interface Visitor<out R> {
        fun visit(ref: EtsThis): R
        fun visit(ref: EtsParameterRef): R
        fun visit(ref: EtsArrayAccess): R
        fun visit(ref: EtsInstanceFieldRef): R
        fun visit(ref: EtsStaticFieldRef): R

        interface Default<out R> : Visitor<R> {
            override fun visit(ref: EtsThis): R = defaultVisit(ref)
            override fun visit(ref: EtsParameterRef): R = defaultVisit(ref)
            override fun visit(ref: EtsArrayAccess): R = defaultVisit(ref)
            override fun visit(ref: EtsInstanceFieldRef): R = defaultVisit(ref)
            override fun visit(ref: EtsStaticFieldRef): R = defaultVisit(ref)

            fun defaultVisit(ref: EtsRef): R
        }
    }

    override fun <R> accept(visitor: EtsEntity.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class EtsThis(
    override val type: EtsType, // ClassType
) : EtsRef, CommonThis {
    override fun toString(): String = "this"

    override fun <R> accept(visitor: EtsRef.Visitor<R>): R {
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

    override fun <R> accept(visitor: EtsRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsArrayAccess(
    val array: EtsEntity,
    val index: EtsEntity,
    override val type: EtsType,
) : EtsRef, EtsLValue {
    override fun toString(): String {
        return "$array[$index]"
    }

    override fun <R> accept(visitor: EtsRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface EtsFieldRef : EtsRef, EtsLValue {
    val field: EtsFieldSignature

    override val type: EtsType
        get() = this.field.sub.type
}

data class EtsInstanceFieldRef(
    val instance: EtsEntity, // Local
    override val field: EtsFieldSignature,
) : EtsFieldRef {
    override fun toString(): String {
        return "$instance.${field.sub.name}"
    }

    override fun <R> accept(visitor: EtsRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class EtsStaticFieldRef(
    override val field: EtsFieldSignature,
) : EtsFieldRef {
    override fun toString(): String {
        return "${field.enclosingClass.name}.${field.sub.name}"
    }

    override fun <R> accept(visitor: EtsRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}
