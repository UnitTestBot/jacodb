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

package org.utbot.jacodb.impl.cfg.analysis

import org.utbot.jacodb.api.cfg.DefaultJcExprVisitor
import org.utbot.jacodb.api.cfg.DefaultJcInstVisitor
import org.utbot.jacodb.api.cfg.JcAssignInst
import org.utbot.jacodb.api.cfg.JcCallExpr
import org.utbot.jacodb.api.cfg.JcCallInst
import org.utbot.jacodb.api.cfg.JcCatchInst
import org.utbot.jacodb.api.cfg.JcExpr
import org.utbot.jacodb.api.cfg.JcGraph
import org.utbot.jacodb.api.cfg.JcInst
import org.utbot.jacodb.api.cfg.JcLocal
import org.utbot.jacodb.api.cfg.JcThrowInst
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

    override fun visitJcLocal(value: JcLocal): Sequence<JcLocal> {
        return sequenceOf(value)
    }

    override fun visitJcAssignInst(inst: JcAssignInst): Sequence<JcLocal> {
        val value = inst.lhv
        return sequence {
            if (value is JcLocal) {
                yield(value)
            }
            if (inst.rhv is JcCallExpr) {
                yieldAll(inst.lhv.accept(this@LocalResolver))
            }
        }
    }

    override fun visitJcThrowInst(inst: JcThrowInst): Sequence<JcLocal> {
        val throwable = inst.throwable
        if (throwable is JcLocal) {
            return sequenceOf(throwable)
        }
        return emptySequence()
    }

    override fun visitJcCatchInst(inst: JcCatchInst): Sequence<JcLocal> {
        val throwable = inst.throwable
        if (throwable is JcLocal) {
            return sequenceOf(throwable)
        }
        return emptySequence()
    }

    override fun visitJcCallInst(inst: JcCallInst): Sequence<JcLocal> {
        return inst.callExpr.accept(this)
    }

}

val JcGraph.locals: List<JcLocal>
    get() {
        return collect(LocalResolver).flatMap { it.toList() }
    }

//val JcInst.locals: List<JcLocal>
//    get() {
//        return collect(LocalResolver)
//    }