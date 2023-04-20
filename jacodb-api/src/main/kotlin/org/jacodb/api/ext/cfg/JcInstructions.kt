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

@file:JvmName("JcInstructions")

package org.jacodb.api.ext.cfg

import org.jacodb.api.cfg.*

fun JcInstList<JcRawInst>.apply(visitor: JcRawInstVisitor<Unit>): JcInstList<JcRawInst> {
    instructions.forEach { it.accept(visitor) }
    return this
}

fun <R, E, T : JcRawInstVisitor<E>> JcInstList<JcRawInst>.applyAndGet(visitor: T, getter: (T) -> R): R {
    instructions.forEach { it.accept(visitor) }
    return getter(visitor)
}

fun <T> JcInstList<JcRawInst>.collect(visitor: JcRawInstVisitor<T>): Collection<T> {
    return instructions.map { it.accept(visitor) }
}


fun <R, E, T : JcRawInstVisitor<E>> JcRawInst.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

fun <R, E, T : JcRawExprVisitor<E>> JcRawExpr.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}


object FieldRefVisitor : DefaultJcExprVisitor<JcFieldRef?>, DefaultJcInstVisitor<JcFieldRef?> {

    override val defaultExprHandler: (JcExpr) -> JcFieldRef?
        get() = { null }

    override val defaultInstHandler: (JcInst) -> JcFieldRef?
        get() = {
            it.operands.map { it.accept(this) }.firstOrNull { it != null }
        }

    override fun visitJcFieldRef(value: JcFieldRef): JcFieldRef {
        return value
    }
}

object ArrayAccessVisitor : DefaultJcExprVisitor<JcArrayAccess?>, DefaultJcInstVisitor<JcArrayAccess?> {

    override val defaultExprHandler: (JcExpr) -> JcArrayAccess?
        get() = {
            it.operands.filterIsInstance<JcArrayAccess>().firstOrNull()
        }

    override val defaultInstHandler: (JcInst) -> JcArrayAccess?
        get() = {
            it.operands.map { it.accept(this) }.firstOrNull { it != null }
        }

}

object CallExprVisitor : DefaultJcInstVisitor<JcCallExpr?> {

    override val defaultInstHandler: (JcInst) -> JcCallExpr?
        get() = {
            it.operands.filterIsInstance<JcCallExpr>().firstOrNull()
        }

}

val JcInst.fieldRef: JcFieldRef?
    get() {
        return accept(FieldRefVisitor)
    }

val JcInst.arrayRef: JcArrayAccess?
    get() {
        return accept(ArrayAccessVisitor)
    }

val JcInst.callExpr: JcCallExpr?
    get() {
        return accept(CallExprVisitor)
    }

val JcInstList<JcInst>.locals: Set<JcLocal>
    get() {
        return instructions.flatMap { it.accept(LocalResolver) }.toSet()
    }