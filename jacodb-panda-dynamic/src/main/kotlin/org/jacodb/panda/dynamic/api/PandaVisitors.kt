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
    fun visitPandaExpr(expr: PandaExpr): T

    fun visitTODOExpr(expr: TODOExpr): T
    fun visitPandaCmpExpr(expr: PandaCmpExpr): T
    fun visitPandaEqExpr(expr: PandaEqExpr): T
    fun visitPandaNeqExpr(expr: PandaNeqExpr): T
    fun visitPandaLtExpr(expr: PandaLtExpr): T
    fun visitPandaLeExpr(expr: PandaLeExpr): T
    fun visitPandaGtExpr(expr: PandaGtExpr): T
    fun visitPandaGeExpr(expr: PandaGeExpr): T
    fun visitPandaStrictEqExpr(expr: PandaStrictEqExpr): T
    fun visitPandaCastExpr(expr: PandaCastExpr): T
    fun visitPandaNewExpr(expr: PandaNewExpr): T
    fun visitPandaAddExpr(expr: PandaAddExpr): T
    fun visitPandaSubExpr(expr: PandaSubExpr): T
    fun visitPandaMulExpr(expr: PandaMulExpr): T
    fun visitPandaDivExpr(expr: PandaDivExpr): T
    fun visitPandaStaticCallExpr(expr: PandaStaticCallExpr): T
    fun visitPandaVirtualCallExpr(expr: PandaVirtualCallExpr): T
    fun visitPandaNegExpr(expr: PandaNegExpr): T
    fun visitPandaTypeofExpr(expr: PandaTypeofExpr): T
    fun visitPandaToNumericExpr(expr: PandaToNumericExpr): T

    fun visitPandaCreateEmptyArrayExpr(expr: PandaCreateEmptyArrayExpr): T

    fun visitPandaLocalVar(expr: PandaLocalVar): T
    fun visitPandaThis(expr: PandaThis): T
    fun visitPandaArgument(expr: PandaArgument): T
    fun visitPandaLoadedValue(expr: PandaLoadedValue): T
    fun visitPandaTODOConstant(expr: TODOConstant): T
    fun visitPandaBoolConstant(expr: PandaBoolConstant): T
    fun visitPandaNumberConstant(expr: PandaNumberConstant): T
    fun visitPandaStringConstant(expr: PandaStringConstant): T
    fun visitPandaUndefinedConstant(expr: PandaUndefinedConstant): T
    fun visitPandaNullConstant(expr: PandaNullConstant): T
    fun visitPandaFieldRef(expr: PandaFieldRef): T
    fun visitPandaArrayAccess(expr: PandaArrayAccess): T
}

interface PandaInstVisitor<out T> : CommonInst.Visitor<T> {
    fun visitTODOInst(inst: TODOInst): T
    fun visitPandaThrowInst(inst: PandaThrowInst): T
    fun visitPandaReturnInst(inst: PandaReturnInst): T
    fun visitPandaAssignInst(inst: PandaAssignInst): T
    fun visitPandaCallInst(inst: PandaCallInst): T
    fun visitPandaIfInst(inst: PandaIfInst): T
}
