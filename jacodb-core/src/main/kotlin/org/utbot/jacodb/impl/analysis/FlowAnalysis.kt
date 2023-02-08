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

package org.utbot.jacodb.impl.analysis

import org.utbot.jacodb.api.cfg.*
import org.utbot.jacodb.impl.cfg.collect

interface FlowAnalysis<T> {

    val ins: MutableMap<JcInst, T>
    val outs: MutableMap<JcInst, T>

    val graph: JcGraph

    val isForward: Boolean

    fun newFlow(): T

    fun newEntryFlow(): T

    fun merge(in1: T, in2: T, out: T)

    fun copy(source: T?, dest: T)

    fun run()
}

object LocalResolver : DefaultJcInstVisitor<Sequence<JcLocal>>, DefaultJcExprVisitor<Sequence<JcLocal>> {

    override val defaultInstHandler: (JcInst) -> Sequence<JcLocal>
        get() = { emptySequence() }
    override val defaultExprHandler: (JcExpr) -> Sequence<JcLocal>
        get() = { emptySequence() }

    override fun visitJcLocalVar(value: JcLocalVar): Sequence<JcLocal> {
        return sequenceOf(value)
    }

    override fun visitJcArgument(value: JcArgument): Sequence<JcLocal> {
        return sequenceOf(value)
    }

    private fun visitCallExpr(expr: JcCallExpr): Sequence<JcLocal> {
        return expr.operands.asSequence().flatMap { it.accept(this@LocalResolver) }
    }

    override fun visitJcDynamicCallExpr(expr: JcDynamicCallExpr): Sequence<JcLocal> = visitCallExpr(expr)

    override fun visitJcSpecialCallExpr(expr: JcSpecialCallExpr): Sequence<JcLocal> = visitCallExpr(expr)

    override fun visitJcVirtualCallExpr(expr: JcVirtualCallExpr): Sequence<JcLocal> = visitCallExpr(expr)

    override fun visitJcStaticCallExpr(expr: JcStaticCallExpr): Sequence<JcLocal> = visitCallExpr(expr)

    override fun visitJcAssignInst(inst: JcAssignInst): Sequence<JcLocal> {
        return sequence {
            yieldAll(inst.lhv.accept(this@LocalResolver))
            yieldAll(inst.rhv.accept(this@LocalResolver))
        }
    }

    override fun visitJcThrowInst(inst: JcThrowInst): Sequence<JcLocal> {
        return inst.throwable.accept(this)
    }

    override fun visitJcCatchInst(inst: JcCatchInst): Sequence<JcLocal> {
        return inst.throwable.accept(this)
    }

    override fun visitJcCallInst(inst: JcCallInst): Sequence<JcLocal> {
        return inst.callExpr.accept(this)
    }

    override fun visitJcReturnInst(inst: JcReturnInst): Sequence<JcLocal> {
        return inst.returnValue?.accept(this) ?: emptySequence()
    }
}

val JcGraph.locals: Set<JcLocal>
    get() {
        return collect(LocalResolver).flatMap { it.toList() }.toSet()
    }

//val JcInst.locals: List<JcLocal>
//    get() {
//        return collect(LocalResolver)
//    }