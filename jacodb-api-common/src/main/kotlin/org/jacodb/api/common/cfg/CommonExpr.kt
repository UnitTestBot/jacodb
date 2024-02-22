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

package org.jacodb.api.common.cfg

import org.jacodb.api.common.CommonType
import org.jacodb.api.common.CommonTypedMethod

interface CommonExpr {
    val type: CommonType
    val operands: List<CommonValue>

    interface Visitor<out T> : CommonValue.Visitor<T> {
        fun visitExternalCommonExpr(expr: CommonExpr): T

        fun visitCommonCallExpr(expr: CommonExpr): T
        fun visitCommonInstanceCallExpr(expr: CommonExpr): T

        interface Default<out T> : Visitor<T>, CommonValue.Visitor.Default<T> {
            fun defaultVisitCommonExpr(expr: CommonExpr): T

            override fun defaultVisitCommonValue(value: CommonValue): T = defaultVisitCommonExpr(value)

            override fun visitExternalCommonExpr(expr: CommonExpr): T = defaultVisitCommonExpr(expr)

            override fun visitCommonCallExpr(expr: CommonExpr): T = defaultVisitCommonExpr(expr)
            override fun visitCommonInstanceCallExpr(expr: CommonExpr): T = defaultVisitCommonExpr(expr)
        }
    }

    fun <T> accept(visitor: Visitor<T>): T = acceptCommonExpr(visitor)
    fun <T> acceptCommonExpr(visitor: Visitor<T>): T {
        return visitor.visitExternalCommonExpr(this)
    }
}

interface CommonCallExpr : CommonExpr {
    val method: CommonTypedMethod<*, *>
    val args: List<CommonValue>

    override val type: CommonType
        get() = method.returnType

    override val operands: List<CommonValue>
        get() = args

    override fun <T> acceptCommonExpr(visitor: CommonExpr.Visitor<T>): T {
        return visitor.visitCommonCallExpr(this)
    }
}

interface CommonInstanceCallExpr : CommonCallExpr {
    val instance: CommonValue

    override fun <T> acceptCommonExpr(visitor: CommonExpr.Visitor<T>): T {
        return visitor.visitCommonInstanceCallExpr(this)
    }
}
