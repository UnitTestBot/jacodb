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

package org.jacodb.api.core.cfg

import org.jacodb.api.core.CoreMethod


interface CoreInst<InstLocation : CoreInstLocation<Method>, Method : CoreMethod<*>, Expr> { // TODO generalize
    val location: InstLocation
    val operands: List<Expr>

    val lineNumber: Int

    fun <T> accept(visitor: InstVisitor<T>): T
}

interface CoreInstLocation<Method : CoreMethod<*>> { // TODO generalize
    val method: Method
    val index: Int
    val lineNumber: Int
}

interface CoreAssignInst<InstLocation, Method, Value, Expr, Type> : CoreInst<InstLocation, Method, Expr>
        where Value : CoreValue<Value, Type>,
              InstLocation : CoreInstLocation<Method>,
              Method : CoreMethod<*> {
    val lhv: Value
    val rhv: Expr
}

interface CoreCallInst<InstLocation, Method, Expr> : CoreInst<InstLocation, Method, Expr>
        where InstLocation : CoreInstLocation<Method>,
              Method : CoreMethod<*>

interface CoreReturnInst<InstLocation, Method, Expr> : CoreInst<InstLocation, Method, Expr>
        where InstLocation : CoreInstLocation<Method>,
              Method : CoreMethod<*>

interface CoreGotoInst<InstLocation, Method, Expr> : CoreInst<InstLocation, Method, Expr>
        where InstLocation : CoreInstLocation<Method>,
              Method : CoreMethod<*>

interface CoreIfInst<InstLocation, Method, Expr> : CoreInst<InstLocation, Method, Expr>
        where InstLocation : CoreInstLocation<Method>,
              Method : CoreMethod<*>
