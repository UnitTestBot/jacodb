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

interface Immediate : Value {
    fun <R> accept(visitor: ImmediateVisitor<R>): R {
        return accept(visitor as ValueVisitor<R>)
    }

    interface Visitor<out R> : Constant.Visitor<R> {
        fun visit(value: Local): R

        interface Default<out R> : Visitor<R>,
            Constant.Visitor.Default<R> {

            fun defaultVisit(value: Immediate): R

            override fun defaultVisit(value: Constant): R = defaultVisit(value as Immediate)
            override fun visit(value: Local): R = defaultVisit(value)
        }
    }

    override fun <R> accept3(visitor: Value.Visitor<R>): R {
        return accept3(visitor as Visitor<R>)
    }

    fun <R> accept3(visitor: Visitor<R>): R
}
