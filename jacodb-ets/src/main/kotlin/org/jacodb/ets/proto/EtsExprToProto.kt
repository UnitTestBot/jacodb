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

package org.jacodb.ets.proto

import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsAwaitExpr
import org.jacodb.ets.model.EtsBitAndExpr
import org.jacodb.ets.model.EtsBitNotExpr
import org.jacodb.ets.model.EtsBitOrExpr
import org.jacodb.ets.model.EtsBitXorExpr
import org.jacodb.ets.model.EtsCallExpr
import org.jacodb.ets.model.EtsCastExpr
import org.jacodb.ets.model.EtsDeleteExpr
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsExpExpr
import org.jacodb.ets.model.EtsExpr
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsInExpr
import org.jacodb.ets.model.EtsInstanceCallExpr
import org.jacodb.ets.model.EtsInstanceOfExpr
import org.jacodb.ets.model.EtsLeftShiftExpr
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNewArrayExpr
import org.jacodb.ets.model.EtsNewExpr
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNullishCoalescingExpr
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsPostDecExpr
import org.jacodb.ets.model.EtsPostIncExpr
import org.jacodb.ets.model.EtsPreDecExpr
import org.jacodb.ets.model.EtsPreIncExpr
import org.jacodb.ets.model.EtsPtrCallExpr
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsRightShiftExpr
import org.jacodb.ets.model.EtsStaticCallExpr
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsTypeOfExpr
import org.jacodb.ets.model.EtsUnaryPlusExpr
import org.jacodb.ets.model.EtsUnsignedRightShiftExpr
import org.jacodb.ets.model.EtsValue
import org.jacodb.ets.model.EtsVoidExpr
import org.jacodb.ets.model.EtsYieldExpr
import model.AwaitExpr as ProtoAwaitExpr
import model.BinaryExpr as ProtoBinaryExpr
import model.BinaryOperator as ProtoBinaryOperator
import model.CallExpr as ProtoCallExpr
import model.CastExpr as ProtoCastExpr
import model.DeleteExpr as ProtoDeleteExpr
import model.Expr as ProtoExpr
import model.InstanceCall as ProtoInstanceCall
import model.InstanceOfExpr as ProtoInstanceOfExpr
import model.NewArrayExpr as ProtoNewArrayExpr
import model.NewExpr as ProtoNewExpr
import model.PtrCall as ProtoPtrCall
import model.RelationExpr as ProtoRelationExpr
import model.RelationOperator as ProtoRelationOperator
import model.StaticCall as ProtoStaticCall
import model.TypeOfExpr as ProtoTypeOfExpr
import model.UnaryExpr as ProtoUnaryExpr
import model.UnaryOperator as ProtoUnaryOperator
import model.Value as ProtoValue
import model.YieldExpr as ProtoYieldExpr

fun EtsExpr.toProto(): ProtoValue = accept(EtsExprToProto)

fun EtsCallExpr.toProto(): ProtoCallExpr {
    val callExpr = ProtoCallExpr(
        callee = this.callee.toProto(),
        args = this.args.map { (it as EtsValue).toProto() },
        type = this.type.toProto(),
    )
    return when (this) {
        is EtsInstanceCallExpr -> callExpr.copy(instance_call = ProtoInstanceCall(instance = this.instance.toProto()))
        is EtsStaticCallExpr -> callExpr.copy(static_call = ProtoStaticCall())
        is EtsPtrCallExpr -> callExpr.copy(ptr_call = ProtoPtrCall(ptr = (this.ptr as EtsValue).toProto()))
        else -> error("Unsupported call expression type: ${this::class.simpleName}")
    }
}

internal object EtsExprToProto : EtsExpr.Visitor<ProtoValue> {
    override fun visit(expr: EtsNewExpr): ProtoValue {
        val newExpr = ProtoNewExpr(
            type = expr.type.toProto(),
        )
        return ProtoValue(expr = ProtoExpr(new_expr = newExpr))
    }

    override fun visit(expr: EtsNewArrayExpr): ProtoValue {
        val newArrayExpr = ProtoNewArrayExpr(
            element_type = expr.elementType.toProto(),
            size = expr.size.toProto(),
        )
        return ProtoValue(expr = ProtoExpr(new_array_expr = newArrayExpr))
    }

    override fun visit(expr: EtsCastExpr): ProtoValue {
        val castExpr = ProtoCastExpr(
            arg = expr.arg.toProto(),
            type = expr.type.toProto(),
        )
        return ProtoValue(expr = ProtoExpr(cast_expr = castExpr))
    }

    override fun visit(expr: EtsInstanceOfExpr): ProtoValue {
        val instanceOfExpr = ProtoInstanceOfExpr(
            arg = expr.arg.toProto(),
            check_type = expr.checkType.toProto(),
        )
        return ProtoValue(expr = ProtoExpr(instance_of_expr = instanceOfExpr))
    }

    override fun visit(expr: EtsDeleteExpr): ProtoValue {
        val deleteExpr = ProtoDeleteExpr(
            arg = expr.arg.toProto(),
        )
        return ProtoValue(expr = ProtoExpr(delete_expr = deleteExpr))
    }

    override fun visit(expr: EtsAwaitExpr): ProtoValue {
        val awaitExpr = ProtoAwaitExpr(
            arg = expr.arg.toProto(),
        )
        return ProtoValue(expr = ProtoExpr(await_expr = awaitExpr))
    }

    override fun visit(expr: EtsYieldExpr): ProtoValue {
        val yieldExpr = ProtoYieldExpr(
            arg = expr.arg.toProto(),
        )
        return ProtoValue(expr = ProtoExpr(yield_expr = yieldExpr))
    }

    override fun visit(expr: EtsTypeOfExpr): ProtoValue {
        val typeOfExpr = ProtoTypeOfExpr(
            arg = expr.arg.toProto(),
        )
        return ProtoValue(expr = ProtoExpr(type_of_expr = typeOfExpr))
    }

    override fun visit(expr: EtsVoidExpr): ProtoValue {
        TODO()
    }

    override fun visit(expr: EtsNotExpr): ProtoValue {
        val notExpr = ProtoUnaryExpr(
            op = ProtoUnaryOperator.LOGICAL_NOT,
            arg = expr.arg.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(unary_expr = notExpr))
    }

    override fun visit(expr: EtsBitNotExpr): ProtoValue {
        val bitNotExpr = ProtoUnaryExpr(
            op = ProtoUnaryOperator.BITWISE_NOT,
            arg = expr.arg.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(unary_expr = bitNotExpr))
    }

    override fun visit(expr: EtsNegExpr): ProtoValue {
        val negExpr = ProtoUnaryExpr(
            op = ProtoUnaryOperator.NEG,
            arg = expr.arg.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(unary_expr = negExpr))
    }

    override fun visit(expr: EtsUnaryPlusExpr): ProtoValue {
        TODO()
    }

    override fun visit(expr: EtsPreIncExpr): ProtoValue {
        TODO()
    }

    override fun visit(expr: EtsPreDecExpr): ProtoValue {
        TODO()
    }

    override fun visit(expr: EtsPostIncExpr): ProtoValue {
        TODO()
    }

    override fun visit(expr: EtsPostDecExpr): ProtoValue {
        TODO()
    }

    override fun visit(expr: EtsEqExpr): ProtoValue {
        val eqExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.EQ,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = eqExpr))
    }

    override fun visit(expr: EtsNotEqExpr): ProtoValue {
        val notEqExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.NEQ,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = notEqExpr))
    }

    override fun visit(expr: EtsStrictEqExpr): ProtoValue {
        val strictEqExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.STRICT_EQ,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = strictEqExpr))
    }

    override fun visit(expr: EtsStrictNotEqExpr): ProtoValue {
        val strictNotEqExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.STRICT_NEQ,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = strictNotEqExpr))
    }

    override fun visit(expr: EtsLtExpr): ProtoValue {
        val ltExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.LT,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = ltExpr))
    }

    override fun visit(expr: EtsLtEqExpr): ProtoValue {
        val ltEqExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.LTE,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = ltEqExpr))
    }

    override fun visit(expr: EtsGtExpr): ProtoValue {
        val gtExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.GT,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = gtExpr))
    }

    override fun visit(expr: EtsGtEqExpr): ProtoValue {
        val gtEqExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.GTE,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = gtEqExpr))
    }

    override fun visit(expr: EtsInExpr): ProtoValue {
        val inExpr = ProtoRelationExpr(
            op = ProtoRelationOperator.IN,
            left = expr.left.toProto(),
            right = expr.right.toProto()
        )
        return ProtoValue(expr = ProtoExpr(relation_expr = inExpr))
    }

    override fun visit(expr: EtsAddExpr): ProtoValue {
        val addExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.ADDITION,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = addExpr))
    }

    override fun visit(expr: EtsSubExpr): ProtoValue {
        val subExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.SUBTRACTION,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = subExpr))
    }

    override fun visit(expr: EtsMulExpr): ProtoValue {
        val mulExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.MULTIPLICATION,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = mulExpr))
    }

    override fun visit(expr: EtsDivExpr): ProtoValue {
        val divExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.DIVISION,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = divExpr))
    }

    override fun visit(expr: EtsRemExpr): ProtoValue {
        val remExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.REMAINDER,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = remExpr))
    }

    override fun visit(expr: EtsExpExpr): ProtoValue {
        val expExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.EXPONENTIATION,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = expExpr))
    }

    override fun visit(expr: EtsBitAndExpr): ProtoValue {
        val bitAndExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.BITWISE_AND,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = bitAndExpr))
    }

    override fun visit(expr: EtsBitOrExpr): ProtoValue {
        val bitOrExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.BITWISE_OR,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = bitOrExpr))
    }

    override fun visit(expr: EtsBitXorExpr): ProtoValue {
        val bitXorExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.BITWISE_XOR,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = bitXorExpr))
    }

    override fun visit(expr: EtsLeftShiftExpr): ProtoValue {
        val leftShiftExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.LEFT_SHIFT,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = leftShiftExpr))
    }

    override fun visit(expr: EtsRightShiftExpr): ProtoValue {
        val rightShiftExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.RIGHT_SHIFT,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = rightShiftExpr))
    }

    override fun visit(expr: EtsUnsignedRightShiftExpr): ProtoValue {
        val unsignedRightShiftExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.UNSIGNED_RIGHT_SHIFT,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = unsignedRightShiftExpr))
    }

    override fun visit(expr: EtsAndExpr): ProtoValue {
        val andExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.LOGICAL_AND,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = andExpr))
    }

    override fun visit(expr: EtsOrExpr): ProtoValue {
        val orExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.LOGICAL_OR,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = orExpr))
    }

    override fun visit(expr: EtsNullishCoalescingExpr): ProtoValue {
        val nullishCoalescingExpr = ProtoBinaryExpr(
            op = ProtoBinaryOperator.NULLISH_COALESCING,
            left = expr.left.toProto(),
            right = expr.right.toProto(),
            type = expr.type.toProto()
        )
        return ProtoValue(expr = ProtoExpr(binary_expr = nullishCoalescingExpr))
    }

    override fun visit(expr: EtsInstanceCallExpr): ProtoValue {
        val instanceCallExpr = ProtoCallExpr(
            callee = expr.callee.toProto(),
            args = expr.args.map { (it as EtsValue).toProto() },
            type = expr.type.toProto(),
            instance_call = ProtoInstanceCall(
                instance = expr.instance.toProto(),
            ),
        )
        return ProtoValue(expr = ProtoExpr(call_expr = instanceCallExpr))
    }

    override fun visit(expr: EtsStaticCallExpr): ProtoValue {
        val staticCallExpr = ProtoCallExpr(
            callee = expr.callee.toProto(),
            args = expr.args.map { (it as EtsValue).toProto() },
            type = expr.type.toProto(),
            static_call = ProtoStaticCall(),
        )
        return ProtoValue(expr = ProtoExpr(call_expr = staticCallExpr))
    }

    override fun visit(expr: EtsPtrCallExpr): ProtoValue {
        val ptrCallExpr = expr.toProto()
        return ProtoValue(expr = ProtoExpr(call_expr = ptrCallExpr))
    }
}
