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

package org.jacodb.api.jvm.ext.cfg

import org.jacodb.api.core.cfg.InstList
import org.jacodb.api.jvm.cfg.DefaultJcExprVisitor
import org.jacodb.api.jvm.cfg.DefaultJcInstVisitor
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcCallExpr
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcLocal
import org.jacodb.api.jvm.cfg.JcRawExpr
import org.jacodb.api.jvm.cfg.JcRawExprVisitor
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawInstVisitor
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.LocalResolver
import org.jacodb.api.jvm.cfg.ValueResolver

fun InstList<JcRawInst>.apply(visitor: JcRawInstVisitor<Unit>): InstList<JcRawInst> {
    instructions.forEach { it.accept(visitor) }
    return this
}

fun <R, E, T : JcRawInstVisitor<E>> InstList<JcRawInst>.applyAndGet(visitor: T, getter: (T) -> R): R {
    instructions.forEach { it.accept(visitor) }
    return getter(visitor)
}

fun <T> InstList<JcRawInst>.collect(visitor: JcRawInstVisitor<T>): Collection<T> {
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

val InstList<JcInst>.locals: Set<JcLocal>
    get() {
        val resolver = LocalResolver().also { res ->
            forEach { it.accept(res) }
        }
        return resolver.result
    }

val InstList<JcInst>.values: Set<JcValue>
    get() {
        val resolver = ValueResolver().also { res ->
            forEach { it.accept(res) }
        }
        return resolver.result
    }