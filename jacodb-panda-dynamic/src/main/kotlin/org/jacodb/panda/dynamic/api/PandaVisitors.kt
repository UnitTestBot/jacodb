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

package org.jacodb.panda.dynamic.api

import org.jacodb.api.common.cfg.CommonExpr
import org.jacodb.api.common.cfg.CommonInst

interface PandaExprVisitor<out T> : CommonExpr.Visitor<T> {
    fun visitTODOExpr(expr: TODOExpr): T
    fun visitPandaArgument(expr: PandaArgument): T
    fun visitPandaCmpExpr(expr: PandaCmpExpr): T
    fun visitPandaStringConstant(expr: PandaStringConstant): T
    fun visitPandaUndefinedConstant(expr: PandaUndefinedConstant): T
    fun visitPandaCastExpr(expr: PandaCastExpr): T
    fun visitPandaEqExpr(expr: PandaEqExpr): T
    fun visitPandaNeqExpr(expr: PandaNeqExpr): T
    fun visitPandaLtExpr(expr: PandaLtExpr): T
    fun visitPandaLeExpr(expr: PandaLeExpr): T
    fun visitPandaGtExpr(expr: PandaGtExpr): T
    fun visitPandaGeExpr(expr: PandaGeExpr): T
    fun visitPandaStrictEqExpr(expr: PandaStrictEqExpr): T
    fun visitPandaNewExpr(expr: PandaNewExpr): T
    fun visitPandaAddExpr(expr: PandaAddExpr): T
    fun visitPandaVirtualCallExpr(expr: PandaVirtualCallExpr): T
    fun visitPandaNumberConstant(expr: PandaNumberConstant): T
    fun visitPandaTODOConstant(expr: TODOConstant): T
    fun visitPandaTypeofExpr(expr: PandaTypeofExpr): T
    fun visitPandaToNumericExpr(expr: PandaToNumericExpr): T
    fun visitPandaLocalVar(expr: PandaLocalVar): T
}

interface PandaInstVisitor<out T> : CommonInst.Visitor<T> {
    fun visitTODOInst(inst: TODOInst): T
    fun visitPandaThrowInst(inst: PandaThrowInst): T
    fun visitPandaReturnInst(inst: PandaReturnInst): T
    fun visitPandaAssignInst(inst: PandaAssignInst): T
    fun visitPandaCallInst(inst: PandaCallInst): T
    fun visitPandaIfInst(inst: PandaIfInst): T
}
