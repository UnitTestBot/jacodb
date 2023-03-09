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

package org.jacodb.analysis.codegen.ast.impl

import org.jacodb.analysis.codegen.ast.base.*

abstract class CallableImpl(
    override val graphId: Int,
    override val returnType: TypeUsage,
    parameters: List<Pair<TypeUsage, String>>
) : CallablePresentation {

    override val callSites = mutableListOf<CallSite>()

    override fun hashCode(): Int {
        return graphId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is CallableImpl)
            return false

        if (graphId == other.graphId) {
            // we uniquely identify callable instance by theirs vertices in graph
            assert(this === other)
        }

        return this === other
    }

    override val visibleLocals = parameters
        .mapIndexed { index, it ->
            ParameterImpl(
                it.first,
                it.second,
                this,
                index
            )
        }
        .toMutableList<CallableLocal>()

    override fun createLocalVariable(
        name: String,
        type: TypeUsage,
        initialValue: CodeValue?
    ): LocalVariablePresentation {
        assert(getLocal(name) == null) { "Already have local entity with name $name" }
        return LocalVariableImpl(type, name, initialValue, this).also { visibleLocals.add(it) }
    }

    override fun createParameter(name: String, type: TypeUsage): ParameterPresentation {
        assert(getLocal(name) == null) { "Already have local entity with name $name" }
        return ParameterImpl(type, name, this, parameters.size).also { visibleLocals.add(it) }
    }

    override fun createCallSite(
        callee: CallablePresentation,
        invokedOn: CodeValue?
    ): CallSite {
        assert(!callSites.any { it.graphId == callee.graphId }) { "already contains call-site for to such method" }

        val invocationExpression: InvocationExpression = if (invokedOn != null && callee is MethodPresentation) {
            MethodInvocationExpressionImpl(callee, invokedOn)
        } else if (invokedOn == null && callee is FunctionPresentation) {
            FunctionInvocationExpressionImpl(callee)
        } else if (invokedOn == null && callee is ConstructorPresentation) {
            ObjectCreationExpressionImpl(callee)
        } else {
            throw Exception("unknown call site creation")
        }

        return CallSiteImpl(callee.graphId, this, invocationExpression).also { callSites.add(it) }
    }

    override val terminationSite: TerminationSite by lazy {
        TerminationSiteImpl(
            this
        )
    }
    override val preparationSite: Site by lazy { PreparationSiteImpl(this) }
}