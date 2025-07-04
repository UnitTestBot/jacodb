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

import org.jacodb.ets.model.EtsAssignStmt
import org.jacodb.ets.model.EtsCallStmt
import org.jacodb.ets.model.EtsIfStmt
import org.jacodb.ets.model.EtsNopStmt
import org.jacodb.ets.model.EtsRawStmt
import org.jacodb.ets.model.EtsReturnStmt
import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsThrowStmt
import org.jacodb.ets.model.EtsValue
import model.AssignStmt as ProtoAssignStmt
import model.CallStmt as ProtoCallStmt
import model.IfStmt as ProtoIfStmt
import model.NopStmt as ProtoNopStmt
import model.RawStmt as ProtoRawStmt
import model.ReturnStmt as ProtoReturnStmt
import model.Stmt as ProtoStmt
import model.ThrowStmt as ProtoThrowStmt

fun EtsStmt.toProto(): ProtoStmt = accept(EtsStmtToProto)

internal object EtsStmtToProto : EtsStmt.Visitor<ProtoStmt> {
    override fun visit(stmt: EtsRawStmt): ProtoStmt {
        val rawStmt = ProtoRawStmt(
            kind = stmt.kind,
        )
        return ProtoStmt(raw_stmt = rawStmt)
    }

    override fun visit(stmt: EtsNopStmt): ProtoStmt {
        val nopStmt = ProtoNopStmt()
        return ProtoStmt(nop_stmt = nopStmt)
    }

    override fun visit(stmt: EtsAssignStmt): ProtoStmt {
        val assignStmt = ProtoAssignStmt(
            lhv = stmt.lhv.toProto(),
            rhv = stmt.rhv.toProto(),
        )
        return ProtoStmt(assign_stmt = assignStmt)
    }

    override fun visit(stmt: EtsReturnStmt): ProtoStmt {
        val returnStmt = ProtoReturnStmt(
            return_value = stmt.returnValue?.toProto()
        )
        return ProtoStmt(return_stmt = returnStmt)
    }

    override fun visit(stmt: EtsThrowStmt): ProtoStmt {
        val throwStmt = ProtoThrowStmt(
            exception = (stmt.exception as EtsValue).toProto()
        )
        return ProtoStmt(throw_stmt = throwStmt)
    }

    override fun visit(stmt: EtsIfStmt): ProtoStmt {
        val ifStmt = ProtoIfStmt(
            condition = (stmt.condition as EtsValue).toProto()
        )
        return ProtoStmt(if_stmt = ifStmt)
    }

    override fun visit(stmt: EtsCallStmt): ProtoStmt {
        val callStmt = ProtoCallStmt(
            expr = stmt.expr.toProto()
        )
        return ProtoStmt(call_stmt = callStmt)
    }
}
