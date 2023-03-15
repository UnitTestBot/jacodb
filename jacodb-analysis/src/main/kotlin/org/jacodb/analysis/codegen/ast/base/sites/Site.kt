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

package org.jacodb.analysis.codegen.ast.base.sites

import org.jacodb.analysis.codegen.ast.base.presentation.callable.CallablePresentation
import org.jacodb.analysis.codegen.ast.base.CodeElement
import org.jacodb.analysis.codegen.ast.base.CodeExpression

/**
 * Some code block in execution path in single function.
 * Any callable instance is list of sites.
 * In any execution path each function
 */
interface Site : CodeElement {
    val parentCallable: CallablePresentation
    val expressionsBefore: Collection<CodeExpression>
    val expressionsAfter: Collection<CodeExpression>
    fun addBefore(expression: CodeExpression)
    fun addAfter(expression: CodeExpression)
}