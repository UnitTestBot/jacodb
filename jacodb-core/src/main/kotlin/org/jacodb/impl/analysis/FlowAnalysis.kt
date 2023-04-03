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

package org.jacodb.impl.analysis

import org.jacodb.api.cfg.DefaultJcExprVisitor
import org.jacodb.api.cfg.DefaultJcInstVisitor
import org.jacodb.api.cfg.JcArgument
import org.jacodb.api.cfg.JcExpr
import org.jacodb.api.cfg.JcGraph
import org.jacodb.api.cfg.JcInst
import org.jacodb.api.cfg.JcLocal
import org.jacodb.api.cfg.JcLocalVar
import org.jacodb.api.cfg.JcThis
import org.jacodb.impl.cfg.collect

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
        get() = { inst ->
            inst.operands
                .asSequence()
                .flatMap {
                    it.accept(this@LocalResolver)
                }
        }

    override val defaultExprHandler: (JcExpr) -> Sequence<JcLocal>
        get() = { expr ->
            expr.operands
                .asSequence()
                .flatMap {
                    it.accept(this@LocalResolver)
                }
        }

    override fun visitJcLocalVar(value: JcLocalVar): Sequence<JcLocal> {
        return sequenceOf(value)
    }

    override fun visitJcArgument(value: JcArgument): Sequence<JcLocal> {
        return sequenceOf(value)
    }

    override fun visitJcThis(value: JcThis): Sequence<JcLocal> {
        return sequenceOf(value)
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