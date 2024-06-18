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

package org.jacodb.panda.dynamic.ark.base

object ArkEntityPrinter : ArkEntity.Visitor.Default<Unit> {
    override fun defaultVisit(value: ArkEntity) {
        println("defaultVisit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun defaultVisit(value: ArkImmediate) {
        println("defaultVisit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun defaultVisit(expr: ArkExpr) {
        println("defaultVisit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun defaultVisit(ref: ArkRef) {
        println("defaultVisit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun defaultVisit(value: ArkConstant) {
        println("defaultVisit<${value::class.java.simpleName}>(value = ${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: ArkLocal) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: StringConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: BooleanConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: NumberConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: NullConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: UndefinedConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: ArrayLiteral) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: ObjectLiteral) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(expr: NewExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: NewArrayExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: TypeOfExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: InstanceOfExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: LengthExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: CastExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: PhiExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: ArkUnaryOperation) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: ArkBinaryOperation) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: ArkRelationOperation) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: ArkInstanceCallExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(expr: ArkStaticCallExpr) {
        println("visit<${expr::class.java.simpleName}>(expr = $expr)")
    }

    override fun visit(ref: ArkThis) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun visit(ref: ArkParameterRef) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun visit(ref: ArkArrayAccess) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun visit(ref: ArkInstanceFieldRef) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }

    override fun visit(ref: ArkStaticFieldRef) {
        println("visit<${ref::class.java.simpleName}>(ref = $ref)")
    }
}

object ImmediatePrinter : ArkImmediate.Visitor.Default<Unit> {
    override fun defaultVisit(value: ArkImmediate) {
        println("defaultVisit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: ArkLocal) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: StringConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: BooleanConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: NumberConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: NullConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }

    override fun visit(value: UndefinedConstant) {
        println("visit<${value::class.java.simpleName}>(value = $value)")
    }
}

fun main() {
    val local: ArkImmediate = ArkLocal("kek", NumberType)
    val stringConstant: ArkConstant = StringConstant("lol")
    val arrayLiteral = ArrayLiteral(listOf(local, stringConstant), ArrayType(NumberType, 1))
    val expr: ArkExpr = ArkBinaryOperation(BinaryOp.Add, local, stringConstant)
    val ref: ArkRef = ArkArrayAccess(arrayLiteral, local, NumberType)

    local.accept(ArkEntityPrinter)
    local.accept(ArkEntityPrinter as ArkImmediate.Visitor<Unit>)
    local.accept(ImmediatePrinter)
    println("-".repeat(40))
    stringConstant.accept(ArkEntityPrinter)
    stringConstant.accept(ArkEntityPrinter as ArkImmediate.Visitor<Unit>)
    stringConstant.accept(ArkEntityPrinter as ArkConstant.Visitor<Unit>)
    stringConstant.accept(ImmediatePrinter)
    stringConstant.accept(ImmediatePrinter as ArkConstant.Visitor<Unit>)
    println("-".repeat(40))
    arrayLiteral.accept(ArkEntityPrinter)
    arrayLiteral.accept(ArkEntityPrinter as ArkImmediate.Visitor<Unit>)
    arrayLiteral.accept(ArkEntityPrinter as ArkConstant.Visitor<Unit>)
    arrayLiteral.accept(ImmediatePrinter)
    arrayLiteral.accept(ImmediatePrinter as ArkConstant.Visitor<Unit>)
    println("-".repeat(40))
    expr.accept(ArkEntityPrinter)
    expr.accept(ArkEntityPrinter as ArkExpr.Visitor<Unit>)
    println("-".repeat(40))
    ref.accept(ArkEntityPrinter)
    ref.accept(ArkEntityPrinter as ArkRef.Visitor<Unit>)
}
