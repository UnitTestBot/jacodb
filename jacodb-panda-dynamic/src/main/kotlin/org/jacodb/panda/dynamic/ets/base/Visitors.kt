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

package org.jacodb.panda.dynamic.ets.base

object EtsEntityPrinter : EtsEntity.Visitor.Default<Unit> {
    override fun defaultVisit(value: EtsEntity) {
        println("defaultVisit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun defaultVisit(value: EtsImmediate) {
        println("defaultVisit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun defaultVisit(expr: EtsExpr) {
        println("defaultVisit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun defaultVisit(ref: EtsRef) {
        println("defaultVisit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun defaultVisit(value: EtsConstant) {
        println("defaultVisit<${value::class.java.simpleName}>(value = ${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsLocal) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsStringConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsBooleanConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsNumberConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsNullConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsUndefinedConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsArrayLiteral) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsObjectLiteral) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(expr: EtsNewExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsNewArrayExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsTypeOfExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsInstanceOfExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsLengthExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsCastExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsPhiExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsUnaryOperation) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsBinaryOperation) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsRelationOperation) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsInstanceCallExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: EtsStaticCallExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(ref: EtsThis) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun visit(ref: EtsParameterRef) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun visit(ref: EtsArrayAccess) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun visit(ref: EtsInstanceFieldRef) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun visit(ref: EtsStaticFieldRef) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }
}

object ImmediatePrinter : EtsImmediate.Visitor.Default<Unit> {
    override fun defaultVisit(value: EtsImmediate) {
        println("defaultVisit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsLocal) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsStringConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsBooleanConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsNumberConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsNullConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: EtsUndefinedConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }
}

fun main() {
    val local: EtsImmediate = EtsLocal("kek", EtsNumberType)
    val stringConstant: EtsConstant = EtsStringConstant("lol")
    val arrayLiteral = EtsArrayLiteral(listOf(local, stringConstant), EtsArrayType(EtsNumberType, 1))
    val expr: EtsExpr = EtsBinaryOperation(BinaryOp.Add, local, stringConstant)
    val ref: EtsRef = EtsArrayAccess(arrayLiteral, local, EtsNumberType)

    local.accept(EtsEntityPrinter)
    local.accept(EtsEntityPrinter as EtsImmediate.Visitor<Unit>)
    local.accept(ImmediatePrinter)
    println("-".repeat(40))
    stringConstant.accept(EtsEntityPrinter)
    stringConstant.accept(EtsEntityPrinter as EtsImmediate.Visitor<Unit>)
    stringConstant.accept(EtsEntityPrinter as EtsConstant.Visitor<Unit>)
    stringConstant.accept(ImmediatePrinter)
    stringConstant.accept(ImmediatePrinter as EtsConstant.Visitor<Unit>)
    println("-".repeat(40))
    arrayLiteral.accept(EtsEntityPrinter)
    arrayLiteral.accept(EtsEntityPrinter as EtsImmediate.Visitor<Unit>)
    arrayLiteral.accept(EtsEntityPrinter as EtsConstant.Visitor<Unit>)
    arrayLiteral.accept(ImmediatePrinter)
    arrayLiteral.accept(ImmediatePrinter as EtsConstant.Visitor<Unit>)
    println("-".repeat(40))
    expr.accept(EtsEntityPrinter)
    expr.accept(EtsEntityPrinter as EtsExpr.Visitor<Unit>)
    println("-".repeat(40))
    ref.accept(EtsEntityPrinter)
    ref.accept(EtsEntityPrinter as EtsRef.Visitor<Unit>)
}
