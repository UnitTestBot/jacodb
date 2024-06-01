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

package org.jacodb.panda.dynamic.ark

interface ValueVisitor<out R> :
    ImmediateVisitor<R>,
    ExprVisitor<R>,
    RefVisitor<R> {

    interface Default<out R> : ValueVisitor<R>,
        ImmediateVisitor.Default<R>,
        ExprVisitor.Default<R>,
        RefVisitor.Default<R> {

        fun defaultVisit(value: Value): R

        override fun defaultVisit(value: Immediate): R = defaultVisit(value as Value)
        override fun defaultVisit(expr: Expr): R = defaultVisit(expr as Value)
        override fun defaultVisit(ref: Ref): R = defaultVisit(ref as Value)
    }
}

fun <R> Value.accept2(valueVisitor: ValueVisitor<R>): R = when (this) {
    is Immediate -> accept2(valueVisitor as ImmediateVisitor<R>)
    is Expr -> accept2(valueVisitor as ExprVisitor<R>)
    is Ref -> accept2(valueVisitor as RefVisitor<R>)
    else -> error("No visitor for $this")
}

interface ImmediateVisitor<out R> : ConstantVisitor<R> {
    fun visit(value: Local): R

    interface Default<out R> : ImmediateVisitor<R>,
        ConstantVisitor.Default<R> {

        fun defaultVisit(value: Immediate): R

        override fun defaultVisit(value: Constant): R = defaultVisit(value as Immediate)

        override fun visit(value: Local): R = defaultVisit(value)
    }
}

fun <R> Immediate.accept2(immediateVisitor: ImmediateVisitor<R>): R = when (this) {
    is Constant -> accept2(immediateVisitor as ConstantVisitor<R>)
    is Local -> immediateVisitor.visit(this)
    else -> error("No visitor for $this")
}

interface ConstantVisitor<out R> {
    fun visit(value: StringConstant): R
    fun visit(value: BooleanConstant): R
    fun visit(value: NumberConstant): R
    fun visit(value: NullConstant): R
    fun visit(value: UndefinedConstant): R
    fun visit(value: ArrayLiteral): R
    fun visit(value: ObjectLiteral): R

    interface Default<out R> : ConstantVisitor<R> {
        fun defaultVisit(value: Constant): R

        override fun visit(value: StringConstant): R = defaultVisit(value)
        override fun visit(value: BooleanConstant): R = defaultVisit(value)
        override fun visit(value: NumberConstant): R = defaultVisit(value)
        override fun visit(value: NullConstant): R = defaultVisit(value)
        override fun visit(value: UndefinedConstant): R = defaultVisit(value)
        override fun visit(value: ArrayLiteral): R = defaultVisit(value)
        override fun visit(value: ObjectLiteral): R = defaultVisit(value)
    }
}

fun <R> Constant.accept2(constantVisitor: ConstantVisitor<R>): R = when (this) {
    is StringConstant -> constantVisitor.visit(this)
    is BooleanConstant -> constantVisitor.visit(this)
    is NumberConstant -> constantVisitor.visit(this)
    is NullConstant -> constantVisitor.visit(this)
    is UndefinedConstant -> constantVisitor.visit(this)
    is ArrayLiteral -> constantVisitor.visit(this)
    is ObjectLiteral -> constantVisitor.visit(this)
    else -> error("No visitor for $this")
}

interface ExprVisitor<out R> {
    fun visit(expr: NewExpr): R
    fun visit(expr: NewArrayExpr): R
    fun visit(expr: TypeOfExpr): R
    fun visit(expr: InstanceOfExpr): R
    fun visit(expr: LengthExpr): R
    fun visit(expr: CastExpr): R
    fun visit(expr: PhiExpr): R
    fun visit(expr: UnaryOperation): R
    fun visit(expr: BinaryOperation): R
    fun visit(expr: RelationOperation): R
    fun visit(expr: InstanceCallExpr): R
    fun visit(expr: StaticCallExpr): R

    interface Default<out R> : ExprVisitor<R> {
        fun defaultVisit(expr: Expr): R

        override fun visit(expr: NewExpr): R = defaultVisit(expr)
        override fun visit(expr: NewArrayExpr): R = defaultVisit(expr)
        override fun visit(expr: TypeOfExpr): R = defaultVisit(expr)
        override fun visit(expr: InstanceOfExpr): R = defaultVisit(expr)
        override fun visit(expr: LengthExpr): R = defaultVisit(expr)
        override fun visit(expr: CastExpr): R = defaultVisit(expr)
        override fun visit(expr: PhiExpr): R = defaultVisit(expr)
        override fun visit(expr: UnaryOperation): R = defaultVisit(expr)
        override fun visit(expr: BinaryOperation): R = defaultVisit(expr)
        override fun visit(expr: RelationOperation): R = defaultVisit(expr)
        override fun visit(expr: InstanceCallExpr): R = defaultVisit(expr)
        override fun visit(expr: StaticCallExpr): R = defaultVisit(expr)
    }
}

fun <R> Expr.accept2(exprVisitor: ExprVisitor<R>): R = when (this) {
    is NewExpr -> exprVisitor.visit(this)
    is NewArrayExpr -> exprVisitor.visit(this)
    is TypeOfExpr -> exprVisitor.visit(this)
    is InstanceOfExpr -> exprVisitor.visit(this)
    is LengthExpr -> exprVisitor.visit(this)
    is CastExpr -> exprVisitor.visit(this)
    is PhiExpr -> exprVisitor.visit(this)
    is UnaryOperation -> exprVisitor.visit(this)
    is BinaryOperation -> exprVisitor.visit(this)
    is InstanceCallExpr -> exprVisitor.visit(this)
    is StaticCallExpr -> exprVisitor.visit(this)
    else -> error("No visitor for $this")
}

interface RefVisitor<out R> {
    fun visit(ref: This): R
    fun visit(ref: ParameterRef): R
    fun visit(ref: ArrayAccess): R
    fun visit(ref: InstanceFieldRef): R
    fun visit(ref: StaticFieldRef): R

    interface Default<out R> : RefVisitor<R> {
        fun defaultVisit(ref: Ref): R

        override fun visit(ref: This): R = defaultVisit(ref)
        override fun visit(ref: ParameterRef): R = defaultVisit(ref)
        override fun visit(ref: ArrayAccess): R = defaultVisit(ref)
        override fun visit(ref: InstanceFieldRef): R = defaultVisit(ref)
        override fun visit(ref: StaticFieldRef): R = defaultVisit(ref)
    }
}

fun <R> Ref.accept2(refVisitor: RefVisitor<R>): R = when (this) {
    is This -> refVisitor.visit(this)
    is ParameterRef -> refVisitor.visit(this)
    is ArrayAccess -> refVisitor.visit(this)
    is InstanceFieldRef -> refVisitor.visit(this)
    is StaticFieldRef -> refVisitor.visit(this)
    else -> error("No visitor for $this")
}

object ValuePrinter : ValueVisitor.Default<Unit> {
    override fun defaultVisit(value: Value) {
        println("defaultVisit<Value>($value)")
    }

    override fun defaultVisit(value: Immediate) {
        println("defaultVisit<Immediate>($value)")
    }

    override fun defaultVisit(expr: Expr) {
        println("defaultVisit<Expr>($expr)")
    }

    override fun defaultVisit(ref: Ref) {
        println("defaultVisit<Ref>($ref)")
    }

    override fun defaultVisit(value: Constant) {
        println("defaultVisit<Constant>($value)")
    }

    override fun visit(value: Local) {
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

    override fun visit(expr: UnaryOperation) {
        println("visit<UnaryOperation>($expr)")
    }

    override fun visit(expr: BinaryOperation) {
        println("visit<BinaryOperation>($expr)")
    }

    override fun visit(expr: RelationOperation) {
        println("visit<RelationOperation>($expr)")
    }

    override fun visit(expr: InstanceCallExpr) {
        println("visit<InstanceCallExpr>($expr)")
    }

    override fun visit(expr: StaticCallExpr) {
        println("visit<StaticCallExpr>($expr)")
    }

    override fun visit(ref: This) {
        println("visit<This>($ref)")
    }

    override fun visit(ref: ParameterRef) {
        println("visit<ParameterRef>($ref)")
    }

    override fun visit(ref: ArrayAccess) {
        println("visit<ArrayAccess>($ref)")
    }

    override fun visit(ref: InstanceFieldRef) {
        println("visit<InstanceFieldRef>($ref)")
    }

    override fun visit(ref: StaticFieldRef) {
        println("visit<StaticFieldRef>($ref)")
    }
}

fun main() {
    val local = Local("kek", NumberType)
    val stringConstant = StringConstant("lol")
    val arrayLiteral = ArrayLiteral(listOf(local, stringConstant), ArrayType(NumberType, 1))
    val expr = BinaryOperation(BinaryOp.Add, local, stringConstant)

    local.accept2(valueVisitor = ValuePrinter)
    local.accept2(immediateVisitor = ValuePrinter)
    println("-".repeat(40))
    stringConstant.accept2(valueVisitor = ValuePrinter)
    stringConstant.accept2(immediateVisitor = ValuePrinter)
    stringConstant.accept2(constantVisitor = ValuePrinter)
    println("-".repeat(40))
    arrayLiteral.accept2(valueVisitor = ValuePrinter)
    arrayLiteral.accept2(immediateVisitor = ValuePrinter)
    arrayLiteral.accept2(constantVisitor = ValuePrinter)
    println("-".repeat(40))
    expr.accept2(valueVisitor = ValuePrinter)
    expr.accept2(exprVisitor = ValuePrinter)
}
