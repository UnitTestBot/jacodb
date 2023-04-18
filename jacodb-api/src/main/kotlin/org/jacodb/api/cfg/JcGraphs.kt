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

@file:JvmName("JcGraphs")

package org.jacodb.api.cfg

object LocalResolver : DefaultJcInstVisitor<Set<JcLocal>?> {

    override val defaultInstHandler: (JcInst) -> Set<JcLocal>?
        get() = { it.operands.flatMap { it.operands }.filterIsInstance<JcLocal>().toSet() }

}

fun JcGraph.apply(visitor: JcInstVisitor<Unit>): JcGraph {
    instructions.forEach { it.accept(visitor) }
    return this
}

fun <R, E, T : JcInstVisitor<E>> JcGraph.applyAndGet(visitor: T, getter: (T) -> R): R {
    instructions.forEach { it.accept(visitor) }
    return getter(visitor)
}

fun <T> JcGraph.collect(visitor: JcInstVisitor<T>): Collection<T> {
    return instructions.map { it.accept(visitor) }
}

fun <R, E, T : JcInstVisitor<E>> JcInst.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

fun <R, E, T : JcExprVisitor<E>> JcExpr.applyAndGet(visitor: T, getter: (T) -> R): R {
    this.accept(visitor)
    return getter(visitor)
}

val JcGraph.locals: Set<JcLocal>
    get() {
        return collect(LocalResolver).flatMap { it.orEmpty() }.toSet()
    }

val JcInst.locals: Set<JcLocal>
    get() {
        return accept(LocalResolver)?.toSet().orEmpty()
    }

