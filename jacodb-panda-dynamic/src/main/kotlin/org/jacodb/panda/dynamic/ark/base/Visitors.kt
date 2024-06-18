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

object ValuePrinter : ArkEntity.Visitor.Default<Unit> {
    override fun defaultVisit(value: ArkEntity) {
        println("defaultVisit<Value>($value)")
    }

    override fun defaultVisit(value: ArkImmediate) {
        println("defaultVisit<Immediate>($value)")
    }

    override fun defaultVisit(expr: ArkExpr) {
        println("defaultVisit<Expr>($expr)")
    }

    override fun defaultVisit(ref: ArkRef) {
        println("defaultVisit<Ref>($ref)")
    }

    override fun defaultVisit(value: ArkConstant) {
        println("defaultVisit<Constant>($value)")
    }

    override fun visit(value: ArkLocal) {
        println("visit<Local>($value)")
    }

    override fun visit(value: StringConstant) {
        println("visit<StringConstant>($value)")
    }

    override fun visit(value: BooleanConstant) {
        println("visit<BooleanConstant>($value)")
    }

    override fun visit(value: NumberConstant) {
        println("visit<NumberConstant>($value)")
    }

    override fun visit(value: NullConstant) {
        println("visit<NullConstant>($value)")
    }

    override fun visit(value: UndefinedConstant) {
        println("visit<UndefinedConstant>($value)")
    }

    override fun visit(value: ArrayLiteral) {
        println("visit<ArrayLiteral>($value)")
    }

    override fun visit(value: ObjectLiteral) {
        println("visit<ObjectLiteral>($value)")
    }

    override fun visit(expr: NewExpr) {
        println("visit<NewExpr>($expr)")
    }

    override fun visit(expr: NewArrayExpr) {
        println("visit<NewArrayExpr>($expr)")
    }

    override fun visit(expr: TypeOfExpr) {
        println("visit<TypeOfExpr>($expr)")
    }

    override fun visit(expr: InstanceOfExpr) {
        println("visit<InstanceOfExpr>($expr)")
    }

    override fun visit(expr: LengthExpr) {
        println("visit<LengthExpr>($expr)")
    }

    override fun visit(expr: CastExpr) {
        println("visit<CastExpr>($expr)")
    }

    override fun visit(expr: PhiExpr) {
        println("visit<PhiExpr>($expr)")
    }

    override fun visit(expr: ArkUnaryOperation) {
        println("visit<UnaryOperation>($expr)")
    }

    override fun visit(expr: ArkBinaryOperation) {
        println("visit<BinaryOperation>($expr)")
    }

    override fun visit(expr: ArkRelationOperation) {
        println("visit<RelationOperation>($expr)")
    }

    override fun visit(expr: ArkInstanceCallExpr) {
        println("visit<InstanceCallExpr>($expr)")
    }

    override fun visit(expr: ArkStaticCallExpr) {
        println("visit<StaticCallExpr>($expr)")
    }

    override fun visit(ref: ArkThis) {
        println("visit<This>($ref)")
    }

    override fun visit(ref: ArkParameterRef) {
        println("visit<ParameterRef>($ref)")
    }

    override fun visit(ref: ArkArrayAccess) {
        println("visit<ArrayAccess>($ref)")
    }

    override fun visit(ref: ArkInstanceFieldRef) {
        println("visit<InstanceFieldRef>($ref)")
    }

    override fun visit(ref: ArkStaticFieldRef) {
        println("visit<StaticFieldRef>($ref)")
    }
}

object ImmediatePrinter : ArkImmediate.Visitor.Default<Unit> {
    override fun defaultVisit(value: ArkImmediate) {
        println("defaultVisit<Immediate>($value)")
    }

    override fun visit(value: ArkLocal) {
        println("visit<Local>($value)")
    }

    override fun visit(value: StringConstant) {
        println("visit<StringConstant>($value)")
    }

    override fun visit(value: BooleanConstant) {
        println("visit<BooleanConstant>($value)")
    }

    override fun visit(value: NumberConstant) {
        println("visit<NumberConstant>($value)")
    }

    override fun visit(value: NullConstant) {
        println("visit<NullConstant>($value)")
    }

    override fun visit(value: UndefinedConstant) {
        println("visit<UndefinedConstant>($value)")
    }
}

fun main() {
    val local: ArkImmediate = ArkLocal("kek", NumberType)
    val stringConstant: ArkConstant = StringConstant("lol")
    val arrayLiteral = ArrayLiteral(listOf(local, stringConstant), ArrayType(NumberType, 1))
    val expr: ArkExpr = ArkBinaryOperation(BinaryOp.Add, local, stringConstant)
    val ref: ArkRef = ArkArrayAccess(arrayLiteral, local, NumberType)

    local.accept(ValuePrinter)
    local.accept(ValuePrinter as ArkImmediate.Visitor<Unit>)
    local.accept(ImmediatePrinter)
    println("-".repeat(40))
    stringConstant.accept(ValuePrinter)
    stringConstant.accept(ValuePrinter as ArkImmediate.Visitor<Unit>)
    stringConstant.accept(ValuePrinter as ArkConstant.Visitor<Unit>)
    stringConstant.accept(ImmediatePrinter)
    stringConstant.accept(ImmediatePrinter as ArkConstant.Visitor<Unit>)
    println("-".repeat(40))
    arrayLiteral.accept(ValuePrinter)
    arrayLiteral.accept(ValuePrinter as ArkImmediate.Visitor<Unit>)
    arrayLiteral.accept(ValuePrinter as ArkConstant.Visitor<Unit>)
    arrayLiteral.accept(ImmediatePrinter)
    arrayLiteral.accept(ImmediatePrinter as ArkConstant.Visitor<Unit>)
    println("-".repeat(40))
    expr.accept(ValuePrinter)
    expr.accept(ValuePrinter as ArkExpr.Visitor<Unit>)
    println("-".repeat(40))
    ref.accept(ValuePrinter)
    ref.accept(ValuePrinter as ArkRef.Visitor<Unit>)
}
