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

package org.jacodb.panda.dynamic.ark

interface Ref : Value {
    fun <R> accept(visitor: RefVisitor<R>): R {
        return accept(visitor as ValueVisitor<R>)
    }
}

data class This(
    override val type: Type, // TODO: consider ClassType
) : Ref {
    override fun toString(): String = "this"

    override fun <R> accept(visitor: ValueVisitor<R>): R {
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

    override fun <R> accept(visitor: ValueVisitor<R>): R {
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

    override fun <R> accept(visitor: ValueVisitor<R>): R {
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

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }
}

data class StaticFieldRef(
    override val field: FieldSignature,
) : FieldRef {
    override fun toString(): String {
        return "${field.enclosingClass.name}.${field.sub.name}"
    }

    override fun <R> accept(visitor: ValueVisitor<R>): R {
        return visitor.visit(this)
    }
}
