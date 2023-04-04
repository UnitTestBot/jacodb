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

package org.jacodb.analysis.codegen.ast.base.presentation.callable

import org.jacodb.analysis.codegen.ast.base.CodePresentation
import org.jacodb.analysis.codegen.ast.base.CodeValue
import org.jacodb.analysis.codegen.ast.base.presentation.callable.local.CallableLocalPresentation
import org.jacodb.analysis.codegen.ast.base.presentation.callable.local.LocalVariablePresentation
import org.jacodb.analysis.codegen.ast.base.presentation.callable.local.ParameterPresentation
import org.jacodb.analysis.codegen.ast.base.sites.CallSite
import org.jacodb.analysis.codegen.ast.base.sites.Site
import org.jacodb.analysis.codegen.ast.base.sites.TerminationSite
import org.jacodb.analysis.codegen.ast.base.typeUsage.TypeUsage
/**
 * Anything that can be called. Parent for functions, methods, lambdas, constructors, destructors etc.
 */
interface CallablePresentation : CodePresentation {
    val signature: String
        get() = parameters.joinToString { it.usage.stringPresentation }

    // consists from parameters and local variables
    val visibleLocals: Collection<CallableLocalPresentation>
    val returnType: TypeUsage

    // should be aware of local variables
    fun createParameter(name: String, type: TypeUsage): ParameterPresentation

    // should be aware of parameters
    fun createLocalVariable(
        name: String,
        type: TypeUsage,
        initialValue: CodeValue? = null
    ): LocalVariablePresentation

    val preparationSite: Site
    /**
     * Each site represent different way to execute this callable
     */
    val callSites: Collection<CallSite>
    fun createCallSite(callee: CallablePresentation, invokedOn: CodeValue? = null): CallSite
    val terminationSite: TerminationSite

    val graphId: Int

    val parameters: Collection<ParameterPresentation>
        get() = visibleLocals.filterIsInstance<ParameterPresentation>()
    val localVariables: Collection<LocalVariablePresentation>
        get() = visibleLocals.filterIsInstance<LocalVariablePresentation>()

    fun getLocal(name: String) = visibleLocals.singleOrNull { it.shortName == name }
    fun getLocals(type: TypeUsage) = visibleLocals.filter { it.usage == type }

    fun getLocalVariable(name: String) = localVariables.singleOrNull { it.shortName == name }
    fun getLocalVariables(type: TypeUsage) = localVariables.filter { it.usage == type }
    fun getOrCreateLocalVariable(
        name: String,
        type: TypeUsage,
        initialValue: CodeValue? = null
    ) = getLocalVariable(name) ?: createLocalVariable(name, type, initialValue)

    fun getParameter(name: String) = parameters.singleOrNull { it.shortName == name }
    fun getParameters(type: TypeUsage) = parameters.filter { it.usage == type }
    fun getOrCreateParameter(name: String, type: TypeUsage) =
        getParameter(name) ?: createParameter(name, type)

    fun getCallSite(callee: CallablePresentation, invokedOn: CodeValue? = null): CallSite? =
        callSites.singleOrNull {
            it.invocationExpression.invokedOn == invokedOn && it.invocationExpression.invokedCallable == callee
        }

    fun getOrCreateCallSite(callee: CallablePresentation, invokedOn: CodeValue? = null): CallSite =
        getCallSite(callee, invokedOn) ?: createCallSite(callee, invokedOn)

}