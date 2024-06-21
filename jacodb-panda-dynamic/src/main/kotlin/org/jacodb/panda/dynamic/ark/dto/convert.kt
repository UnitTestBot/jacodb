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

import org.jacodb.panda.dynamic.ark.base.ArkAnyType
import org.jacodb.panda.dynamic.ark.base.ArkArrayAccess
import org.jacodb.panda.dynamic.ark.base.ArkArrayLiteral
import org.jacodb.panda.dynamic.ark.base.ArkAssignStmt
import org.jacodb.panda.dynamic.ark.base.ArkBinaryOperation
import org.jacodb.panda.dynamic.ark.base.ArkBooleanConstant
import org.jacodb.panda.dynamic.ark.base.ArkBooleanType
import org.jacodb.panda.dynamic.ark.base.ArkCallExpr
import org.jacodb.panda.dynamic.ark.base.ArkCallStmt
import org.jacodb.panda.dynamic.ark.base.ArkCastExpr
import org.jacodb.panda.dynamic.ark.base.ArkConditionExpr
import org.jacodb.panda.dynamic.ark.base.ArkConstant
import org.jacodb.panda.dynamic.ark.base.ArkDeleteStmt
import org.jacodb.panda.dynamic.ark.base.ArkEntity
import org.jacodb.panda.dynamic.ark.base.ArkFieldRef
import org.jacodb.panda.dynamic.ark.base.ArkGotoStmt
import org.jacodb.panda.dynamic.ark.base.ArkIfStmt
import org.jacodb.panda.dynamic.ark.base.ArkInstLocation
import org.jacodb.panda.dynamic.ark.base.ArkInstanceCallExpr
import org.jacodb.panda.dynamic.ark.base.ArkInstanceFieldRef
import org.jacodb.panda.dynamic.ark.base.ArkInstanceOfExpr
import org.jacodb.panda.dynamic.ark.base.ArkLValue
import org.jacodb.panda.dynamic.ark.base.ArkLengthExpr
import org.jacodb.panda.dynamic.ark.base.ArkLocal
import org.jacodb.panda.dynamic.ark.base.ArkNeverType
import org.jacodb.panda.dynamic.ark.base.ArkNewArrayExpr
import org.jacodb.panda.dynamic.ark.base.ArkNewExpr
import org.jacodb.panda.dynamic.ark.base.ArkNopStmt
import org.jacodb.panda.dynamic.ark.base.ArkNullConstant
import org.jacodb.panda.dynamic.ark.base.ArkNullType
import org.jacodb.panda.dynamic.ark.base.ArkNumberConstant
import org.jacodb.panda.dynamic.ark.base.ArkNumberType
import org.jacodb.panda.dynamic.ark.base.ArkObjectLiteral
import org.jacodb.panda.dynamic.ark.base.ArkParameterRef
import org.jacodb.panda.dynamic.ark.base.ArkPhiExpr
import org.jacodb.panda.dynamic.ark.base.ArkRelationOperation
import org.jacodb.panda.dynamic.ark.base.ArkReturnStmt
import org.jacodb.panda.dynamic.ark.base.ArkStaticCallExpr
import org.jacodb.panda.dynamic.ark.base.ArkStaticFieldRef
import org.jacodb.panda.dynamic.ark.base.ArkStmt
import org.jacodb.panda.dynamic.ark.base.ArkStringConstant
import org.jacodb.panda.dynamic.ark.base.ArkStringType
import org.jacodb.panda.dynamic.ark.base.ArkSwitchStmt
import org.jacodb.panda.dynamic.ark.base.ArkThis
import org.jacodb.panda.dynamic.ark.base.ArkThrowStmt
import org.jacodb.panda.dynamic.ark.base.ArkType
import org.jacodb.panda.dynamic.ark.base.ArkTypeOfExpr
import org.jacodb.panda.dynamic.ark.base.ArkUnaryOperation
import org.jacodb.panda.dynamic.ark.base.ArkUnclearRefType
import org.jacodb.panda.dynamic.ark.base.ArkUndefinedConstant
import org.jacodb.panda.dynamic.ark.base.ArkUndefinedType
import org.jacodb.panda.dynamic.ark.base.ArkUnknownType
import org.jacodb.panda.dynamic.ark.base.ArkValue
import org.jacodb.panda.dynamic.ark.base.ArkVoidType
import org.jacodb.panda.dynamic.ark.base.BinaryOp
import org.jacodb.panda.dynamic.ark.base.UnaryOp
import org.jacodb.panda.dynamic.ark.graph.ArkCfg
import org.jacodb.panda.dynamic.ark.model.ArkClass
import org.jacodb.panda.dynamic.ark.model.ArkClassImpl
import org.jacodb.panda.dynamic.ark.model.ArkClassSignature
import org.jacodb.panda.dynamic.ark.model.ArkField
import org.jacodb.panda.dynamic.ark.model.ArkFieldImpl
import org.jacodb.panda.dynamic.ark.model.ArkFieldSignature
import org.jacodb.panda.dynamic.ark.model.ArkFieldSubSignature
import org.jacodb.panda.dynamic.ark.model.ArkFile
import org.jacodb.panda.dynamic.ark.model.ArkMethod
import org.jacodb.panda.dynamic.ark.model.ArkMethodImpl
import org.jacodb.panda.dynamic.ark.model.ArkMethodParameter
import org.jacodb.panda.dynamic.ark.model.ArkMethodSignature
import org.jacodb.panda.dynamic.ark.model.ArkMethodSubSignature

fun convertToArkStmt(stmt: StmtDto, location: ArkInstLocation): ArkStmt {
    return when (stmt) {
        is UnknownStmtDto -> object : ArkStmt {
            override val location: ArkInstLocation = location

            override fun toString(): String = "UNKNOWN"

            override fun <R> accept(visitor: ArkStmt.Visitor<R>): R {
                error("UnknownStmt is not supported")
            }
        }

        is NopStmtDto -> ArkNopStmt(location = location)

        is AssignStmtDto -> ArkAssignStmt(
            location = location,
            lhv = convertToArkEntity(stmt.left) as ArkLValue,
            rhv = convertToArkEntity(stmt.right),
        )

        is CallStmtDto -> ArkCallStmt(
            location = location,
            expr = convertToArkEntity(stmt.expr) as ArkCallExpr,
        )

        is DeleteStmtDto -> ArkDeleteStmt(
            location = location,
            arg = convertToArkFieldRef(stmt.arg),
        )

        is ReturnStmtDto -> ArkReturnStmt(
            location = location,
            arg = convertToArkEntity(stmt.arg),
        )

        is ReturnVoidStmtDto -> ArkReturnStmt(
            location = location,
            arg = null,
        )

        is ThrowStmtDto -> ArkThrowStmt(
            location = location,
            arg = convertToArkEntity(stmt.arg),
        )

        is GotoStmtDto -> ArkGotoStmt(location = location)

        is IfStmtDto -> ArkIfStmt(
            location = location,
            condition = convertToArkEntity(stmt.condition) as ArkConditionExpr,
        )

        is SwitchStmtDto -> ArkSwitchStmt(
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
                get() = ArkUnknownType

            override fun toString(): String = "UNKNOWN"

            override fun <R> accept(visitor: ArkEntity.Visitor<R>): R {
                if (visitor is ArkEntity.Visitor.Default<R>) {
                    return visitor.defaultVisit(this)
                }
                error("UnknownEntity is not supported")
            }
        }

        is LocalDto -> ArkLocal(
            name = value.name,
            type = convertToArkType(value.type),
        )

        is ConstantDto -> convertToArkConstant(value)

        is NewExprDto -> ArkNewExpr(
            type = convertToArkType(value.type) // as ClassType
        )

        is NewArrayExprDto -> ArkNewArrayExpr(
            elementType = convertToArkType(value.type),
            size = convertToArkEntity(value.size),
        )

        is TypeOfExprDto -> ArkTypeOfExpr(
            arg = convertToArkEntity(value.arg)
        )

        is InstanceOfExprDto -> ArkInstanceOfExpr(
            arg = convertToArkEntity(value.arg),
            checkType = convertToArkType(value.checkType),
        )

        is LengthExprDto -> ArkLengthExpr(
            arg = convertToArkEntity(value.arg)
        )

        is CastExprDto -> ArkCastExpr(
            arg = convertToArkEntity(value.arg),
            type = convertToArkType(value.type),
        )

        is PhiExprDto -> ArkPhiExpr(
            args = value.args.map { convertToArkEntity(it) },
            argToBlock = emptyMap(), // TODO
            type = convertToArkType(value.type),
        )

        is ArrayLiteralDto -> ArkArrayLiteral(
            elements = value.elements.map { convertToArkEntity(it) },
            type = convertToArkType(value.type), // TODO: as ArkArrayType,
        )

        is ObjectLiteralDto -> ArkObjectLiteral(
            properties = emptyList(), // TODO
            type = convertToArkType(value.type),
        )

        is UnaryOperationDto -> ArkUnaryOperation(
            op = convertToArkUnaryOp(value.op),
            arg = convertToArkEntity(value.arg),
        )

        is BinaryOperationDto -> ArkBinaryOperation(
            op = convertToArkBinaryOp(value.op),
            left = convertToArkEntity(value.left),
            right = convertToArkEntity(value.right),
        )

        is RelationOperationDto -> ArkRelationOperation(
            relop = value.op,
            left = convertToArkEntity(value.left),
            right = convertToArkEntity(value.right),
        )

        is InstanceCallExprDto -> ArkInstanceCallExpr(
            instance = convertToArkEntity(value.instance),
            method = convertToArkMethodSignature(value.method),
            args = value.args.map { convertToArkEntity(it) as ArkValue },
        )

        is StaticCallExprDto -> ArkStaticCallExpr(
            method = convertToArkMethodSignature(value.method),
            args = value.args.map { convertToArkEntity(it) as ArkValue },
        )

        is ThisRefDto -> ArkThis(
            type = convertToArkType(value.type) // TODO: as ClassType
        )

        is ParameterRefDto -> ArkParameterRef(
            index = value.index,
            type = convertToArkType(value.type),
        )

        is ArrayRefDto -> ArkArrayAccess(
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
        "any" -> ArkAnyType
        "unknown" -> ArkUnknownType
        // "union" -> UnionType
        // "tuple" -> TupleType
        "boolean" -> ArkBooleanType
        "number" -> ArkNumberType
        "string" -> ArkStringType
        "null" -> ArkNullType
        "undefined" -> ArkUndefinedType
        "void" -> ArkVoidType
        "never" -> ArkNeverType
        // "literal" -> LiteralType
        // "class" -> ClassType
        // "array" -> ArrayType
        // "object" -> ArrayObjectType
        else -> ArkUnclearRefType(type)
    }
}

fun convertToArkConstant(value: ConstantDto): ArkConstant {
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

        "unknown" -> object : ArkConstant {
            override val type: ArkType
                get() = ArkUnknownType

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
        "MinusToken" -> UnaryOp.Minus
        "PlusToken" -> UnaryOp.Plus
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

fun convertToArkFieldRef(fieldRef: FieldRefDto): ArkFieldRef {
    val field = convertToArkFieldSignature(fieldRef.field)
    return when (fieldRef) {
        is InstanceFieldRefDto -> ArkInstanceFieldRef(
            instance = convertToArkEntity(fieldRef.instance), // as Local
            field = field
        )

        is StaticFieldRefDto -> ArkStaticFieldRef(
            field = field
        )
    }
}

fun convertToArkClassSignature(clazz: ClassSignatureDto): ArkClassSignature {
    return ArkClassSignature(
        name = clazz.name,
        namespace = null, // TODO
        file = null, // TODO
    )
}

fun convertToArkFieldSignature(field: FieldSignatureDto): ArkFieldSignature {
    return ArkFieldSignature(
        enclosingClass = convertToArkClassSignature(field.enclosingClass),
        sub = ArkFieldSubSignature(
            name = field.name,
            type = convertToArkType(field.fieldType),
        )
    )
}

fun convertToArkMethodSignature(method: MethodSignatureDto): ArkMethodSignature {
    return ArkMethodSignature(
        enclosingClass = convertToArkClassSignature(method.enclosingClass),
        sub = ArkMethodSubSignature(
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
    // Note: locals are not used in the current implementation
    // val locals = method.body.locals.map {
    //     convertToArkEntity(it) as ArkLocal  // safe cast
    // }
    val arkMethod = ArkMethodImpl(signature)
    val cfg = cfg2cfg(method.body.cfg, arkMethod)
    arkMethod.cfg = cfg
    return arkMethod
}

fun cfg2cfg(cfg: CfgDto, arkMethod: ArkMethod): ArkCfg {
    val stmts: MutableList<ArkStmt> = mutableListOf()
    val blocks = cfg.blocks.associateBy { it.id }
    val visited: MutableSet<Int> = hashSetOf()
    val queue: ArrayDeque<Int> = ArrayDeque()
    queue.add(0)
    val blockStart: MutableMap<Int, Int> = hashMapOf()
    while (queue.isNotEmpty()) {
        val block = blocks[queue.removeFirst()]!!
        for ((i, stmt) in block.stmts.withIndex()) {
            if (i == 0) {
                blockStart[block.id] = stmts.size
            }
            val location = ArkInstLocation(arkMethod, stmts.size)
            stmts += convertToArkStmt(stmt, location)
        }
        for (next in block.successors) {
            if (visited.add(next)) {
                queue.addLast(next)
            }
        }
    }
    val successorMap: MutableMap<ArkStmt, List<ArkStmt>> = hashMapOf()
    for (block in cfg.blocks) {
        for (i in block.stmts.indices) {
            val arkStmt = stmts[blockStart[block.id]!! + i]
            if (i == block.stmts.lastIndex) {
                successorMap[arkStmt] = block.successors.mapNotNull { id ->
                    blockStart[id]?.let { stmts[it] }
                }
            } else {
                successorMap[arkStmt] = listOf(stmts[i + 1])
            }
        }
    }
    return ArkCfg(stmts, successorMap)
}

fun convertToArkField(field: FieldDto): ArkField {
    return ArkFieldImpl(
        signature = ArkFieldSignature(
            enclosingClass = convertToArkClassSignature(field.signature.enclosingClass),
            sub = ArkFieldSubSignature(
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
        signature = ArkClassSignature(
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
