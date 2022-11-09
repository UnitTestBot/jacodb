package org.utbot.jcdb.api.cfg.ext

import org.utbot.jcdb.api.JcRawExpr
import org.utbot.jcdb.api.JcRawInst
import org.utbot.jcdb.api.JcRawInstList
import org.utbot.jcdb.api.cfg.JcRawExprVisitor
import org.utbot.jcdb.api.cfg.JcRawInstVisitor

fun JcRawInstList.filter(visitor: JcRawInstVisitor<Boolean>) =
    JcRawInstList(instructions.filter { it.accept(visitor) })

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
