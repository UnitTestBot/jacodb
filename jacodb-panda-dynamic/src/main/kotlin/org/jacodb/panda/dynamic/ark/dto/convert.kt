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

import org.jacodb.panda.dynamic.ark.base.AnyType as ArkAnyType
import org.jacodb.panda.dynamic.ark.base.ArrayAccess as ArkArrayAccess
import org.jacodb.panda.dynamic.ark.base.ArrayLiteral as ArkArrayLiteral
import org.jacodb.panda.dynamic.ark.base.ArrayType as ArkArrayType
import org.jacodb.panda.dynamic.ark.base.AssignStmt as ArkAssignStmt
import org.jacodb.panda.dynamic.ark.base.BinaryOp as ArkBinaryOp
import org.jacodb.panda.dynamic.ark.base.BinaryOperation as ArkBinaryOperation
import org.jacodb.panda.dynamic.ark.base.BooleanConstant as ArkBooleanConstant
import org.jacodb.panda.dynamic.ark.base.BooleanType as ArkBooleanType
import org.jacodb.panda.dynamic.ark.base.CallExpr as ArkCallExpr
import org.jacodb.panda.dynamic.ark.base.CallStmt as ArkCallStmt
import org.jacodb.panda.dynamic.ark.base.CastExpr as ArkCastExpr
import org.jacodb.panda.dynamic.ark.base.ClassType as ArkClassType
import org.jacodb.panda.dynamic.ark.base.ConditionExpr as ArkConditionExpr
import org.jacodb.panda.dynamic.ark.base.DeleteStmt as ArkDeleteStmt
import org.jacodb.panda.dynamic.ark.base.FieldRef as ArkFieldRef
import org.jacodb.panda.dynamic.ark.base.GotoStmt as ArkGotoStmt
import org.jacodb.panda.dynamic.ark.base.IfStmt as ArkIfStmt
import org.jacodb.panda.dynamic.ark.base.InstanceCallExpr as ArkInstanceCallExpr
import org.jacodb.panda.dynamic.ark.base.InstanceFieldRef as ArkInstanceFieldRef
import org.jacodb.panda.dynamic.ark.base.InstanceOfExpr as ArkInstanceOfExpr
import org.jacodb.panda.dynamic.ark.base.LengthExpr as ArkLengthExpr
import org.jacodb.panda.dynamic.ark.base.Local as ArkLocal
import org.jacodb.panda.dynamic.ark.base.NeverType as ArkNeverType
import org.jacodb.panda.dynamic.ark.base.NewArrayExpr as ArkNewArrayExpr
import org.jacodb.panda.dynamic.ark.base.NewExpr as ArkNewExpr
import org.jacodb.panda.dynamic.ark.base.NopStmt as ArkNopStmt
import org.jacodb.panda.dynamic.ark.base.NullConstant as ArkNullConstant
import org.jacodb.panda.dynamic.ark.base.NullType as ArkNullType
import org.jacodb.panda.dynamic.ark.base.NumberConstant as ArkNumberConstant
import org.jacodb.panda.dynamic.ark.base.NumberType as ArkNumberType
import org.jacodb.panda.dynamic.ark.base.ParameterRef as ArkParameterRef
import org.jacodb.panda.dynamic.ark.base.PhiExpr as ArkPhiExpr
import org.jacodb.panda.dynamic.ark.base.RelationOperation as ArkRelationOperation
import org.jacodb.panda.dynamic.ark.base.ReturnStmt as ArkReturnStmt
import org.jacodb.panda.dynamic.ark.base.StaticCallExpr as ArkStaticCallExpr
import org.jacodb.panda.dynamic.ark.base.StaticFieldRef as ArkStaticFieldRef
import org.jacodb.panda.dynamic.ark.base.Stmt as ArkStmt
import org.jacodb.panda.dynamic.ark.base.StringConstant as ArkStringConstant
import org.jacodb.panda.dynamic.ark.base.StringType as ArkStringType
import org.jacodb.panda.dynamic.ark.base.SwitchStmt as ArkSwitchStmt
import org.jacodb.panda.dynamic.ark.base.This as ArkThis
import org.jacodb.panda.dynamic.ark.base.ThrowStmt as ArkThrowStmt
import org.jacodb.panda.dynamic.ark.base.Type as ArkType
import org.jacodb.panda.dynamic.ark.base.TypeOfExpr as ArkTypeOfExpr
import org.jacodb.panda.dynamic.ark.base.UnaryOp as ArkUnaryOp
import org.jacodb.panda.dynamic.ark.base.UnaryOperation as ArkUnaryOperation
import org.jacodb.panda.dynamic.ark.base.UndefinedConstant as ArkUndefinedConstant
import org.jacodb.panda.dynamic.ark.base.UndefinedType as ArkUndefinedType
import org.jacodb.panda.dynamic.ark.base.UnknownType as ArkUnknownType
import org.jacodb.panda.dynamic.ark.base.Value as ArkValue
import org.jacodb.panda.dynamic.ark.base.VoidType as ArkVoidType
import org.jacodb.panda.dynamic.ark.model.ClassSignature as ArkClassSignature
import org.jacodb.panda.dynamic.ark.model.FieldSignature as ArkFieldSignature
import org.jacodb.panda.dynamic.ark.model.FieldSubSignature as ArkFieldSubSignature
import org.jacodb.panda.dynamic.ark.model.MethodParameter as ArkMethodParameter
import org.jacodb.panda.dynamic.ark.model.MethodSignature as ArkMethodSignature
import org.jacodb.panda.dynamic.ark.model.MethodSubSignature as ArkMethodSubSignature

fun convertToArkStmt(stmt: Stmt): ArkStmt {
    return when (stmt) {
        is UnknownStmt -> object : ArkStmt {
            override fun toString(): String = "UnknownStmt"

            override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
                error("UnknownStmt is not supported")
            }
        }

        is NopStmt -> ArkNopStmt

        is AssignStmt -> ArkAssignStmt(
            left = convertToArkValue(stmt.left) as ArkLocal,
            right = convertToArkValue(stmt.right),
        )

        is CallStmt -> ArkCallStmt(
            expr = convertToArkValue(stmt.expr) as ArkCallExpr
        )

        is DeleteStmt -> ArkDeleteStmt(
            arg = convertToArkFieldRef(stmt.arg)
        )

        is ReturnStmt -> ArkReturnStmt(
            arg = stmt.arg?.let { convertToArkValue(it) }
        )

        is ThrowStmt -> ArkThrowStmt(
            arg = convertToArkValue(stmt.arg)
        )

        is GotoStmt -> ArkGotoStmt

        is IfStmt -> ArkIfStmt(
            condition = convertToArkValue(stmt.condition) as ArkConditionExpr,
        )

        is SwitchStmt -> ArkSwitchStmt(
            arg = convertToArkValue(stmt.arg),
            cases = stmt.cases.map { convertToArkValue(it) },
        )

        // else -> error("Unknown Stmt: $stmt")
    }
}

fun convertToArkValue(value: Value): ArkValue {
    return when (value) {
        is UnknownValue -> object : ArkValue {
            override val type: ArkType
                get() = ArkUnknownType

            override fun toString(): String = "UnknownValue"

            override fun <R> accept(visitor: ArkValue.Visitor<R>): R {
                error("UnknownValue is not supported")
            }
        }

        is Local -> ArkLocal(
            name = value.name,
            type = convertToArkType(value.type),
        )

        is Constant -> convertToArkConstant(value)

        is NewExpr -> ArkNewExpr(
            type = convertToArkType(value.type) as ArkClassType,
        )

        is NewArrayExpr -> ArkNewArrayExpr(
            elementType = convertToArkType(value.type),
            size = convertToArkValue(value.size),
        )

        is TypeOfExpr -> ArkTypeOfExpr(
            arg = convertToArkValue(value.arg)
        )

        is InstanceOfExpr -> ArkInstanceOfExpr(
            arg = convertToArkValue(value.arg),
            checkType = convertToArkType(value.checkType),
        )

        is LengthExpr -> ArkLengthExpr(
            arg = convertToArkValue(value.arg)
        )

        is CastExpr -> ArkCastExpr(
            arg = convertToArkValue(value.arg),
            type = convertToArkType(value.type),
        )

        is PhiExpr -> ArkPhiExpr(
            args = value.args.map { convertToArkValue(it) },
            argToBlock = emptyMap(), // TODO
            type = convertToArkType(value.type),
        )

        is ArrayLiteralExpr -> ArkArrayLiteral(
            elements = value.elements.map { convertToArkValue(it) },
            type = convertToArkType(value.type) as ArkArrayType,
        )

        is UnaryOperation -> ArkUnaryOperation(
            op = convertToArkUnaryOp(value.op),
            arg = convertToArkValue(value.arg),
        )

        is BinaryOperation -> ArkBinaryOperation(
            op = convertToArkBinaryOp(value.op),
            left = convertToArkValue(value.left),
            right = convertToArkValue(value.right),
        )

        is RelationOperation -> ArkRelationOperation(
            relop = value.op,
            left = convertToArkValue(value.left),
            right = convertToArkValue(value.right),
        )

        is InstanceCallExpr -> ArkInstanceCallExpr(
            instance = convertToArkValue(value.instance) as ArkLocal, // safe cast
            method = convertToArkMethodSignature(value.method),
            args = value.args.map { convertToArkValue(it) },
        )

        is StaticCallExpr -> ArkStaticCallExpr(
            method = convertToArkMethodSignature(value.method),
            args = value.args.map { convertToArkValue(it) },
        )

        is ThisRef -> ArkThis(
            type = convertToArkType(value.type) as ArkClassType
        )

        is ParameterRef -> ArkParameterRef(
            index = value.index,
            type = convertToArkType(value.type),
        )

        is ArrayAccess -> ArkArrayAccess(
            array = convertToArkValue(value.array),
            index = convertToArkValue(value.index),
            type = convertToArkType(value.type),
        )

        is InstanceFieldRef -> ArkInstanceFieldRef(
            instance = convertToArkValue(value.instance) as ArkLocal, // safe cast
            field = convertToArkFieldSignature(value.field),
        )

        is StaticFieldRef -> ArkStaticFieldRef(
            field = convertToArkFieldSignature(value.field)
        )

        // else -> error("Unknown Value: $value")
    }
}

fun convertToArkType(type: String): ArkType {
    return when (type) {
        "any" -> ArkAnyType
        "unknown" -> ArkUnknownType
        // "union" -> ArkUnionType
        // "tuple" -> ArkTupleType
        "boolean" -> ArkBooleanType
        "number" -> ArkNumberType
        "string" -> ArkStringType
        "null" -> ArkNullType
        "undefined" -> ArkUndefinedType
        "" -> ArkVoidType
        "never" -> ArkNeverType
        // "literal" -> ArkLiteralType
        // "class" -> ArkClassType
        // "array" -> ArkArrayType
        // "object" -> ArkArrayObjectType
        // "???" -> ArkUnclearRefType
        else -> ArkUnknownType
    }
}

fun convertToArkConstant(value: Constant): ArkValue {
    return when (value.type) {
        "string" -> ArkStringConstant(
            value = value.value
        )

        "boolean" -> ArkBooleanConstant(
            value = value.value.toBoolean()
        )

        "number" -> ArkNumberConstant(
            value = value.value.toDouble()
        )

        "null" -> ArkNullConstant

        "undefined" -> ArkUndefinedConstant

        else -> error("Unknown Constant: $value")
    }
}

fun convertToArkUnaryOp(op: String): ArkUnaryOp {
    return when (op) {
        "+" -> ArkUnaryOp.Plus
        "-" -> ArkUnaryOp.Minus
        "!" -> ArkUnaryOp.Bang
        "~" -> ArkUnaryOp.Tilde
        "typeof" -> ArkUnaryOp.Typeof
        "void" -> ArkUnaryOp.Void
        "delete" -> ArkUnaryOp.Delete
        else -> error("Unknown UnaryOp: $op")
    }
}

fun convertToArkBinaryOp(op: String): ArkBinaryOp {
    return when (op) {
        "+" -> ArkBinaryOp.Add
        "-" -> ArkBinaryOp.Sub
        "*" -> ArkBinaryOp.Mul
        "/" -> ArkBinaryOp.Div
        "%" -> ArkBinaryOp.Mod
        "==" -> ArkBinaryOp.EqEq
        "!=" -> ArkBinaryOp.NotEq
        "===" -> ArkBinaryOp.EqEqEq
        "!==" -> ArkBinaryOp.NotEqEq
        "<" -> ArkBinaryOp.Lt
        "<=" -> ArkBinaryOp.LtEq
        ">" -> ArkBinaryOp.Gt
        ">=" -> ArkBinaryOp.GtEq
        "<<" -> ArkBinaryOp.LShift
        ">>" -> ArkBinaryOp.RShift
        ">>>" -> ArkBinaryOp.ZeroFillRShift
        "&" -> ArkBinaryOp.BitAnd
        "|" -> ArkBinaryOp.BitOr
        "^" -> ArkBinaryOp.BitXor
        "&&" -> ArkBinaryOp.LogicalAnd
        "||" -> ArkBinaryOp.LogicalOr
        "in" -> ArkBinaryOp.In
        "instanceof" -> ArkBinaryOp.InstanceOf
        "**" -> ArkBinaryOp.Exp
        "??" -> ArkBinaryOp.NullishCoalescing

        else -> error("Unknown BinaryOp: $op")
    }
}

fun convertToArkFieldRef(fieldRef: FieldRef): ArkFieldRef {
    val field = convertToArkFieldSignature(fieldRef.field)
    return when (fieldRef) {
        is InstanceFieldRef -> ArkInstanceFieldRef(
            instance = convertToArkValue(fieldRef.instance) as ArkLocal,
            field = field
        )

        is StaticFieldRef -> ArkStaticFieldRef(
            field = field
        )
    }
}

fun convertToArkClassSignature(clazz: ClassSignature): ArkClassSignature {
    return ArkClassSignature(
        name = clazz.name,
        namespace = null, // TODO
        file = null, // TODO
    )
}

fun convertToArkFieldSignature(field: FieldSignature): ArkFieldSignature {
    return ArkFieldSignature(
        sub = ArkFieldSubSignature(
            name = field.name,
            type = convertToArkType(field.fieldType),
            isOptional = field.optional,
        ),
        enclosingClass = convertToArkClassSignature(field.enclosingClass)
    )
}

fun convertToArkMethodSignature(method: MethodSignature): ArkMethodSignature {
    return ArkMethodSignature(
        sub = ArkMethodSubSignature(
            name = method.name,
            parameters = method.parameters.map {
                ArkMethodParameter(
                    name = it.name,
                    type = convertToArkType(it.type),
                    isOptional = it.optional,
                )
            },
            returnType = convertToArkType(method.returnType)
        ),
        enclosingClass = convertToArkClassSignature(method.enclosingClass)
    )
}
