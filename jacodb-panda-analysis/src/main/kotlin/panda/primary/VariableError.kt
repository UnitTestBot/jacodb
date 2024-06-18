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

package panda.primary

import org.jacodb.panda.dynamic.api.PandaInst

sealed interface VariableError {
    val varName: String
    val inst: PandaInst
}

data class ConstAssignmentCheckerError(
    override val varName: String,
    override val inst: PandaInst,
    val errorType: ConstAssignmentCheckerErrorType
): VariableError

class UndeclaredVariablesCheckerError(
    override val varName: String,
    override val inst: PandaInst,
    val varAccess: VarAccess,
    val isConstant: Lazy<Boolean>,
    val fictive: Boolean = false
): VariableError
