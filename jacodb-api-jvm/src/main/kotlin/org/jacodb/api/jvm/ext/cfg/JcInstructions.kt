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

import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcCallExpr
import org.jacodb.api.jvm.cfg.JcExpr
import org.jacodb.api.jvm.cfg.JcExprVisitor
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcInstVisitor
import org.jacodb.api.jvm.cfg.JcLocal
import org.jacodb.api.jvm.cfg.JcRawExpr
import org.jacodb.api.jvm.cfg.JcRawExprVisitor
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawInstVisitor
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.LocalResolver
import org.jacodb.api.jvm.cfg.ValueResolver

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

object FieldRefVisitor :
    JcExprVisitor.Default<JcFieldRef?>,
    JcInstVisitor.Default<JcFieldRef?> {

    override fun defaultVisitCommonExpr(expr: CommonExpr): JcFieldRef? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitCommonInst(inst: CommonInst): JcFieldRef? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitJcExpr(expr: JcExpr): JcFieldRef? {
        return expr.operands.filterIsInstance<JcFieldRef>().firstOrNull()
    }

    override fun defaultVisitJcInst(inst: JcInst): JcFieldRef? {
        return inst.operands.map { it.accept(this) }.firstOrNull { it != null }
    }

    override fun visitJcFieldRef(value: JcFieldRef): JcFieldRef {
        return value
    }
}

object ArrayAccessVisitor :
    JcExprVisitor.Default<JcArrayAccess?>,
    JcInstVisitor.Default<JcArrayAccess?> {

    override fun defaultVisitCommonExpr(expr: CommonExpr): JcArrayAccess? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitCommonInst(inst: CommonInst): JcArrayAccess? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitJcExpr(expr: JcExpr): JcArrayAccess? {
        return expr.operands.filterIsInstance<JcArrayAccess>().firstOrNull()
    }

    override fun defaultVisitJcInst(inst: JcInst): JcArrayAccess? {
        return inst.operands.map { it.accept(this) }.firstOrNull { it != null }
    }

    override fun visitJcArrayAccess(value: JcArrayAccess): JcArrayAccess {
        return value
    }
}

object CallExprVisitor : JcInstVisitor.Default<JcCallExpr?> {
    override fun defaultVisitCommonInst(inst: CommonInst): JcCallExpr? {
        TODO("Not yet implemented")
    }

    override fun defaultVisitJcInst(inst: JcInst): JcCallExpr? {
        return inst.operands.filterIsInstance<JcCallExpr>().firstOrNull()
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
        val resolver = LocalResolver().also { res ->
            forEach { it.accept(res) }
        }
        return resolver.result
    }

val JcInstList<JcInst>.values: Set<JcValue>
    get() {
        val resolver = ValueResolver().also { res ->
            forEach { it.accept(res) }
        }
        return resolver.result
    }
