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

package org.jacodb.analysis.codegen.ast.base.expression

import org.jacodb.analysis.codegen.ast.base.CodeValue
import org.jacodb.analysis.codegen.ast.base.ValueExpression
import org.jacodb.analysis.codegen.ast.base.presentation.callable.local.ParameterPresentation

/**
 * Expression that have arguments. Each argument should specify for which parameter it is used for.
 * If some parameters are not matched - default values of types will be used.
 */
interface ArgumentsOwnerExpression : ValueExpression {
    val parameterToArgument: Map<ParameterPresentation, CodeValue>
    fun addInCall(parameter: ParameterPresentation, argument: CodeValue)
}