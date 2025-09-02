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

import org.jacodb.ets.dsl.ArrayAccess
import org.jacodb.ets.dsl.BinaryExpr
import org.jacodb.ets.dsl.BinaryOperator
import org.jacodb.ets.dsl.Block
import org.jacodb.ets.dsl.BlockAssign
import org.jacodb.ets.dsl.BlockCfg
import org.jacodb.ets.dsl.BlockCustomEts
import org.jacodb.ets.dsl.BlockIf
import org.jacodb.ets.dsl.BlockNop
import org.jacodb.ets.dsl.BlockReturn
import org.jacodb.ets.dsl.ConstantBoolean
import org.jacodb.ets.dsl.ConstantInt
import org.jacodb.ets.dsl.ConstantNumber
import org.jacodb.ets.dsl.ConstantString
import org.jacodb.ets.dsl.CustomBinaryExpr
import org.jacodb.ets.dsl.CustomUnaryExpr
import org.jacodb.ets.dsl.CustomValue
import org.jacodb.ets.dsl.Expr
import org.jacodb.ets.dsl.FieldRef
import org.jacodb.ets.dsl.Local
import org.jacodb.ets.dsl.Parameter
import org.jacodb.ets.dsl.StaticFieldRef
import org.jacodb.ets.dsl.ThisRef
import org.jacodb.ets.dsl.UnaryExpr
import org.jacodb.ets.dsl.UnaryOperator
import org.jacodb.ets.model.BasicBlock
import org.jacodb.ets.model.EtsAddExpr
import org.jacodb.ets.model.EtsAndExpr
import org.jacodb.ets.model.EtsArrayAccess
import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsBlockCfg
import org.jacodb.ets.model.EtsBooleanConstant
import org.jacodb.ets.model.EtsClassSignature
import org.jacodb.ets.model.EtsDivExpr
import org.jacodb.ets.model.EtsEntity
import org.jacodb.ets.model.EtsEqExpr
import org.jacodb.ets.model.EtsFieldSignature
import org.jacodb.ets.model.EtsGtEqExpr
import org.jacodb.ets.model.EtsGtExpr
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsImmediate
import org.jacodb.ets.model.EtsInstanceFieldRef
import org.jacodb.ets.model.EtsLValue
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
import org.jacodb.ets.model.EtsRemExpr
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStaticFieldRef
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation
import org.jacodb.ets.model.EtsStrictEqExpr
import org.jacodb.ets.model.EtsStrictNotEqExpr
import org.jacodb.ets.model.EtsStringConstant
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

    private val stub
        get() = EtsStmtLocation.stub(method)

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

        fun ensureImmediate(entity: EtsEntity): EtsImmediate {
            if (entity is EtsImmediate) {
                return entity
            }
            return ensureLocal(entity)
        }

        fun Expr.toEtsEntity(): EtsEntity = when (this) {
            is Local -> {
                EtsLocal(
                    name = name,
                    type = EtsUnknownType,
                )
            }

            is Parameter -> {
                EtsParameterRef(
                    index = index,
                    type = EtsUnknownType,
                )
            }

            ThisRef -> {
                EtsThis(
                    type = EtsUnknownType,
                )
            }

            is ConstantInt -> {
                EtsNumberConstant(value = value.toDouble())
            }

            is ConstantNumber -> {
                EtsNumberConstant(value = value)
            }

            is ConstantBoolean -> {
                EtsBooleanConstant(value = value)
            }

            is ConstantString -> {
                EtsStringConstant(value = value)
            }

            is FieldRef -> {
                val instanceEntity = instance.toEtsEntity()
                val instanceLocal = ensureLocal(instanceEntity)
                EtsInstanceFieldRef(
                    instance = instanceLocal,
                    field = EtsFieldSignature(
                        enclosingClass = EtsClassSignature.UNKNOWN,
                        name = fieldName,
                        type = EtsUnknownType,
                    ),
                    type = EtsUnknownType
                )
            }

            is StaticFieldRef -> {
                EtsStaticFieldRef(
                    field = EtsFieldSignature(
                        enclosingClass = EtsClassSignature.UNKNOWN,
                        name = fieldName,
                        type = EtsUnknownType,
                    ),
                    type = EtsUnknownType
                )
            }

            is ArrayAccess -> {
                val arrayEntity = array.toEtsEntity()
                val arrayLocal = ensureLocal(arrayEntity)
                val indexEntity = index.toEtsEntity()
                val indexValue = ensureImmediate(indexEntity)
                EtsArrayAccess(
                    array = arrayLocal,
                    index = indexValue,
                    type = EtsUnknownType,
                )
            }

            is UnaryExpr -> {
                val arg = ensureImmediate(expr.toEtsEntity())
                when (operator) {
                    UnaryOperator.NOT -> EtsNotExpr(
                        arg = arg,
                    )

                    UnaryOperator.NEG -> EtsNegExpr(
                        arg = arg,
                        type = EtsUnknownType,
                    )
                }
            }

            is BinaryExpr -> {
                val left = ensureImmediate(left.toEtsEntity())
                val right = ensureImmediate(right.toEtsEntity())
                when (operator) {
                    BinaryOperator.AND -> EtsAndExpr(
                        left = left,
                        right = right,
                        type = EtsUnknownType,
                    )

                    BinaryOperator.OR -> EtsOrExpr(
                        left = left,
                        right = right,
                        type = EtsUnknownType,
                    )

                    BinaryOperator.EQ -> EtsEqExpr(
                        left = left,
                        right = right,
                    )

                    BinaryOperator.NEQ -> EtsNotEqExpr(
                        left = left,
                        right = right,
                    )

                    BinaryOperator.EQQ -> EtsStrictEqExpr(
                        left = left,
                        right = right,
                    )

                    BinaryOperator.NEQQ -> EtsStrictNotEqExpr(
                        left = left,
                        right = right,
                    )

                    BinaryOperator.LT -> EtsLtExpr(
                        left = left,
                        right = right,
                    )

                    BinaryOperator.LTE -> EtsLtEqExpr(
                        left = left,
                        right = right,
                    )

                    BinaryOperator.GT -> EtsGtExpr(
                        left = left,
                        right = right,
                    )

                    BinaryOperator.GTE -> EtsGtEqExpr(
                        left = left,
                        right = right,
                    )

                    BinaryOperator.ADD -> EtsAddExpr(
                        left = left,
                        right = right,
                        type = EtsUnknownType,
                    )

                    BinaryOperator.SUB -> EtsSubExpr(
                        left = left,
                        right = right,
                        type = EtsUnknownType,
                    )

                    BinaryOperator.MUL -> EtsMulExpr(
                        left = left,
                        right = right,
                        type = EtsUnknownType,
                    )

                    BinaryOperator.DIV -> EtsDivExpr(
                        left = left,
                        right = right,
                        type = EtsUnknownType,
                    )

                    BinaryOperator.REM -> EtsRemExpr(
                        left = left,
                        right = right,
                        type = EtsUnknownType,
                    )
                }
            }

            is CustomValue -> {
                toEts()
            }

            is CustomUnaryExpr -> {
                val arg = ensureImmediate(arg.toEtsEntity())
                toEts(arg)
            }

            is CustomBinaryExpr -> {
                val left = ensureImmediate(left.toEtsEntity())
                val right = ensureImmediate(right.toEtsEntity())
                toEts(left, right)
            }
        }

        for (stmt in statements) {
            when (stmt) {
                BlockNop -> {
                    etsStatements += EtsNopStmt(location = stub)
                }

                is BlockAssign -> {
                    val lhv = stmt.target.toEtsEntity()
                    val rhv = stmt.expr.toEtsEntity()
                    check(lhv is EtsLValue) {
                        "Assignment target must be an LValue, got: ${lhv::class.simpleName}"
                    }
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

                is BlockCustomEts -> {
                    etsStatements += stmt.toEts(stub)
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
}
