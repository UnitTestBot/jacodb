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

package org.jacodb.panda.dynamic.ark.dto

import org.jacodb.panda.dynamic.ark.base.AnyType
import org.jacodb.panda.dynamic.ark.base.ArrayAccess
import org.jacodb.panda.dynamic.ark.base.ArrayLiteral
import org.jacodb.panda.dynamic.ark.base.ArrayType
import org.jacodb.panda.dynamic.ark.base.AssignStmt
import org.jacodb.panda.dynamic.ark.base.BinaryOp
import org.jacodb.panda.dynamic.ark.base.BinaryOperation
import org.jacodb.panda.dynamic.ark.base.BooleanConstant
import org.jacodb.panda.dynamic.ark.base.BooleanType
import org.jacodb.panda.dynamic.ark.base.CallExpr
import org.jacodb.panda.dynamic.ark.base.CallStmt
import org.jacodb.panda.dynamic.ark.base.CastExpr
import org.jacodb.panda.dynamic.ark.base.ClassType
import org.jacodb.panda.dynamic.ark.base.ConditionExpr
import org.jacodb.panda.dynamic.ark.base.Constant
import org.jacodb.panda.dynamic.ark.base.DeleteStmt
import org.jacodb.panda.dynamic.ark.base.FieldRef
import org.jacodb.panda.dynamic.ark.base.GotoStmt
import org.jacodb.panda.dynamic.ark.base.IfStmt
import org.jacodb.panda.dynamic.ark.base.InstanceCallExpr
import org.jacodb.panda.dynamic.ark.base.InstanceFieldRef
import org.jacodb.panda.dynamic.ark.base.InstanceOfExpr
import org.jacodb.panda.dynamic.ark.base.LengthExpr
import org.jacodb.panda.dynamic.ark.base.Local
import org.jacodb.panda.dynamic.ark.base.NeverType
import org.jacodb.panda.dynamic.ark.base.NewArrayExpr
import org.jacodb.panda.dynamic.ark.base.NewExpr
import org.jacodb.panda.dynamic.ark.base.NopStmt
import org.jacodb.panda.dynamic.ark.base.NullConstant
import org.jacodb.panda.dynamic.ark.base.NullType
import org.jacodb.panda.dynamic.ark.base.NumberConstant
import org.jacodb.panda.dynamic.ark.base.NumberType
import org.jacodb.panda.dynamic.ark.base.ParameterRef
import org.jacodb.panda.dynamic.ark.base.PhiExpr
import org.jacodb.panda.dynamic.ark.base.RelationOperation
import org.jacodb.panda.dynamic.ark.base.ReturnStmt
import org.jacodb.panda.dynamic.ark.base.StaticCallExpr
import org.jacodb.panda.dynamic.ark.base.StaticFieldRef
import org.jacodb.panda.dynamic.ark.base.Stmt
import org.jacodb.panda.dynamic.ark.base.StringConstant
import org.jacodb.panda.dynamic.ark.base.StringType
import org.jacodb.panda.dynamic.ark.base.SwitchStmt
import org.jacodb.panda.dynamic.ark.base.This
import org.jacodb.panda.dynamic.ark.base.ThrowStmt
import org.jacodb.panda.dynamic.ark.base.Type
import org.jacodb.panda.dynamic.ark.base.TypeOfExpr
import org.jacodb.panda.dynamic.ark.base.UnaryOp
import org.jacodb.panda.dynamic.ark.base.UnaryOperation
import org.jacodb.panda.dynamic.ark.base.UnclearRefType
import org.jacodb.panda.dynamic.ark.base.UndefinedConstant
import org.jacodb.panda.dynamic.ark.base.UndefinedType
import org.jacodb.panda.dynamic.ark.base.UnknownType
import org.jacodb.panda.dynamic.ark.base.Value
import org.jacodb.panda.dynamic.ark.base.VoidType
import org.jacodb.panda.dynamic.ark.model.ArkClass
import org.jacodb.panda.dynamic.ark.model.ArkClassImpl
import org.jacodb.panda.dynamic.ark.model.ArkField
import org.jacodb.panda.dynamic.ark.model.ArkFieldImpl
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.jacodb.panda.dynamic.ark.model.ArkMethod
import org.jacodb.panda.dynamic.ark.model.ArkMethodImpl
import org.jacodb.panda.dynamic.ark.model.ClassSignature
import org.jacodb.panda.dynamic.ark.model.FieldSignature
import org.jacodb.panda.dynamic.ark.model.FieldSubSignature
import org.jacodb.panda.dynamic.ark.model.MethodParameter
import org.jacodb.panda.dynamic.ark.model.MethodSignature
import org.jacodb.panda.dynamic.ark.model.MethodSubSignature

fun convertToArkStmt(stmt: StmtDto): Stmt {
    return when (stmt) {
        is UnknownStmtDto -> object : Stmt {
            override fun toString(): String = "UnknownStmt"

            override fun <R> accept(visitor: Stmt.Visitor<R>): R {
                error("UnknownStmt is not supported")
            }
        }

        is NopStmtDto -> NopStmt

        is AssignStmtDto -> AssignStmt(
            left = convertToArkValue(stmt.left),
            right = convertToArkValue(stmt.right),
        )

        is CallStmtDto -> CallStmt(
            expr = convertToArkValue(stmt.expr) as CallExpr
        )

        is DeleteStmtDto -> DeleteStmt(
            arg = convertToArkFieldRef(stmt.arg)
        )

        is ReturnStmtDto -> ReturnStmt(
            arg = convertToArkValue(stmt.arg)
        )

        is ReturnVoidStmtDto -> ReturnStmt(null)

        is ThrowStmtDto -> ThrowStmt(
            arg = convertToArkValue(stmt.arg)
        )

        is GotoStmtDto -> GotoStmt

        is IfStmtDto -> IfStmt(
            condition = convertToArkValue(stmt.condition) as ConditionExpr,
        )

        is SwitchStmtDto -> SwitchStmt(
            arg = convertToArkValue(stmt.arg),
            cases = stmt.cases.map { convertToArkValue(it) },
        )

        // else -> error("Unknown Stmt: $stmt")
    }
}

fun convertToArkValue(value: ValueDto): Value {
    return when (value) {
        is UnknownValueDto -> object : Value {
            override val type: Type
                get() = UnknownType

            override fun toString(): String = "UnknownValue"

            override fun <R> accept(visitor: Value.Visitor<R>): R {
                error("UnknownValue is not supported")
            }
        }

        is LocalDto -> Local(
            name = value.name,
            type = convertToArkType(value.type),
        )

        is ConstantDto -> convertToArkConstant(value)

        is NewExprDto -> NewExpr(
            type = convertToArkType(value.type) // as ClassType
        )

        is NewArrayExprDto -> NewArrayExpr(
            elementType = convertToArkType(value.type),
            size = convertToArkValue(value.size),
        )

        is TypeOfExprDto -> TypeOfExpr(
            arg = convertToArkValue(value.arg)
        )

        is InstanceOfExprDto -> InstanceOfExpr(
            arg = convertToArkValue(value.arg),
            checkType = convertToArkType(value.checkType),
        )

        is LengthExprDto -> LengthExpr(
            arg = convertToArkValue(value.arg)
        )

        is CastExprDto -> CastExpr(
            arg = convertToArkValue(value.arg),
            type = convertToArkType(value.type),
        )

        is PhiExprDto -> PhiExpr(
            args = value.args.map { convertToArkValue(it) },
            argToBlock = emptyMap(), // TODO
            type = convertToArkType(value.type),
        )

        is ArrayLiteralDto -> ArrayLiteral(
            elements = value.elements.map { convertToArkValue(it) },
            type = convertToArkType(value.type) as ArrayType,
        )

        is UnaryOperationDto -> UnaryOperation(
            op = convertToArkUnaryOp(value.op),
            arg = convertToArkValue(value.arg),
        )

        is BinaryOperationDto -> BinaryOperation(
            op = convertToArkBinaryOp(value.op),
            left = convertToArkValue(value.left),
            right = convertToArkValue(value.right),
        )

        is RelationOperationDto -> RelationOperation(
            relop = value.op,
            left = convertToArkValue(value.left),
            right = convertToArkValue(value.right),
        )

        is InstanceCallExprDto -> InstanceCallExpr(
            instance = convertToArkValue(value.instance) as Local, // safe cast
            method = convertToArkMethodSignature(value.method),
            args = value.args.map { convertToArkValue(it) },
        )

        is StaticCallExprDto -> StaticCallExpr(
            method = convertToArkMethodSignature(value.method),
            args = value.args.map { convertToArkValue(it) },
        )

        is ThisRefDto -> This(
            type = convertToArkType(value.type) // as ClassType
        )

        is ParameterRefDto -> ParameterRef(
            index = value.index,
            type = convertToArkType(value.type),
        )

        is ArrayRefDto -> ArrayAccess(
            array = convertToArkValue(value.array),
            index = convertToArkValue(value.index),
            type = convertToArkType(value.type),
        )

        is FieldRefDto -> convertToArkFieldRef(value)

        // else -> error("Unknown Value: $value")
    }
}

fun convertToArkType(type: String): Type {
    return when (type) {
        "any" -> AnyType
        "unknown" -> UnknownType
        // "union" -> UnionType
        // "tuple" -> TupleType
        "boolean" -> BooleanType
        "number" -> NumberType
        "string" -> StringType
        "null" -> NullType
        "undefined" -> UndefinedType
        "void" -> VoidType
        "never" -> NeverType
        // "literal" -> LiteralType
        // "class" -> ClassType
        // "array" -> ArrayType
        // "object" -> ArrayObjectType
        else -> UnclearRefType(type)
    }
}

fun convertToArkConstant(value: ConstantDto): Constant {
    return when (value.type) {
        "string" -> StringConstant(
            value = value.value
        )

        "boolean" -> BooleanConstant(
            value = value.value.toBoolean()
        )

        "number" -> NumberConstant(
            value = value.value.toDouble()
        )

        "null" -> NullConstant

        "undefined" -> UndefinedConstant

        "unknown" -> object: Constant {
            override val type: Type
                get() = UnknownType

            override fun toString(): String = "UnknownConstant(${value.value})"

            override fun <R> accept(visitor: Constant.Visitor<R>): R {
                TODO("UnknownConstant is not supported")
            }
        }

        else -> error("Unknown Constant: $value")
    }
}

fun convertToArkUnaryOp(op: String): UnaryOp {
    return when (op) {
        "+" -> UnaryOp.Plus
        "-" -> UnaryOp.Minus
        "!" -> UnaryOp.Bang
        "~" -> UnaryOp.Tilde
        "typeof" -> UnaryOp.Typeof
        "void" -> UnaryOp.Void
        "delete" -> UnaryOp.Delete
        else -> error("Unknown UnaryOp: $op")
    }
}

fun convertToArkBinaryOp(op: String): BinaryOp {
    return when (op) {
        "+" -> BinaryOp.Add
        "-" -> BinaryOp.Sub
        "*" -> BinaryOp.Mul
        "/" -> BinaryOp.Div
        "%" -> BinaryOp.Mod
        "==" -> BinaryOp.EqEq
        "!=" -> BinaryOp.NotEq
        "===" -> BinaryOp.EqEqEq
        "!==" -> BinaryOp.NotEqEq
        "<" -> BinaryOp.Lt
        "<=" -> BinaryOp.LtEq
        ">" -> BinaryOp.Gt
        ">=" -> BinaryOp.GtEq
        "<<" -> BinaryOp.LShift
        ">>" -> BinaryOp.RShift
        ">>>" -> BinaryOp.ZeroFillRShift
        "&" -> BinaryOp.BitAnd
        "|" -> BinaryOp.BitOr
        "^" -> BinaryOp.BitXor
        "&&" -> BinaryOp.LogicalAnd
        "||" -> BinaryOp.LogicalOr
        "in" -> BinaryOp.In
        "instanceof" -> BinaryOp.InstanceOf
        "**" -> BinaryOp.Exp
        "??" -> BinaryOp.NullishCoalescing

        else -> error("Unknown BinaryOp: $op")
    }
}

fun convertToArkFieldRef(fieldRef: FieldRefDto): FieldRef {
    val field = convertToArkFieldSignature(fieldRef.field)
    return when (fieldRef) {
        is InstanceFieldRefDto -> InstanceFieldRef(
            instance = convertToArkValue(fieldRef.instance), // as Local
            field = field
        )

        is StaticFieldRefDto -> StaticFieldRef(
            field = field
        )
    }
}

fun convertToArkClassSignature(clazz: ClassSignatureDto): ClassSignature {
    return ClassSignature(
        name = clazz.name,
        namespace = null, // TODO
        file = null, // TODO
    )
}

fun convertToArkFieldSignature(field: FieldSignatureDto): FieldSignature {
    return FieldSignature(
        enclosingClass = convertToArkClassSignature(field.enclosingClass),
        sub = FieldSubSignature(
            name = field.name,
            type = convertToArkType(field.fieldType),
        )
    )
}

fun convertToArkMethodSignature(method: MethodSignatureDto): MethodSignature {
    return MethodSignature(
        enclosingClass = convertToArkClassSignature(method.enclosingClass),
        sub = MethodSubSignature(
            name = method.name,
            parameters = method.parameters.map { convertToArkMethodParameter(it) },
            returnType = convertToArkType(method.returnType)
        )
    )
}

fun convertToArkMethodParameter(param: MethodParameterDto): MethodParameter {
    return MethodParameter(
        name = param.name,
        type = convertToArkType(param.type),
        isOptional = param.isOptional,
    )
}

fun convertToArkMethod(method: MethodDto): ArkMethod {
    return ArkMethodImpl(
        signature = MethodSignature(
            enclosingClass = convertToArkClassSignature(method.signature.enclosingClass),
            sub = MethodSubSignature(
                name = method.signature.name,
                parameters = method.signature.parameters.map { convertToArkMethodParameter(it) },
                returnType = convertToArkType(method.signature.returnType)
            )
        ),
        body = method.body.map { convertToArkStmt(it) }
    )
}

fun convertToArkField(field: FieldDto): ArkField {
    return ArkFieldImpl(
        signature = FieldSignature(
            enclosingClass = convertToArkClassSignature(field.signature.enclosingClass),
            sub = FieldSubSignature(
                name = field.signature.name,
                type = convertToArkType(field.signature.fieldType)
            )
        ),
        // TODO: decorators = field.modifiers...
        isOptional = field.isOptional,
        isDefinitelyAssigned = field.isDefinitelyAssigned,
        initializer = field.initializer?.let { convertToArkValue(it) }
    )
}

fun convertToArkClass(clazz: ClassDto): ArkClass {
    return ArkClassImpl(
        signature = ClassSignature(
            name = clazz.signature.name,
            namespace = null, // TODO
            file = null, // TODO
        ),
        fields = clazz.fields.map { convertToArkField(it) },
        methods = clazz.methods.map { convertToArkMethod(it) }
    )
}

fun convertToArkFile(file: ArkFileDto): ArkFile {
    val classes = file.classes.map { convertToArkClass(it) }
    return ArkFile(
        path = file.absoluteFilePath,
        projectName = file.projectName,
        classes = classes
    )
}
