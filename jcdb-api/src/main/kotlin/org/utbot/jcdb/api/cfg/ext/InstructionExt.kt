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

package org.utbot.jcdb.api.cfg.ext

import org.utbot.jcdb.api.cfg.JcRawExpr
import org.utbot.jcdb.api.cfg.JcRawExprVisitor
import org.utbot.jcdb.api.cfg.JcRawInst
import org.utbot.jcdb.api.cfg.JcRawInstList
import org.utbot.jcdb.api.cfg.JcRawInstVisitor

fun JcRawInstList.filter(visitor: JcRawInstVisitor<Boolean>) =
    JcRawInstList(instructions.filter { it.accept(visitor) })

fun JcRawInstList.filterNot(visitor: JcRawInstVisitor<Boolean>) =
    JcRawInstList(instructions.filterNot { it.accept(visitor) })

fun JcRawInstList.map(visitor: JcRawInstVisitor<JcRawInst>) =
    JcRawInstList(instructions.map { it.accept(visitor) })

fun JcRawInstList.mapNotNull(visitor: JcRawInstVisitor<JcRawInst?>) =
    JcRawInstList(instructions.mapNotNull { it.accept(visitor) })

fun JcRawInstList.flatMap(visitor: JcRawInstVisitor<Collection<JcRawInst>>) =
    JcRawInstList(instructions.flatMap { it.accept(visitor) })

fun JcRawInstList.apply(visitor: JcRawInstVisitor<Unit>): JcRawInstList {
    instructions.forEach { it.accept(visitor) }
    return this
}

fun <R, E, T : JcRawInstVisitor<E>> JcRawInstList.applyAndGet(visitor: T, getter: (T) -> R): R {
    instructions.forEach { it.accept(visitor) }
    return getter(visitor)
}

fun <T> JcRawInstList.collect(visitor: JcRawInstVisitor<T>): Collection<T> {
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
