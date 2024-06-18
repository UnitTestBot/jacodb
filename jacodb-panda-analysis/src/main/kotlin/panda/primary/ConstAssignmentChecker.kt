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

import org.jacodb.panda.dynamic.api.*

enum class ConstAssignmentCheckerErrorType {
    ACCESS_TO_UNDECLARED_CONST_VARIABLE,
    CONST_GLOBAL_VARIABLE_REASSIGNMENT,
    CONST_LOCAL_VARIABLE_REASSIGNMENT
}

private val logger = mu.KotlinLogging.logger {}

class ConstAssignmentChecker(val project: PandaProject) {
    val graph = PandaApplicationGraphImpl(project)

    fun analyse(): List<ConstAssignmentCheckerError> {
        val globalVariableErrors = UndeclaredVariablesChecker(project).analyse(checkConstReassignment = true).map {
            it as ConstAssignmentCheckerError
        }
        val localVariableErrors = mutableListOf<ConstAssignmentCheckerError>()

        val methods = graph.project.classes.flatMap { it.methods }
        for (method in methods) {
            for (inst in method.instructions) {
                if (inst is PandaThrowInst && inst.throwable.typeName == "ConstAssignmentError") {
                    localVariableErrors.add(
                        ConstAssignmentCheckerError(
                            varName = "unknown",
                            inst = inst,
                            errorType = ConstAssignmentCheckerErrorType.CONST_LOCAL_VARIABLE_REASSIGNMENT
                        )
                    )
                }
            }
        }

        val allErrors = globalVariableErrors + localVariableErrors

        allErrors.forEach { err ->
            val errorStr = when(err.errorType) {
                ConstAssignmentCheckerErrorType.ACCESS_TO_UNDECLARED_CONST_VARIABLE -> "Access to undeclared const variable"
                else -> "Reassignment of const variable"
            }
            logger.info { "$errorStr `${err.varName}` in ${err.inst} (location: ${err.inst.location})" }
        }

        return allErrors
    }
}
