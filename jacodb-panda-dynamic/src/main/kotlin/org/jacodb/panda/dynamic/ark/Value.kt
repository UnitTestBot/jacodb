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

interface Value {
    val type: Type

    interface Visitor<out R> :
        Immediate.Visitor<R>,
        Expr.Visitor<R>,
        Ref.Visitor<R> {

        interface Default<out R> : Visitor<R>,
            Immediate.Visitor.Default<R>,
            Expr.Visitor.Default<R>,
            Ref.Visitor.Default<R> {

            fun defaultVisit(value: Value): R

            override fun defaultVisit(value: Immediate): R = defaultVisit(value as Value)
            override fun defaultVisit(expr: Expr): R = defaultVisit(expr as Value)
            override fun defaultVisit(ref: Ref): R = defaultVisit(ref as Value)
        }
    }

    fun <R> accept(visitor: ValueVisitor<R>): R

    fun <R> accept3(visitor: Visitor<R>): R
}

// TODO: use LValue to mark assignable values
interface LValue : Value
