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

package org.jacodb.ets.utils

import org.jacodb.ets.dsl.BinaryExpr
import org.jacodb.ets.dsl.BinaryOperator
import org.jacodb.ets.dsl.Block
import org.jacodb.ets.dsl.BlockAssign
import org.jacodb.ets.dsl.BlockCfg
import org.jacodb.ets.dsl.BlockIf
import org.jacodb.ets.dsl.BlockNop
import org.jacodb.ets.dsl.BlockReturn
import org.jacodb.ets.dsl.Constant
import org.jacodb.ets.dsl.Expr
import org.jacodb.ets.dsl.Local
import org.jacodb.ets.dsl.Parameter
import org.jacodb.ets.dsl.ThisRef
import org.jacodb.ets.dsl.UnaryExpr
import org.jacodb.ets.dsl.UnaryOperator
import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsLocal
import org.jacodb.ets.model.EtsLtEqExpr
import org.jacodb.ets.model.EtsLtExpr
import org.jacodb.ets.model.EtsMethod
import org.jacodb.ets.model.EtsMulExpr
import org.jacodb.ets.model.EtsNegExpr
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsNotEqExpr
import org.jacodb.ets.model.EtsNotExpr
import org.jacodb.ets.model.EtsNumberConstant
import org.jacodb.ets.model.EtsOrExpr
import org.jacodb.ets.model.EtsParameterRef
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsSubExpr
import org.jacodb.ets.model.EtsThis
import org.jacodb.ets.model.EtsUnknownType

fun BlockCfg.toEtsBlockCfg(method: EtsMethod): EtsBlockCfg {
    return EtsBlockCfgBuilder(method).build(this)
}

class EtsBlockCfgBuilder(
    val method: EtsMethod,
) {
    fun build(blockCfg: BlockCfg): EtsBlockCfg {
        return EtsBlockCfg(
            blocks = blockCfg.blocks.map { it.toEtsBasicBlock() },
            successors = blockCfg.successors,
        )
    }

    private var freeTempLocal: Int = 0
    private fun newTempLocal(): EtsLocal {
        return EtsLocal(
            name = "_tmp${freeTempLocal++}",
        )
    }

    private fun Block.toEtsBasicBlock(): BasicBlock {
        val etsStatements: MutableList<EtsStmt> = mutableListOf()

        fun ensureLocal(entity: EtsEntity): EtsLocal {
            if (entity is EtsLocal) {
                return entity
            }
            val newLocal = newTempLocal()
            etsStatements += EtsAssignStmt(
                location = stub,
                lhv = newLocal,
                rhv = entity,
            )
            return newLocal
        }

        for (stmt in statements) {
            when (stmt) {
                BlockNop -> {
                    etsStatements += EtsNopStmt(location = stub)
                }

                is BlockAssign -> {
                    val lhv = stmt.target.toEtsEntity() as EtsLocal // safe cast
                    val rhv = stmt.expr.toEtsEntity()
                    etsStatements += EtsAssignStmt(
                        location = stub,
                        lhv = lhv,
                        rhv = rhv,
                    )
                }

                is BlockReturn -> {
                    val returnValue = ensureLocal(stmt.expr.toEtsEntity())
                    etsStatements += EtsReturnStmt(
                        location = stub,
                        returnValue = returnValue,
                    )
                }

                is BlockIf -> {
                    val condition = ensureLocal(stmt.condition.toEtsEntity())
                    etsStatements += EtsIfStmt(
                        location = stub,
                        condition = condition,
                    )
                }
            }
        }

        if (etsStatements.isEmpty()) {
            etsStatements += EtsNopStmt(location = stub)
        }

        return BasicBlock(
            id = id,
            statements = etsStatements,
        )
    }

    private val stub
        get() = EtsStmtLocation.stub(method)

    private fun Expr.toEtsEntity(): EtsEntity = when (this) {
        is Local -> EtsLocal(
            name = name,
            type = EtsUnknownType, // TODO
        )

        is Parameter -> EtsParameterRef(
            index = index,
            type = EtsUnknownType, // TODO
        )

        ThisRef -> EtsThis(
            type = EtsUnknownType, // TODO
        )

        is Constant -> EtsNumberConstant(value = value)

        is UnaryExpr -> when (operator) {
            UnaryOperator.NOT -> EtsNotExpr(
                arg = expr.toEtsEntity(),
            )

            UnaryOperator.NEG -> EtsNegExpr(
                arg = expr.toEtsEntity(),
                type = EtsUnknownType, // TODO
            )
        }

        is BinaryExpr -> when (operator) {
            BinaryOperator.AND -> EtsAndExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
                type = EtsUnknownType, // TODO
            )

            BinaryOperator.OR -> EtsOrExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
                type = EtsUnknownType, // TODO
            )

            BinaryOperator.EQ -> EtsEqExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
            )

            BinaryOperator.NEQ -> EtsNotEqExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
            )

            BinaryOperator.LT -> EtsLtExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
            )

            BinaryOperator.LTE -> EtsLtEqExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
            )

            BinaryOperator.GT -> EtsGtExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
            )

            BinaryOperator.GTE -> EtsGtEqExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
            )

            BinaryOperator.ADD -> EtsAddExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
                type = EtsUnknownType, // TODO
            )

            BinaryOperator.SUB -> EtsSubExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
                type = EtsUnknownType, // TODO
            )

            BinaryOperator.MUL -> EtsMulExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
                type = EtsUnknownType, // TODO
            )

            BinaryOperator.DIV -> EtsDivExpr(
                left = left.toEtsEntity(),
                right = right.toEtsEntity(),
                type = EtsUnknownType, // TODO
            )
        }
    }
}
