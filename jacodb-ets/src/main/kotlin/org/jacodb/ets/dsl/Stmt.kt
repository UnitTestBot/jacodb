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

package org.jacodb.ets.dsl

import org.jacodb.ets.model.EtsStmt
import org.jacodb.ets.model.EtsStmtLocation

typealias StmtLocation = Int

sealed interface Stmt {
    val location: StmtLocation
}

data class NopStmt(
    override val location: StmtLocation,
) : Stmt

data class AssignStmt(
    override val location: StmtLocation,
    val target: LValue,
    val expr: Expr,
) : Stmt

data class ReturnStmt(
    override val location: StmtLocation,
    val expr: Expr,
) : Stmt

data class IfStmt(
    override val location: StmtLocation,
    val condition: Expr,
) : Stmt

class CustomEtsStmt(
    override val location: StmtLocation,
    val toEts: (EtsStmtLocation) -> EtsStmt,
) : Stmt
