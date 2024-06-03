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

import org.jacodb.panda.dynamic.ark.model.FieldSignature

interface Ref : Value {
    interface Visitor<out R> {
        fun visit(ref: This): R
        fun visit(ref: ParameterRef): R
        fun visit(ref: ArrayAccess): R
        fun visit(ref: InstanceFieldRef): R
        fun visit(ref: StaticFieldRef): R

        interface Default<out R> : Visitor<R> {
            override fun visit(ref: This): R = defaultVisit(ref)
            override fun visit(ref: ParameterRef): R = defaultVisit(ref)
            override fun visit(ref: ArrayAccess): R = defaultVisit(ref)
            override fun visit(ref: InstanceFieldRef): R = defaultVisit(ref)
            override fun visit(ref: StaticFieldRef): R = defaultVisit(ref)

            fun defaultVisit(ref: Ref): R
        }
    }

    override fun <R> accept(visitor: Value.Visitor<R>): R {
        return accept(visitor as Visitor<R>)
    }

    fun <R> accept(visitor: Visitor<R>): R
}

data class This(
    override val type: Type, // TODO: consider ClassType
) : Ref {
    override fun toString(): String = "this"

    override fun <R> accept(visitor: Ref.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ParameterRef(
    val index: Int,
    override val type: Type,
) : Ref {
    override fun toString(): String {
        return "arg$index"
    }

    override fun <R> accept(visitor: Ref.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class ArrayAccess(
    val array: Value,
    val index: Value,
    override val type: Type,
) : Ref {
    override fun toString(): String {
        return "$array[$index]"
    }

    override fun <R> accept(visitor: Ref.Visitor<R>): R {
        return visitor.visit(this)
    }
}

interface FieldRef : Ref {
    val field: FieldSignature

    override val type: Type
        get() = this.field.sub.type
}

data class InstanceFieldRef(
    val instance: Local, // TODO: consider Value
    override val field: FieldSignature,
) : FieldRef {
    override fun toString(): String {
        return "$instance.${field.sub.name}"
    }

    override fun <R> accept(visitor: Ref.Visitor<R>): R {
        return visitor.visit(this)
    }
}

data class StaticFieldRef(
    override val field: FieldSignature,
) : FieldRef {
    override fun toString(): String {
        return "${field.enclosingClass.name}.${field.sub.name}"
    }

    override fun <R> accept(visitor: Ref.Visitor<R>): R {
        return visitor.visit(this)
    }
}
