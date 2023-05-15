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

abstract class TypedExprResolver<T : JcExpr> : DefaultJcInstVisitor<Set<T>>, DefaultJcExprVisitor<Set<T>> {

    val result = hashSetOf<T>()

    override val defaultInstHandler: (JcInst) -> Set<T>
        get() = {
            it.operands.forEach {
                addIfMatched(it)
                it.operands.forEach {
                    addIfMatched(it)
                }
            }
            emptySet()
        }

    override val defaultExprHandler: (JcExpr) -> Set<T>
        get() = {
            it.operands.forEach {
                addIfMatched(it)
            }
            addIfMatched(it)
            emptySet()
        }

    protected abstract fun matches(expr: JcExpr): T?

    private fun addIfMatched(expr: JcExpr) = matches(expr)?.let { result.add(it) }

}


class LocalResolver : TypedExprResolver<JcLocal>() {

    override fun matches(expr: JcExpr): JcLocal? {
        return expr as? JcLocal
    }

}

class ValueResolver : TypedExprResolver<JcValue>() {

    override fun matches(expr: JcExpr): JcValue? {
        return expr as? JcValue
    }
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
        val resolver = LocalResolver().also {
            collect(it)
        }
        return resolver.result
    }

val JcInst.locals: Set<JcLocal>
    get() {
        val resolver = LocalResolver().also {
            accept(it)
        }
        return resolver.result
    }


val JcInst.values: Set<JcValue>
    get() {
        val resolver = ValueResolver().also {
            accept(it)
        }
        return resolver.result
    }

val JcGraph.values: Set<JcValue>
    get() {
        val resolver = ValueResolver().also {
            collect(it)
        }
        return resolver.result
    }