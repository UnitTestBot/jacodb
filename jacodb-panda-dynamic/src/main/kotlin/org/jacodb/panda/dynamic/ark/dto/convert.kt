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
import org.jacodb.panda.dynamic.ark.base.ArkConstant
import org.jacodb.panda.dynamic.ark.base.ArkEntity
import org.jacodb.panda.dynamic.ark.base.ArkInstLocation
import org.jacodb.panda.dynamic.ark.base.ArkThis
import org.jacodb.panda.dynamic.ark.base.ArkType
import org.jacodb.panda.dynamic.ark.base.ArkValue
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
import org.jacodb.panda.dynamic.ark.base.ConditionExpr
import org.jacodb.panda.dynamic.ark.base.DeleteStmt
import org.jacodb.panda.dynamic.ark.base.FieldRef
import org.jacodb.panda.dynamic.ark.base.GotoStmt
import org.jacodb.panda.dynamic.ark.base.IfStmt
import org.jacodb.panda.dynamic.ark.base.InstanceCallExpr
import org.jacodb.panda.dynamic.ark.base.InstanceFieldRef
import org.jacodb.panda.dynamic.ark.base.InstanceOfExpr
import org.jacodb.panda.dynamic.ark.base.LValue
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
import org.jacodb.panda.dynamic.ark.base.ObjectLiteral
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
import org.jacodb.panda.dynamic.ark.base.ThrowStmt
import org.jacodb.panda.dynamic.ark.base.TypeOfExpr
import org.jacodb.panda.dynamic.ark.base.UnaryOp
import org.jacodb.panda.dynamic.ark.base.UnaryOperation
import org.jacodb.panda.dynamic.ark.base.UnclearRefType
import org.jacodb.panda.dynamic.ark.base.UndefinedConstant
import org.jacodb.panda.dynamic.ark.base.UndefinedType
import org.jacodb.panda.dynamic.ark.base.UnknownType
import org.jacodb.panda.dynamic.ark.base.VoidType
import org.jacodb.panda.dynamic.ark.graph.BasicBlock
import org.jacodb.panda.dynamic.ark.graph.Cfg
import org.jacodb.panda.dynamic.ark.model.ArkClass
import org.jacodb.panda.dynamic.ark.model.ArkClassImpl
import org.jacodb.panda.dynamic.ark.model.ArkField
import org.jacodb.panda.dynamic.ark.model.ArkFieldImpl
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.jacodb.panda.dynamic.ark.model.ArkMethod
import org.jacodb.panda.dynamic.ark.model.ArkMethodImpl
import org.jacodb.panda.dynamic.ark.model.ArkMethodParameter
import org.jacodb.panda.dynamic.ark.model.ClassSignature
import org.jacodb.panda.dynamic.ark.model.FieldSignature
import org.jacodb.panda.dynamic.ark.model.FieldSubSignature
import org.jacodb.panda.dynamic.ark.model.MethodSignature
import org.jacodb.panda.dynamic.ark.model.MethodSubSignature

fun convertToArkStmt(stmt: StmtDto, location: ArkInstLocation): Stmt {
    return when (stmt) {
        is UnknownStmtDto -> object : Stmt {
            override val location: ArkInstLocation = location

            override fun toString(): String = "UnknownStmt"

            override fun <R> accept(visitor: Stmt.Visitor<R>): R {
                error("UnknownStmt is not supported")
            }
        }

        is NopStmtDto -> NopStmt(location = location)

        is AssignStmtDto -> AssignStmt(
            location = location,
            lhv = convertToArkEntity(stmt.left) as LValue,
            rhv = convertToArkEntity(stmt.right),
        )

        is CallStmtDto -> CallStmt(
            location = location,
            expr = convertToArkEntity(stmt.expr) as CallExpr,
        )

        is DeleteStmtDto -> DeleteStmt(
            location = location,
            arg = convertToArkFieldRef(stmt.arg),
        )

        is ReturnStmtDto -> ReturnStmt(
            location = location,
            arg = convertToArkEntity(stmt.arg),
        )

        is ReturnVoidStmtDto -> ReturnStmt(
            location = location,
            arg = null,
        )

        is ThrowStmtDto -> ThrowStmt(
            location = location,
            arg = convertToArkEntity(stmt.arg),
        )

        is GotoStmtDto -> GotoStmt(location = location)

        is IfStmtDto -> IfStmt(
            location = location,
            condition = convertToArkEntity(stmt.condition) as ConditionExpr,
        )

        is SwitchStmtDto -> SwitchStmt(
            location = location,
            arg = convertToArkEntity(stmt.arg),
            cases = stmt.cases.map { convertToArkEntity(it) },
        )

        // else -> error("Unknown Stmt: $stmt")
    }
}

fun convertToArkEntity(value: ValueDto): ArkEntity {
    return when (value) {
        is UnknownValueDto -> object : ArkEntity {
            override val type: ArkType
                get() = UnknownType

            override fun toString(): String = "UnknownValue"

            override fun <R> accept(visitor: ArkEntity.Visitor<R>): R {
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
            size = convertToArkEntity(value.size),
        )

        is TypeOfExprDto -> TypeOfExpr(
            arg = convertToArkEntity(value.arg)
        )

        is InstanceOfExprDto -> InstanceOfExpr(
            arg = convertToArkEntity(value.arg),
            checkType = convertToArkType(value.checkType),
        )

        is LengthExprDto -> LengthExpr(
            arg = convertToArkEntity(value.arg)
        )

        is CastExprDto -> CastExpr(
            arg = convertToArkEntity(value.arg),
            type = convertToArkType(value.type),
        )

        is PhiExprDto -> PhiExpr(
            args = value.args.map { convertToArkEntity(it) },
            argToBlock = emptyMap(), // TODO
            type = convertToArkType(value.type),
        )

        is ArrayLiteralDto -> ArrayLiteral(
            elements = value.elements.map { convertToArkEntity(it) },
            type = convertToArkType(value.type) as ArrayType,
        )

        is ObjectLiteralDto -> ObjectLiteral(
            properties = emptyList(), // TODO
            type = convertToArkType(value.type),
        )

        is UnaryOperationDto -> UnaryOperation(
            op = convertToArkUnaryOp(value.op),
            arg = convertToArkEntity(value.arg),
        )

        is BinaryOperationDto -> BinaryOperation(
            op = convertToArkBinaryOp(value.op),
            left = convertToArkEntity(value.left),
            right = convertToArkEntity(value.right),
        )

        is RelationOperationDto -> RelationOperation(
            relop = value.op,
            left = convertToArkEntity(value.left),
            right = convertToArkEntity(value.right),
        )

        is InstanceCallExprDto -> InstanceCallExpr(
            instance = convertToArkEntity(value.instance) as Local, // safe cast
            method = convertToArkMethodSignature(value.method),
            args = value.args.map { convertToArkEntity(it) as ArkValue },
        )

        is StaticCallExprDto -> StaticCallExpr(
            method = convertToArkMethodSignature(value.method),
            args = value.args.map { convertToArkEntity(it) as ArkValue },
        )

        is ThisRefDto -> ArkThis(
            type = convertToArkType(value.type) // as ClassType
        )

        is ParameterRefDto -> ParameterRef(
            index = value.index,
            type = convertToArkType(value.type),
        )

        is ArrayRefDto -> ArrayAccess(
            array = convertToArkEntity(value.array),
            index = convertToArkEntity(value.index),
            type = convertToArkType(value.type),
        )

        is FieldRefDto -> convertToArkFieldRef(value)

        // else -> error("Unknown Value: $value")
    }
}

fun convertToArkType(type: String): ArkType {
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

fun convertToArkConstant(value: ConstantDto): ArkConstant {
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

        "unknown" -> object : ArkConstant {
            override val type: ArkType
                get() = UnknownType

            override fun toString(): String = "UnknownConstant(${value.value})"

            override fun <R> accept(visitor: ArkConstant.Visitor<R>): R {
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
            instance = convertToArkEntity(fieldRef.instance), // as Local
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
            parameters = method.parameters.mapIndexed { index, param ->
                ArkMethodParameter(
                    index = index,
                    name = param.name,
                    type = convertToArkType(param.type),
                    isOptional = param.isOptional
                )
            },
            returnType = convertToArkType(method.returnType),
        )
    )
}

fun convertToArkMethod(method: MethodDto): ArkMethod {
    val signature = convertToArkMethodSignature(method.signature)
    val locals = method.body.locals.map {
        convertToArkEntity(it) as Local  // safe cast
    }
    val arkMethod = ArkMethodImpl(signature, locals)
    val location = ArkInstLocation(arkMethod)
    val blocks = method.body.cfg.blocks.associate { block ->
        block.id to BasicBlock(
            id = block.id,
            successors = block.successors,
            predecessors = block.predecessors,
            stmts = block.stmts.map { convertToArkStmt(it, location) },
        )
    }
    val cfg = Cfg(blocks)
    arkMethod.cfg = cfg
    return arkMethod
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
        initializer = field.initializer?.let { convertToArkEntity(it) }
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
        methods = clazz.methods.map { convertToArkMethod(it) },
    )
}

fun convertToArkFile(file: ArkFileDto): ArkFile {
    val classes = file.classes.map { convertToArkClass(it) }
    return ArkFile(
        name = file.name,
        path = file.absoluteFilePath,
        classes = classes,
    )
}
