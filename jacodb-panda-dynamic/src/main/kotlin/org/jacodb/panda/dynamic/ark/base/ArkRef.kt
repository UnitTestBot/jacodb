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

package org.jacodb.panda.dynamic.ark.base

import org.jacodb.api.common.cfg.CommonArgument
import org.jacodb.api.common.cfg.CommonThis
import org.jacodb.panda.dynamic.ark.model.FieldSignature

interface ArkRef : ArkValue {
    interface Visitor<out R> {
        fun visit(ref: ArkThis): R
        fun visit(ref: ArkParameterRef): R
        fun visit(ref: ArkArrayAccess): R
        fun visit(ref: ArkInstanceFieldRef): R
        fun visit(ref: ArkStaticFieldRef): R

        interface Default<out R> : Visitor<R> {
            override fun visit(ref: ArkThis): R = defaultVisit(ref)
            override fun visit(ref: ArkParameterRef): R = defaultVisit(ref)
            override fun visit(ref: ArkArrayAccess): R = defaultVisit(ref)
            override fun visit(ref: ArkInstanceFieldRef): R = defaultVisit(ref)
            override fun visit(ref: ArkStaticFieldRef): R = defaultVisit(ref)

            fun defaultVisit(ref: ArkRef): R
        }
    }

    override fun <R> accept(visitor: ArkEntity.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class ArkThis(
    override val type: ArkType, // ClassType
) : ArkRef, CommonThis {
    override fun toString(): String = "this"

    override fun <R> accept(visitor: ArkRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkParameterRef(
    val index: Int,
    override val type: ArkType,
) : ArkRef, CommonArgument {
    override fun toString(): String {
        return "arg$index"
    }

    override fun <R> accept(visitor: ArkRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkArrayAccess(
    val array: ArkEntity,
    val index: ArkEntity,
    override val type: ArkType,
) : ArkRef, ArkLValue {
    override fun toString(): String {
        return "$array[$index]"
    }

    override fun <R> accept(visitor: ArkRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface FieldRef : ArkRef, ArkLValue {
    val field: FieldSignature

    override val type: ArkType
        get() = this.field.sub.type
}

data class ArkInstanceFieldRef(
    val instance: ArkEntity, // Local
    override val field: FieldSignature,
) : FieldRef {
    override fun toString(): String {
        return "$instance.${field.sub.name}"
    }

    override fun <R> accept(visitor: ArkRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArkStaticFieldRef(
    override val field: FieldSignature,
) : FieldRef {
    override fun toString(): String {
        return "${field.enclosingClass.name}.${field.sub.name}"
    }

    override fun <R> accept(visitor: ArkRef.Visitor<R>): R {
        return visitor.visit(this)
    }
}
