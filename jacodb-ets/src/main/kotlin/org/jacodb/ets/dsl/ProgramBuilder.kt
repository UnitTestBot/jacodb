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

@file:Suppress("UnusedReceiverParameter")

package org.jacodb.ets.dsl

import org.jacodb.ets.model.EtsEntity

interface ProgramBuilder {
    fun assign(target: Local, expr: Expr)
    fun ret(expr: Expr)
    fun ifStmt(condition: Expr, block: IfBuilder.() -> Unit): IfBuilder
    fun nop()
    fun label(name: String)
    fun goto(label: String)
}

fun ProgramBuilder.local(name: String) = Local(name)
fun ProgramBuilder.param(index: Int) = Parameter(index)
fun ProgramBuilder.thisRef() = ThisRef
fun ProgramBuilder.const(value: Int) = ConstantNumber(value.toDouble())
fun ProgramBuilder.const(value: Double) = ConstantNumber(value)
fun ProgramBuilder.const(value: Boolean) = ConstantBoolean(value)
fun ProgramBuilder.const(value: String) = ConstantString(value)

fun ProgramBuilder.and(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.AND, left, right)
fun ProgramBuilder.or(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.OR, left, right)
fun ProgramBuilder.eq(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.EQ, left, right)
fun ProgramBuilder.neq(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.NEQ, left, right)
fun ProgramBuilder.eqq(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.EQQ, left, right)
fun ProgramBuilder.neqq(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.NEQQ, left, right)
fun ProgramBuilder.lt(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.LT, left, right)
fun ProgramBuilder.leq(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.LTE, left, right)
fun ProgramBuilder.gt(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.GT, left, right)
fun ProgramBuilder.geq(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.GTE, left, right)
fun ProgramBuilder.add(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.ADD, left, right)
fun ProgramBuilder.sub(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.SUB, left, right)
fun ProgramBuilder.mul(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.MUL, left, right)
fun ProgramBuilder.div(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.DIV, left, right)
fun ProgramBuilder.rem(left: Expr, right: Expr) = BinaryExpr(BinaryOperator.REM, left, right)

fun ProgramBuilder.not(expr: Expr) = UnaryExpr(UnaryOperator.NOT, expr)
fun ProgramBuilder.neg(expr: Expr) = UnaryExpr(UnaryOperator.NEG, expr)

class CustomValue(
    val toEts: () -> EtsEntity,
) : Expr {
    constructor(value: EtsEntity) : this({ value })
}

class CustomUnaryExpr(
    val arg: Expr,
    val toEts: (arg: EtsEntity) -> EtsEntity,
) : Expr

class CustomBinaryExpr(
    val left: Expr,
    val right: Expr,
    val toEts: (left: EtsEntity, right: EtsEntity) -> EtsEntity,
) : Expr

class ProgramBuilderImpl : ProgramBuilder {
    private val _nodes: MutableList<Node> = mutableListOf()
    val nodes: List<Node> get() = _nodes

    fun build(): Program {
        return Program(nodes)
    }

    override fun nop() {
        _nodes += Nop
    }

    override fun label(name: String) {
        _nodes += Label(name)
    }

    override fun goto(label: String) {
        _nodes += Goto(label)
    }

    override fun ret(expr: Expr) {
        _nodes += Return(expr)
    }

    override fun assign(target: Local, expr: Expr) {
        _nodes += Assign(target, expr)
    }

    override fun ifStmt(condition: Expr, block: IfBuilder.() -> Unit) : IfBuilder {
        val builder = IfBuilder().apply(block)
        _nodes += If(condition, builder.thenNodes, builder.elseNodes)
        return builder
    }
}

class IfBuilder : ProgramBuilder {
    private val thenBuilder = ProgramBuilderImpl()
    private val elseBuilder = ProgramBuilderImpl()
    private var elseEntered = false

    val thenNodes: List<Node> get() = thenBuilder.nodes
    val elseNodes: List<Node> get() = elseBuilder.nodes

    fun `else`(block: ProgramBuilder.() -> Unit) {
        check(!elseEntered) { "Multiple else branches" }
        elseEntered = true
        elseBuilder.apply(block)
    }

    fun elseIf(condition: Expr, block: ProgramBuilder.() -> Unit): IfBuilder {
        elseBuilder.ifStmt(condition, block)
        return this
    }

    override fun assign(target: Local, expr: Expr) = thenBuilder.assign(target, expr)

    override fun ifStmt(condition: Expr, block: IfBuilder.() -> Unit): IfBuilder {
        return thenBuilder.ifStmt(condition, block)
    }

    override fun ret(expr: Expr) = thenBuilder.ret(expr)
    override fun nop() = thenBuilder.nop()
    override fun goto(label: String) = thenBuilder.goto(label)
    override fun label(name: String) = thenBuilder.label(name)
}
