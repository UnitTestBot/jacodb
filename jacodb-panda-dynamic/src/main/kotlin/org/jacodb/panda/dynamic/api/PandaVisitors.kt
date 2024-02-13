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

import org.jacodb.api.core.cfg.CoreExprVisitor
import org.jacodb.api.core.cfg.InstVisitor

interface PandaExprVisitor<T> : CoreExprVisitor<T> {
    fun visitTODOExpr(expr: TODOExpr): T
    fun visitPandaArgument(expr: PandaArgument): T
    fun visitPandaCmpExpr(expr: PandaCmpExpr): T
    fun visitPandaStringConstant(expr: PandaStringConstant): T
    fun visitPandaCastExpr(expr: PandaCastExpr): T
    fun visitPandaEqExpr(expr: PandaEqExpr): T
    fun visitPandaNeqExpr(expr: PandaNeqExpr): T
    fun visitPandaNewExpr(expr: PandaNewExpr): T
    fun visitPandaAddExpr(expr: PandaAddExpr): T
    fun visitPandaVirtualCallExpr(expr: PandaVirtualCallExpr): T
    fun visitPandaNumberConstant(expr: PandaNumberConstant): T
    fun visitPandaTODOConstant(expr: TODOConstant): T
    fun visitPandaTypeofExpr(expr: PandaTypeofExpr): T
    fun visitPandaLocalVar(expr: PandaLocalVar): T
}

interface PandaInstVisitor<T> : InstVisitor<T> {
    fun visitTODOInst(inst: TODOInst): T
    fun visitPandaThrowInst(inst: PandaThrowInst): T
    fun visitPandaReturnInst(inst: PandaReturnInst): T
    fun visitPandaAssignInst(inst: PandaAssignInst): T
    fun visitPandaCallInst(inst: PandaCallInst): T
    fun visitPandaIfInst(inst: PandaIfInst): T
}
